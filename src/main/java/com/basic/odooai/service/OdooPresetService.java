package com.basic.odooai.service;

import com.basic.odooai.model.OdooSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiFunction;

@Service
public class OdooPresetService {

    private static final Logger log = LoggerFactory.getLogger(OdooPresetService.class);

    private final OdooQueryService odooQueryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private record Preset(String id, List<String> keywords, BiFunction<OdooSession, String, String> executor) {}

    private final List<Preset> PRESETS;

    public OdooPresetService(OdooQueryService odooQueryService) {
        this.odooQueryService = odooQueryService;
        PRESETS = List.of(
            // ---- specific presets first (more specific keywords win over general ones) ----
            new Preset(
                "OVERDUE_AR",
                List.of("เกินกำหนด", "เลยกำหนด", "เกิน 30", "เกิน 60", "เกิน 90", "aging"),
                (s, q) -> overdueAr(s)
            ),
            new Preset(
                "CEO_OVERVIEW",
                List.of("เป็นยังไง", "ภาพรวม", "สรุป", "วันนี้", "overview", "summary"),
                (s, q) -> ceoOverview(s)
            ),
            new Preset(
                "RECEIVABLE",
                List.of("ลูกหนี้", "receivable", "ค้างชำระ", "ค้างรับ"),
                (s, q) -> receivableSummary(s)
            ),
            new Preset(
                "MONTHLY_SALES",
                List.of("ยอดขาย", "รายได้", "ขายได้"),
                (s, q) -> monthlySales(s)
            ),
            new Preset(
                "PO_PENDING_BILL",
                List.of("po ค้าง", "รอวาง bill", "รอบิล", "ค้าง bill"),
                (s, q) -> poPendingBill(s)
            ),
            new Preset(
                "AP_SUMMARY",
                List.of("เจ้าหนี้", "payable", "รอจ่าย", "ต้องจ่าย"),
                (s, q) -> apSummary(s)
            ),
            new Preset(
                "SO_PENDING_DELIVERY",
                List.of("so ค้าง", "ค้างส่ง", "ยังไม่ส่ง", "ค้างจัดส่ง"),
                (s, q) -> soPendingDelivery(s)
            ),
            new Preset(
                "TOP_CUSTOMERS",
                List.of("top customer", "ลูกค้า top", "ลูกค้าอันดับ", "ลูกค้าซื้อมาก"),
                (s, q) -> topCustomers(s)
            ),
            new Preset(
                "STOCK_LOW",
                List.of("สต็อกหมด", "ของหมด", "สินค้าหมด", "ขาดสต็อก", "stock ต่ำ"),
                (s, q) -> stockLow(s)
            ),
            new Preset(
                "PO_PENDING_RECEIPT",
                List.of("รอรับสินค้า", "ยังไม่รับ", "ของยังไม่มา"),
                (s, q) -> poPendingReceipt(s)
            ),
            new Preset(
                "PAYMENT_RECEIVED",
                List.of("เงินเข้า", "รับชำระ", "ลูกค้าจ่าย"),
                (s, q) -> paymentReceived(s)
            ),
            new Preset(
                "EXPENSE_SUMMARY",
                List.of("ค่าใช้จ่าย", "บิลซื้อ", "expense"),
                (s, q) -> expenseSummary(s)
            )
        );
    }

    public Optional<String> matchAndRun(String question, OdooSession session) {
        String q = question.toLowerCase();
        return PRESETS.stream()
            .filter(p -> p.keywords().stream().anyMatch(kw -> q.contains(kw.toLowerCase())))
            .findFirst()
            .map(p -> {
                log.info("Preset keyword matched [{}] for: {}", p.id(), question);
                return p.executor().apply(session, q);
            });
    }

    public Optional<String> runById(String id, OdooSession session) {
        return PRESETS.stream()
            .filter(p -> p.id().equals(id))
            .findFirst()
            .map(p -> {
                log.info("Preset classifier matched [{}]", id);
                return p.executor().apply(session, "");
            });
    }

    private String ceoOverview(OdooSession session) {
        try {
            String firstOfMonth = LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

            List<?> arDomain = arOutstandingDomain();
            int arCount = toInt(odooQueryService.searchCount(session, "account.move", arDomain));
            double arAmount = sumField(odooQueryService.searchRead(session, "account.move", arDomain,
                List.of("amount_residual"), 5000), "amount_residual");

            List<?> apDomain = List.of(
                List.of("move_type", "=", "in_invoice"),
                List.of("state", "=", "posted"),
                List.of("payment_state", "in", List.of("not_paid", "partial"))
            );
            int apCount = toInt(odooQueryService.searchCount(session, "account.move", apDomain));
            double apAmount = sumField(odooQueryService.searchRead(session, "account.move", apDomain,
                List.of("amount_residual"), 5000), "amount_residual");

            List<?> salesDomain = List.of(
                List.of("move_type", "=", "out_invoice"),
                List.of("state", "=", "posted"),
                List.of("invoice_date", ">=", firstOfMonth)
            );
            int salesCount = toInt(odooQueryService.searchCount(session, "account.move", salesDomain));
            double salesAmount = sumField(odooQueryService.searchRead(session, "account.move", salesDomain,
                List.of("amount_total"), 5000), "amount_total");

            int soCount = toInt(odooQueryService.searchCount(session, "sale.order",
                List.of(List.of("state", "=", "sale"), List.of("delivery_status", "!=", "full"))));

            int poCount = toInt(odooQueryService.searchCount(session, "purchase.order",
                List.of(List.of("state", "=", "purchase"), List.of("invoice_status", "=", "to invoice"))));

            return "[CEO_OVERVIEW]\n"
                + "ลูกหนี้ค้าง: " + arCount + " ใบ / " + fmt(arAmount) + " บาท\n"
                + "เจ้าหนี้รอจ่าย: " + apCount + " ใบ / " + fmt(apAmount) + " บาท\n"
                + "ยอดขายเดือนนี้: " + fmt(salesAmount) + " บาท (" + salesCount + " invoice)\n"
                + "SO ค้างส่ง: " + soCount + " ใบ\n"
                + "PO รอวาง bill: " + poCount + " ใบ";

        } catch (Exception e) {
            log.error("ceoOverview error: {}", e.getMessage(), e);
            return "เกิดข้อผิดพลาด: " + e.getMessage();
        }
    }

    private String receivableSummary(OdooSession session) {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            List<?> allAr = arOutstandingDomain();
            List<?> overdueAr = List.of(
                List.of("move_type", "=", "out_invoice"),
                List.of("state", "=", "posted"),
                List.of("payment_state", "in", List.of("not_paid", "partial")),
                List.of("invoice_date_due", "<", today)
            );

            int total = toInt(odooQueryService.searchCount(session, "account.move", allAr));
            double totalAmt = sumField(odooQueryService.searchRead(session, "account.move", allAr,
                List.of("amount_residual"), 5000), "amount_residual");
            int overdue = toInt(odooQueryService.searchCount(session, "account.move", overdueAr));
            double overdueAmt = sumField(odooQueryService.searchRead(session, "account.move", overdueAr,
                List.of("amount_residual"), 5000), "amount_residual");

            String sample = odooQueryService.searchRead(session, "account.move", allAr,
                List.of("name", "partner_id", "amount_residual", "invoice_date_due"), 20);

            return "[RECEIVABLE]\n"
                + "ลูกหนี้ทั้งหมด: " + total + " ใบ / " + fmt(totalAmt) + " บาท\n"
                + "เกินกำหนดชำระแล้ว: " + overdue + " ใบ / " + fmt(overdueAmt) + " บาท\n"
                + "ตัวอย่าง 20 รายการ:\n" + sample;

        } catch (Exception e) {
            log.error("receivableSummary error: {}", e.getMessage(), e);
            return "เกิดข้อผิดพลาด: " + e.getMessage();
        }
    }

    private String monthlySales(OdooSession session) {
        try {
            String firstOfMonth = LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            List<?> domain = List.of(
                List.of("move_type", "=", "out_invoice"),
                List.of("state", "=", "posted"),
                List.of("invoice_date", ">=", firstOfMonth)
            );
            int count = toInt(odooQueryService.searchCount(session, "account.move", domain));
            double amount = sumField(odooQueryService.searchRead(session, "account.move", domain,
                List.of("amount_total"), 5000), "amount_total");
            String sample = odooQueryService.searchRead(session, "account.move", domain,
                List.of("name", "partner_id", "amount_total", "invoice_date"), 20);

            return "[MONTHLY_SALES]\n"
                + "ยอดขายเดือนนี้: " + fmt(amount) + " บาท (" + count + " invoice)\n"
                + "20 รายการล่าสุด:\n" + sample;

        } catch (Exception e) {
            log.error("monthlySales error: {}", e.getMessage(), e);
            return "เกิดข้อผิดพลาด: " + e.getMessage();
        }
    }

    private String poPendingBill(OdooSession session) {
        try {
            List<?> domain = List.of(
                List.of("state", "=", "purchase"),
                List.of("invoice_status", "=", "to invoice")
            );
            int count = toInt(odooQueryService.searchCount(session, "purchase.order", domain));
            double amount = sumField(odooQueryService.searchRead(session, "purchase.order", domain,
                List.of("amount_total"), 5000), "amount_total");
            String sample = odooQueryService.searchRead(session, "purchase.order", domain,
                List.of("name", "partner_id", "amount_total", "date_order"), 20);

            return "[PO_PENDING_BILL]\n"
                + "PO รอวาง bill: " + count + " ใบ / " + fmt(amount) + " บาท\n"
                + "20 รายการล่าสุด:\n" + sample;

        } catch (Exception e) {
            log.error("poPendingBill error: {}", e.getMessage(), e);
            return "เกิดข้อผิดพลาด: " + e.getMessage();
        }
    }

    private String apSummary(OdooSession session) {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String in7days = LocalDate.now().plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE);

            List<?> allAp = List.of(
                List.of("move_type", "=", "in_invoice"),
                List.of("state", "=", "posted"),
                List.of("payment_state", "in", List.of("not_paid", "partial"))
            );
            List<?> overdueDomain = List.of(
                List.of("move_type", "=", "in_invoice"),
                List.of("state", "=", "posted"),
                List.of("payment_state", "in", List.of("not_paid", "partial")),
                List.of("invoice_date_due", "<", today)
            );
            List<?> dueSoonDomain = List.of(
                List.of("move_type", "=", "in_invoice"),
                List.of("state", "=", "posted"),
                List.of("payment_state", "in", List.of("not_paid", "partial")),
                List.of("invoice_date_due", ">=", today),
                List.of("invoice_date_due", "<=", in7days)
            );

            int total = toInt(odooQueryService.searchCount(session, "account.move", allAp));
            double totalAmt = sumField(odooQueryService.searchRead(session, "account.move", allAp,
                List.of("amount_residual"), 5000), "amount_residual");
            int overdueCount = toInt(odooQueryService.searchCount(session, "account.move", overdueDomain));
            double overdueAmt = sumField(odooQueryService.searchRead(session, "account.move", overdueDomain,
                List.of("amount_residual"), 5000), "amount_residual");
            int dueSoonCount = toInt(odooQueryService.searchCount(session, "account.move", dueSoonDomain));
            double dueSoonAmt = sumField(odooQueryService.searchRead(session, "account.move", dueSoonDomain,
                List.of("amount_residual"), 5000), "amount_residual");
            String sample = odooQueryService.searchRead(session, "account.move", allAp,
                List.of("name", "partner_id", "amount_residual", "invoice_date_due"), 20);

            return "[AP_SUMMARY]\n"
                + "เจ้าหนี้รอจ่ายทั้งหมด: " + total + " ใบ / " + fmt(totalAmt) + " บาท\n"
                + "เกินกำหนดชำระแล้ว: " + overdueCount + " ใบ / " + fmt(overdueAmt) + " บาท\n"
                + "ครบกำหนดใน 7 วัน: " + dueSoonCount + " ใบ / " + fmt(dueSoonAmt) + " บาท\n"
                + "ตัวอย่าง 20 รายการ:\n" + sample;
        } catch (Exception e) {
            log.error("apSummary error: {}", e.getMessage(), e);
            return "เกิดข้อผิดพลาด: " + e.getMessage();
        }
    }

    private String overdueAr(OdooSession session) {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            List<?> domain = List.of(
                List.of("move_type", "=", "out_invoice"),
                List.of("state", "=", "posted"),
                List.of("payment_state", "in", List.of("not_paid", "partial")),
                List.of("invoice_date_due", "<", today)
            );
            String json = odooQueryService.searchRead(session, "account.move", domain,
                List.of("name", "partner_id", "amount_residual", "invoice_date_due"), 5000);

            List<Map<String, Object>> records = objectMapper.readValue(json, new TypeReference<>() {});
            LocalDate todayDate = LocalDate.now();
            double b1 = 0, b2 = 0, b3 = 0, b4 = 0;
            int c1 = 0, c2 = 0, c3 = 0, c4 = 0;
            for (Map<String, Object> r : records) {
                double amt = toDouble(r.get("amount_residual"));
                String due = (String) r.get("invoice_date_due");
                if (due == null || due.isBlank()) continue;
                long days = ChronoUnit.DAYS.between(LocalDate.parse(due), todayDate);
                if      (days <= 30) { b1 += amt; c1++; }
                else if (days <= 60) { b2 += amt; c2++; }
                else if (days <= 90) { b3 += amt; c3++; }
                else                 { b4 += amt; c4++; }
            }

            return "[OVERDUE_AR]\n"
                + "ลูกหนี้เกินกำหนดรวม: " + records.size() + " ใบ / " + fmt(b1+b2+b3+b4) + " บาท\n"
                + "1-30 วัน:  " + c1 + " ใบ / " + fmt(b1) + " บาท\n"
                + "31-60 วัน: " + c2 + " ใบ / " + fmt(b2) + " บาท\n"
                + "61-90 วัน: " + c3 + " ใบ / " + fmt(b3) + " บาท\n"
                + ">90 วัน:   " + c4 + " ใบ / " + fmt(b4) + " บาท";
        } catch (Exception e) {
            log.error("overdueAr error: {}", e.getMessage(), e);
            return "เกิดข้อผิดพลาด: " + e.getMessage();
        }
    }

    private String soPendingDelivery(OdooSession session) {
        try {
            List<?> domain = List.of(
                List.of("state", "=", "sale"),
                List.of("delivery_status", "!=", "full")
            );
            int count = toInt(odooQueryService.searchCount(session, "sale.order", domain));
            double amount = sumField(odooQueryService.searchRead(session, "sale.order", domain,
                List.of("amount_total"), 5000), "amount_total");
            String sample = odooQueryService.searchRead(session, "sale.order", domain,
                List.of("name", "partner_id", "amount_total", "date_order", "commitment_date"), 20);

            return "[SO_PENDING_DELIVERY]\n"
                + "SO ค้างส่งสินค้า: " + count + " ใบ / " + fmt(amount) + " บาท\n"
                + "20 รายการ:\n" + sample;
        } catch (Exception e) {
            log.error("soPendingDelivery error: {}", e.getMessage(), e);
            return "เกิดข้อผิดพลาด: " + e.getMessage();
        }
    }

    private String topCustomers(OdooSession session) {
        try {
            String firstOfMonth = LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            List<?> domain = List.of(
                List.of("move_type", "=", "out_invoice"),
                List.of("state", "=", "posted"),
                List.of("invoice_date", ">=", firstOfMonth)
            );
            String json = odooQueryService.searchRead(session, "account.move", domain,
                List.of("partner_id", "amount_total"), 5000);

            List<Map<String, Object>> records = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Double> byPartner = new LinkedHashMap<>();
            for (Map<String, Object> r : records) {
                String name = partnerName(r.get("partner_id"));
                byPartner.merge(name, toDouble(r.get("amount_total")), Double::sum);
            }

            List<Map.Entry<String, Double>> sorted = new ArrayList<>(byPartner.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            StringBuilder sb = new StringBuilder("[TOP_CUSTOMERS]\nลูกค้า top 10 เดือนนี้:\n");
            int rank = 1;
            for (Map.Entry<String, Double> e : sorted.subList(0, Math.min(10, sorted.size()))) {
                sb.append(rank++).append(". ").append(e.getKey())
                  .append(": ").append(fmt(e.getValue())).append(" บาท\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("topCustomers error: {}", e.getMessage(), e);
            return "เกิดข้อผิดพลาด: " + e.getMessage();
        }
    }

    private String stockLow(OdooSession session) {
        try {
            List<?> domain = List.of(
                List.of("type", "=", "product"),
                List.of("active", "=", true),
                List.of("qty_available", "<=", 0)
            );
            int count = toInt(odooQueryService.searchCount(session, "product.product", domain));
            String sample = odooQueryService.searchRead(session, "product.product", domain,
                List.of("name", "default_code", "qty_available", "virtual_available", "categ_id"), 50);

            return "[STOCK_LOW]\n"
                + "สินค้าสต็อกหมด/ติดลบ: " + count + " รายการ\n"
                + "รายละเอียด (สูงสุด 50):\n" + sample;
        } catch (Exception e) {
            log.error("stockLow error: {}", e.getMessage(), e);
            return "เกิดข้อผิดพลาด: " + e.getMessage();
        }
    }

    private String poPendingReceipt(OdooSession session) {
        try {
            List<?> domain = List.of(
                List.of("state", "=", "purchase"),
                List.of("receipt_status", "in", List.of("pending", "partial"))
            );
            int count = toInt(odooQueryService.searchCount(session, "purchase.order", domain));
            double amount = sumField(odooQueryService.searchRead(session, "purchase.order", domain,
                List.of("amount_total"), 5000), "amount_total");
            String sample = odooQueryService.searchRead(session, "purchase.order", domain,
                List.of("name", "partner_id", "amount_total", "date_planned"), 20);

            return "[PO_PENDING_RECEIPT]\n"
                + "PO รอรับสินค้า: " + count + " ใบ / " + fmt(amount) + " บาท\n"
                + "20 รายการ:\n" + sample;
        } catch (Exception e) {
            log.error("poPendingReceipt error: {}", e.getMessage(), e);
            return "เกิดข้อผิดพลาด: " + e.getMessage();
        }
    }

    private String paymentReceived(OdooSession session) {
        try {
            String firstOfMonth = LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            List<?> domain = List.of(
                List.of("partner_type", "=", "customer"),
                List.of("payment_type", "=", "inbound"),
                List.of("state", "=", "posted"),
                List.of("date", ">=", firstOfMonth)
            );
            int count = toInt(odooQueryService.searchCount(session, "account.payment", domain));
            double amount = sumField(odooQueryService.searchRead(session, "account.payment", domain,
                List.of("amount"), 5000), "amount");
            String sample = odooQueryService.searchRead(session, "account.payment", domain,
                List.of("name", "partner_id", "amount", "date", "journal_id"), 20);

            return "[PAYMENT_RECEIVED]\n"
                + "รับเงินจากลูกค้าเดือนนี้: " + count + " รายการ / " + fmt(amount) + " บาท\n"
                + "20 รายการล่าสุด:\n" + sample;
        } catch (Exception e) {
            log.error("paymentReceived error: {}", e.getMessage(), e);
            return "เกิดข้อผิดพลาด: " + e.getMessage();
        }
    }

    private String expenseSummary(OdooSession session) {
        try {
            String firstOfMonth = LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            List<?> domain = List.of(
                List.of("move_type", "=", "in_invoice"),
                List.of("state", "=", "posted"),
                List.of("invoice_date", ">=", firstOfMonth)
            );
            int count = toInt(odooQueryService.searchCount(session, "account.move", domain));
            double amount = sumField(odooQueryService.searchRead(session, "account.move", domain,
                List.of("amount_total"), 5000), "amount_total");
            String sample = odooQueryService.searchRead(session, "account.move", domain,
                List.of("name", "partner_id", "amount_total", "invoice_date"), 20);

            return "[EXPENSE_SUMMARY]\n"
                + "บิลซื้อ/ค่าใช้จ่ายเดือนนี้: " + count + " ใบ / " + fmt(amount) + " บาท\n"
                + "20 รายการล่าสุด:\n" + sample;
        } catch (Exception e) {
            log.error("expenseSummary error: {}", e.getMessage(), e);
            return "เกิดข้อผิดพลาด: " + e.getMessage();
        }
    }

    private List<?> arOutstandingDomain() {
        return List.of(
            List.of("move_type", "=", "out_invoice"),
            List.of("state", "=", "posted"),
            List.of("payment_state", "in", List.of("not_paid", "partial"))
        );
    }

    private double toDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private String partnerName(Object v) {
        if (v instanceof List<?> arr && arr.size() >= 2) return arr.get(1).toString();
        return "ไม่ทราบ";
    }

    private int toInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    @SuppressWarnings("unchecked")
    private double sumField(String json, String field) {
        try {
            List<Map<String, Object>> records = objectMapper.readValue(json, new TypeReference<>() {});
            return records.stream().mapToDouble(r -> {
                Object v = r.get(field);
                return v instanceof Number n ? n.doubleValue() : 0.0;
            }).sum();
        } catch (Exception e) { return 0.0; }
    }

    private String fmt(double amount) {
        return String.format("%,.0f", amount);
    }
}
