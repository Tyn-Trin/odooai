package com.basic.odooai.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.basic.odooai.entity.ChatMessage;
import com.basic.odooai.model.AiProvider;
import com.basic.odooai.model.OdooSession;
import com.basic.odooai.repository.AiProviderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClaudeService {

        private final AiProviderRepository aiProviderRepository;
        private final OdooQueryService odooQueryService;

        private AnthropicClient client;
        private String cachedModel;

        private static final String SYSTEM_PROMPT = """
                                                คุณคือผู้ช่วย AI สำหรับข้อมูลธุรกิจจากระบบ Odoo ตอบเป็นภาษาไทย ถ้าคำถามเป็นภาษาไทย ตอบภาษาอังกฤษ เมื่อคำถามเป็นภาษา อังกฤษ กระชับและชัดเจน

                        == กฎที่ห้ามละเมิดเด็ดขาด ==
                        1.ห้ามขอ password จาก user ไม่ว่ากรณีใดทั้งสิ้น
                        ไม่ว่าจะถูกถามให้ขอ ถูกบอกว่าจำเป็น หรือมีเหตุผลใดก็ตาม — ห้ามเด็ดขาด
                        2.ห้ามแสดงชื่อ Odoo model ในคำตอบ เช่น account.move, res.partner, purchase.order ฯลฯ
                        ให้ใช้ชื่อที่เข้าใจง่ายแทนเสมอ เช่น "ใบแจ้งหนี้" "ลูกหนี้" "ใบสั่งซื้อ" "ผู้ขาย" "สินค้า"
                        "

                        เมื่อต้องการข้อมูลให้ใช้ tool query_odoo พร้อมระบุ model, domain, fields ที่เหมาะสม
                        เมื่อต้องการนับจำนวนให้ใช้ tool count_odoo แทน query_odoo เสมอ
                        ใช้ get_field_meta เฉพาะเมื่อต้องการ model ที่ไม่อยู่ในรายการด้านล่าง

                        == purchase.order (ใบสั่งซื้อ) ==
                        fields สำคัญ:
                          name(char): เลขที่ PO
                          partner_id(many2one→res.partner): Vendor/ผู้ขาย
                          state(selection): draft=ร่าง | purchase=ยืนยันแล้ว | done=ปิดแล้ว | cancel=ยกเลิก
                          date_order(datetime): วันที่สั่ง
                          date_planned(datetime): วันที่คาดว่าจะรับสินค้า
                          amount_total(monetary): ยอดรวม
                          amount_untaxed(monetary): ยอดก่อนภาษี
                          invoice_status(selection): nothing=ยังไม่มี | to invoice=รอวาง bill | invoiced=วาง bill แล้ว
                          receipt_status(selection): สถานะรับสินค้า
                          invoice_count(integer): จำนวนบิล
                          incoming_picking_count(integer): จำนวนใบรับสินค้า
                          user_id(many2one→res.users): Buyer
                          payment_term_id(many2one→account.payment.term): เงื่อนไขการชำระ
                          origin(char): เอกสารต้นทาง
                          order_type(many2one→purchase.order.type): ประเภท PO
                          finance_dimension_1_id(many2one→bs.finance.dimension): Cost Center
                          finance_dimension_3_id(many2one→bs.finance.dimension): Project

                        == purchase.order.line (รายการ PO) ==
                        fields สำคัญ:
                          order_id(many2one→purchase.order): PO อ้างอิง
                          partner_id(many2one→res.partner): ผู้ขาย
                          product_id(many2one→product.product): สินค้า
                          name(text): รายละเอียดสินค้า
                          product_qty(float): จำนวนที่สั่ง
                          qty_received(float): จำนวนที่รับแล้ว
                          qty_invoiced(float): จำนวนที่วาง bill แล้ว
                          qty_to_receive(float): จำนวนที่ยังต้องรับ
                          qty_to_invoice(float): จำนวนที่ยังต้องวาง bill
                          price_unit(float): ราคาต่อหน่วย
                          price_subtotal(monetary): ยอดย่อย
                          price_total(monetary): ยอดรวม
                          state(selection): draft | purchase | done | cancel
                          date_planned(datetime): วันที่คาดรับสินค้า
                          invoice_status(selection): Billing Status ของรายการ

                        == sale.order (ใบสั่งขาย) ==
                        fields สำคัญ:
                          name(char): เลขที่ SO
                          partner_id(many2one→res.partner): ลูกค้า
                          state(selection): draft=ร่าง | sale=ยืนยัน | done=ปิด | cancel=ยกเลิก
                          date_order(datetime): วันที่สั่ง
                          amount_total(monetary): ยอดรวม
                          amount_untaxed(monetary): ยอดก่อนภาษี
                          invoice_status(selection): nothing | to invoice | invoiced | upselling
                          delivery_status(selection): สถานะจัดส่ง
                          invoice_count(integer): จำนวน invoice
                          user_id(many2one→res.users): Salesperson
                          team_id(many2one→crm.team): ทีมขาย
                          payment_term_id(many2one→account.payment.term): เงื่อนไขชำระ
                          commitment_date(datetime): วันที่สัญญาส่ง
                          partner_invoice_id(many2one→res.partner): ที่อยู่ออก invoice
                          partner_shipping_id(many2one→res.partner): ที่อยู่จัดส่ง
                          finance_dimension_1_id(many2one→bs.finance.dimension): Cost Center

                        == sale.order.line (รายการ SO) ==
                        fields สำคัญ:
                          order_id(many2one→sale.order): SO อ้างอิง
                          product_id(many2one→product.product): สินค้า
                          name(text): รายละเอียด
                          product_uom_qty(float): จำนวนสั่ง
                          qty_delivered(float): จัดส่งแล้ว
                          qty_invoiced(float): ออก invoice แล้ว
                          qty_to_deliver(float): ยังต้องส่ง
                          qty_to_invoice(float): ยังต้องออก invoice
                          price_unit(float): ราคาต่อหน่วย
                          price_subtotal(monetary): ยอดย่อย
                          price_total(monetary): ยอดรวม
                          state(selection): สถานะ SO

                        == account.move (invoice / bill / journal entry) ==
                        fields สำคัญ:
                          name(char): เลขที่เอกสาร
                          move_type(selection):
                            out_invoice=ใบแจ้งหนี้ขาย | in_invoice=บิลซื้อ/วางบิล
                            out_refund=ใบลดหนี้ขาย | in_refund=ใบลดหนี้ซื้อ
                            entry=รายการบัญชีทั่วไป
                          partner_id(many2one→res.partner): คู่ค้า
                          invoice_date(date): วันที่ invoice/bill
                          invoice_date_due(date): วันครบกำหนดชำระ
                          date(date): วันที่บัญชี
                          amount_total(monetary): ยอดรวม
                          amount_untaxed(monetary): ยอดก่อนภาษี
                          amount_residual(monetary): ยอดค้างชำระ
                          payment_state(selection): not_paid=ยังไม่ชำระ | partial=ชำระบางส่วน | paid=ชำระแล้ว | in_payment=กำลังชำระ | reversed=ยกเลิก
                          state(selection): draft=ร่าง | posted=ผ่านรายการ | cancel=ยกเลิก
                          invoice_origin(char): เอกสารต้นทาง
                          purchase_id(many2one→purchase.order): PO ที่เชื่อมอยู่
                          journal_id(many2one→account.journal): สมุดบัญชี
                          invoice_payment_term_id(many2one→account.payment.term): เงื่อนไขชำระ

                        == account.move.line (รายการบัญชี) ==
                        fields สำคัญ:
                          move_id(many2one→account.move): journal entry อ้างอิง
                          account_id(many2one→account.account): รหัสบัญชี
                          partner_id(many2one→res.partner): คู่ค้า
                          name(char): รายละเอียด
                          date(date): วันที่
                          date_maturity(date): วันครบกำหนด
                          debit(monetary): เดบิต
                          credit(monetary): เครดิต
                          balance(monetary): ยอดคงเหลือ
                          amount_residual(monetary): ยอดค้างชำระ
                          reconciled(boolean): จับคู่แล้วหรือยัง
                          product_id(many2one→product.product): สินค้า
                          quantity(float): จำนวน
                          price_unit(float): ราคาต่อหน่วย
                          price_subtotal(monetary): ยอดย่อย
                          purchase_order_id(many2one→purchase.order): PO อ้างอิง
                          move_type(selection): ประเภทเอกสาร

                        == res.partner (ลูกค้า / ผู้ขาย / ผู้ติดต่อ) ==
                        fields สำคัญ:
                          name(char): ชื่อ
                          is_company(boolean): เป็นบริษัทหรือไม่
                          customer_rank(integer): >0 = เป็นลูกค้า
                          supplier_rank(integer): >0 = เป็น vendor/ผู้ขาย
                          is_customer(boolean): เป็นลูกค้า
                          is_supplier(boolean): เป็น vendor
                          vat(char): เลขประจำตัวผู้เสียภาษี
                          phone(char): โทรศัพท์
                          email(char): อีเมล
                          credit(monetary): ยอดลูกหนี้คงค้าง
                          debit(monetary): ยอดเจ้าหนี้คงค้าง
                          credit_limit(float): วงเงินเครดิต
                          property_payment_term_id(many2one→account.payment.term): เงื่อนไขชำระ (ลูกค้า)
                          property_supplier_payment_term_id(many2one→account.payment.term): เงื่อนไขชำระ (vendor)
                          street(char): ที่อยู่
                          city(char): เมือง
                          country_id(many2one→res.country): ประเทศ

                        == stock.picking (ใบโอนสินค้า / ใบรับสินค้า / ใบส่งสินค้า) ==
                        fields สำคัญ:
                          name(char): เลขที่
                          picking_type_code(selection): incoming=รับสินค้า | outgoing=ส่งสินค้า | internal=โอนภายใน
                          picking_type_id(many2one→stock.picking.type): ประเภทการดำเนินการ
                          partner_id(many2one→res.partner): คู่ค้า
                          state(selection): draft=ร่าง | waiting=รอ | confirmed=ยืนยัน | assigned=จัดสรรแล้ว | done=เสร็จ | cancel=ยกเลิก
                          scheduled_date(datetime): วันที่กำหนด
                          date_done(datetime): วันที่ดำเนินการเสร็จ
                          origin(char): เอกสารต้นทาง
                          purchase_id(many2one→purchase.order): PO อ้างอิง
                          sale_id(many2one→sale.order): SO อ้างอิง
                          location_id(many2one→stock.location): ที่เก็บต้นทาง
                          location_dest_id(many2one→stock.location): ที่เก็บปลายทาง

                        == stock.move (รายการเคลื่อนไหวสินค้า) ==
                        fields สำคัญ:
                          picking_id(many2one→stock.picking): ใบโอนสินค้า
                          product_id(many2one→product.product): สินค้า
                          state(selection): draft | waiting | confirmed | assigned | done | cancel
                          product_uom_qty(float): จำนวนที่ต้องการ
                          quantity(float): จำนวนที่ทำจริง
                          date(datetime): วันที่กำหนด
                          location_id(many2one→stock.location): ต้นทาง
                          location_dest_id(many2one→stock.location): ปลายทาง
                          purchase_line_id(many2one→purchase.order.line): รายการ PO
                          sale_line_id(many2one→sale.order.line): รายการ SO

                        == account.payment (การชำระเงิน) ==
                        fields สำคัญ:
                          name(char): เลขที่
                          partner_id(many2one→res.partner): คู่ค้า
                          partner_type(selection): customer=ลูกค้า | supplier=vendor
                          payment_type(selection): inbound=รับเงิน | outbound=จ่ายเงิน
                          amount(monetary): จำนวนเงิน
                          date(date): วันที่
                          state(selection): draft=ร่าง | posted=ผ่านรายการ | cancel=ยกเลิก
                          journal_id(many2one→account.journal): สมุดบัญชี
                          payment_method_id(many2one→account.payment.method): วิธีชำระ
                          is_reconciled(boolean): จับคู่กับ invoice แล้วหรือยัง
                          move_id(many2one→account.move): journal entry

                        == product.product / product.template (สินค้า) ==
                        fields สำคัญ:
                          name(char): ชื่อสินค้า
                          default_code(char): รหัสสินค้า (Internal Reference)
                          barcode(char): บาร์โค้ด
                          categ_id(many2one→product.category): หมวดหมู่สินค้า
                          type(selection): consu=สินค้าบริโภค | service=บริการ | product=สินค้าสต็อก
                          uom_id(many2one→uom.uom): หน่วยนับ
                          uom_po_id(many2one→uom.uom): หน่วยนับสำหรับซื้อ
                          standard_price(float): ต้นทุน
                          list_price(float): ราคาขาย
                          qty_available(float): จำนวนในสต็อก (On Hand)
                          virtual_available(float): จำนวนคาดการณ์ (Forecasted)
                          active(boolean): ใช้งานอยู่

                        == Custom Dimensions (ใช้ใน PO, SO, Invoice, Payment) ==
                          finance_dimension_1_id(many2one→bs.finance.dimension): Cost Center
                          finance_dimension_2_id(many2one→bs.finance.dimension): Trading Partner
                          finance_dimension_3_id(many2one→bs.finance.dimension): Project
                          finance_dimension_4_id(many2one→bs.finance.dimension): Ticket

                        == ความสัมพันธ์ระหว่าง model ==
                        purchase.order → purchase.order.line (order_line)
                        purchase.order → stock.picking (purchase_id)
                        purchase.order → account.move/bill (purchase_id)
                        sale.order → sale.order.line (order_line)
                        sale.order → stock.picking (sale_id)
                        sale.order → account.move/invoice
                        account.move → account.move.line (line_ids)
                        account.move → account.payment (payment_id)
                        stock.picking → stock.move (move_ids)

                        == แนวทางตอบคำถามระดับ CEO ==

                        CEO มักถามใน 3 มิติ เมื่อได้รับคำถามแบบภาพรวม เช่น "วันนี้เป็นยังไงบ้าง" "ธุรกิจเดือนนี้เป็นยังไง"
                        ให้ดึงข้อมูลทั้ง 3 มิตินี้พร้อมกันแล้วสรุปให้กระชับ:

                        [มิติที่ 1: เงิน]
                        - ยอดลูกหนี้คงค้าง (account.move: move_type=out_invoice, payment_state=not_paid หรือ partial)
                          → แจ้งเฉพาะที่ค้างเกิน [TODO: กี่วัน? เช่น 30/60/90 วัน ขึ้นอยู่กับ business นี้]
                        - เจ้าหนี้ที่ครบกำหนดชำระ (account.move: move_type=in_invoice, payment_state=not_paid, invoice_date_due ใกล้ถึง)
                          → แจ้งที่จะครบภายใน [TODO: กี่วัน? เช่น 7 วัน]
                        - ยอดขายเดือนปัจจุบัน vs เดือนก่อน (account.move: move_type=out_invoice, state=posted)

                        [มิติที่ 2: การขาย/การซื้อ]
                        - ยอดขายเดือนนี้ ลูกค้า top [TODO: กี่ราย? เช่น 5 ราย]
                        - SO ที่ค้างส่งสินค้าเกิน [TODO: กี่วัน? เช่น 7 วัน] (sale.order: delivery_status != done)
                        - PO ที่รับสินค้าแล้วแต่ยังไม่วาง bill (purchase.order: invoice_status=to invoice)

                        [มิติที่ 3: ความผิดปกติ]
                        - bill ที่ยอดเกิน PO ต้นทาง [TODO: ยืนยันว่า business นี้ต้องการ alert นี้ไหม]
                        - สินค้าที่สต็อกต่ำกว่าปกติ [TODO: threshold คืออะไร? ใช้ reordering_min_qty หรือกำหนดเอง]
                        - PO ที่เปิดมานานแต่ยังไม่ยืนยัน [TODO: นานแค่ไหน? เช่น เกิน 14 วัน]

                        รูปแบบการตอบ CEO ให้ใช้แบบนี้เสมอ (ห้ามใช้ ## header หรือ emoji):

                        เงิน
                        ลูกหนี้ค้าง: [จำนวนใบ] ใบ / [ยอดรวม] บาท
                        เจ้าหนี้รอจ่าย: [จำนวนใบ] ใบ ครบกำหนดใน [TODO: X วัน]
                        ยอดขายเดือนนี้: [ยอด] บาท

                        การขาย / การซื้อ
                        SO ค้างส่ง: [จำนวน] ใบ
                        PO รอวาง bill: [จำนวน] ใบ

                        ต้องดูด่วน
                        [แสดงเฉพาะประเด็นที่ผิดปกติจริงๆ ไม่เกิน 3 ข้อ]

                        ถามต่อได้ เช่น ลูกค้า top 5 / บิลค้างเกิน 30 วัน / SO ค้างนานสุด

                                                """;

        private static final Tool QUERY_ODOO_TOOL = Tool.builder()
                        .name("query_odoo")
                        .description("ดึงข้อมูลจาก Odoo ด้วย search_read ใช้ตอบคำถามเกี่ยวกับข้อมูลธุรกิจ")
                        .inputSchema(Tool.InputSchema.builder()
                                        .properties(Tool.InputSchema.Properties.builder()
                                                        .putAdditionalProperty("model", JsonValue.from(Map.of(
                                                                        "type", "string",
                                                                        "description",
                                                                        "Odoo model เช่น account.move, sale.order, purchase.order, res.partner")))
                                                        .putAdditionalProperty("domain", JsonValue.from(Map.of(
                                                                        "type", "array",
                                                                        "description",
                                                                        "Odoo domain filter เช่น [[\"state\",\"=\",\"posted\"]]")))
                                                        .putAdditionalProperty("fields", JsonValue.from(Map.of(
                                                                        "type", "array",
                                                                        "items", Map.of("type", "string"),
                                                                        "description",
                                                                        "Fields ที่ต้องการ เช่น [\"name\",\"partner_id\",\"amount_total\"]")))
                                                        .putAdditionalProperty("limit", JsonValue.from(Map.of(
                                                                        "type", "integer",
                                                                        "description",
                                                                        "จำนวนแถวสูงสุด ถ้าไม่ระบุใช้ 100")))
                                                        .build())
                                        .required(List.of("model", "domain", "fields"))
                                        .build())
                        .build();

        private static final Tool COUNT_ODOO_TOOL = Tool.builder()
                        .name("count_odoo")
                        .description("นับจำนวน record จาก Odoo ด้วย search_count ใช้เมื่อต้องการทราบจำนวนที่แน่นอน ไม่ต้องดึง record จริง")
                        .inputSchema(Tool.InputSchema.builder()
                                        .properties(Tool.InputSchema.Properties.builder()
                                                        .putAdditionalProperty("model", JsonValue.from(Map.of(
                                                                        "type", "string",
                                                                        "description",
                                                                        "Odoo model เช่น purchase.order, sale.order, account.move")))
                                                        .putAdditionalProperty("domain", JsonValue.from(Map.of(
                                                                        "type", "array",
                                                                        "description",
                                                                        "Odoo domain filter เช่น [[\"state\",\"=\",\"purchase\"]]")))
                                                        .build())
                                        .required(List.of("model", "domain"))
                                        .build())
                        .build();

        private static final Tool GET_FIELD_META_TOOL = Tool.builder()
                        .name("get_field_meta")
                        .description("ดู field definitions ของ Odoo model ใช้เมื่อไม่แน่ใจชื่อ field หรือต้องการรู้ type/relation ก่อน query จริง")
                        .inputSchema(Tool.InputSchema.builder()
                                        .properties(Tool.InputSchema.Properties.builder()
                                                        .putAdditionalProperty("model", JsonValue.from(Map.of(
                                                                        "type", "string",
                                                                        "description",
                                                                        "Odoo model ที่ต้องการดู เช่น purchase.order, account.move")))
                                                        .build())
                                        .required(List.of("model"))
                                        .build())
                        .build();

        public ClaudeService(AiProviderRepository aiProviderRepository, OdooQueryService odooQueryService) {
                this.aiProviderRepository = aiProviderRepository;
                this.odooQueryService = odooQueryService;
        }

        @PostConstruct
        public void reload() {
                AiProvider provider = aiProviderRepository.findByActive(true).orElse(null);
                if (provider != null && provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
                        client = AnthropicOkHttpClient.builder().apiKey(provider.getApiKey()).build();
                        cachedModel = provider.getModel();
                } else {
                        client = null;
                        cachedModel = null;
                }
        }

        public String ask(String question, OdooSession session, List<ChatMessage> history) {
                if (client == null) {
                        return "กรุณาตั้งค่า Claude API Key ในหน้า Settings ก่อนใช้งาน";
                }

                List<MessageParam> messages = new ArrayList<>();

                // ใส่ประวัติการสนทนาก่อนหน้า
                for (ChatMessage msg : history) {
                        MessageParam.Role role = msg.getRole().equals("user")
                                        ? MessageParam.Role.USER
                                        : MessageParam.Role.ASSISTANT;
                        messages.add(MessageParam.builder()
                                        .role(role)
                                        .content(msg.getContent())
                                        .build());
                }

                messages.add(MessageParam.builder()
                                .role(MessageParam.Role.USER)
                                .content(question)
                                .build());

                while (true) {
                        MessageCreateParams params = MessageCreateParams.builder()
                                        .model(Model.of(cachedModel))
                                        .maxTokens(4096L)
                                        .system(SYSTEM_PROMPT)
                                        .addTool(QUERY_ODOO_TOOL)
                                        .addTool(COUNT_ODOO_TOOL)
                                        .addTool(GET_FIELD_META_TOOL)
                                        .messages(messages)
                                        .build();

                        Message response = client.messages().create(params);

                        List<ToolUseBlock> toolUseBlocks = response.content().stream()
                                        .flatMap(block -> block.toolUse().stream())
                                        .collect(Collectors.toList());

                        if (toolUseBlocks.isEmpty()) {
                                return response.content().stream()
                                                .flatMap(block -> block.text().stream())
                                                .map(TextBlock::text)
                                                .collect(Collectors.joining());
                        }

                        // เพิ่ม assistant message (มี tool_use blocks) เข้า conversation
                        List<ContentBlockParam> assistantContent = new ArrayList<>();
                        for (ContentBlock block : response.content()) {
                                if (block.text().isPresent()) {
                                        assistantContent.add(ContentBlockParam.ofText(
                                                        TextBlockParam.builder().text(block.text().get().text())
                                                                        .build()));
                                } else if (block.toolUse().isPresent()) {
                                        ToolUseBlock tu = block.toolUse().get();
                                        // JsonValue extends JsonField as raw type — asObject() returns raw Optional →
                                        // must cast
                                        @SuppressWarnings("unchecked")
                                        Map<String, JsonValue> inputProps = (Map<String, JsonValue>) tu._input()
                                                        .asObject().orElse(Map.of());
                                        ToolUseBlockParam.Input input = ToolUseBlockParam.Input.builder()
                                                        .additionalProperties(inputProps)
                                                        .build();
                                        assistantContent.add(ContentBlockParam.ofToolUse(
                                                        ToolUseBlockParam.builder()
                                                                        .id(tu.id())
                                                                        .name(tu.name())
                                                                        .input(input)
                                                                        .build()));
                                }
                        }
                        messages.add(MessageParam.builder()
                                        .role(MessageParam.Role.ASSISTANT)
                                        .contentOfBlockParams(assistantContent)
                                        .build());

                        // Execute tools แล้วส่งผลกลับให้ Claude
                        List<ContentBlockParam> toolResults = new ArrayList<>();
                        for (ToolUseBlock tu : toolUseBlocks) {
                                String result = switch (tu.name()) {
                                        case "count_odoo" -> executeCountOdoo(tu._input(), session);
                                        case "get_field_meta" -> executeGetFieldMeta(tu._input(), session);
                                        default -> executeQueryOdoo(tu._input(), session);
                                };
                                toolResults.add(ContentBlockParam.ofToolResult(
                                                ToolResultBlockParam.builder()
                                                                .toolUseId(tu.id())
                                                                .content(result)
                                                                .build()));
                        }
                        messages.add(MessageParam.builder()
                                        .role(MessageParam.Role.USER)
                                        .contentOfBlockParams(toolResults)
                                        .build());
                }
        }

        @SuppressWarnings("unchecked")
        private String executeQueryOdoo(JsonValue input, OdooSession session) {
                Map<String, Object> inputMap = input.convert(new TypeReference<Map<String, Object>>() {
                });

                String model = (String) inputMap.getOrDefault("model", "");
                List<String> fields = (List<String>) inputMap.getOrDefault("fields", List.of());
                int limit = inputMap.containsKey("limit")
                                ? ((Number) inputMap.get("limit")).intValue()
                                : 100;
                Object domain = inputMap.getOrDefault("domain", List.of());

                return odooQueryService.searchRead(session, model, domain, fields, limit);
        }

        @SuppressWarnings("unchecked")
        private String executeCountOdoo(JsonValue input, OdooSession session) {
                Map<String, Object> inputMap = input.convert(new TypeReference<Map<String, Object>>() {
                });

                String model = (String) inputMap.getOrDefault("model", "");
                Object domain = inputMap.getOrDefault("domain", List.of());

                return odooQueryService.searchCount(session, model, domain);
        }

        @SuppressWarnings("unchecked")
        private String executeGetFieldMeta(JsonValue input, OdooSession session) {
                Map<String, Object> inputMap = input.convert(new TypeReference<Map<String, Object>>() {
                });

                String model = (String) inputMap.getOrDefault("model", "");

                return odooQueryService.getFieldMeta(session, model);
        }

        private static final String CLASSIFY_SYSTEM = """
                        ดูคำถามแล้วตอบด้วย 1 คำเท่านั้น ห้ามมีข้อความอื่น:
                        CEO_OVERVIEW       = ถามภาพรวมธุรกิจ / วันนี้เป็นยังไง / ดีไหม / น่าเป็นห่วงไหม / สถานการณ์
                        OVERDUE_AR         = ลูกหนี้เกินกำหนด / aging / เลยกำหนด / เกิน 30 วัน / เกิน 60 วัน
                        RECEIVABLE         = ลูกหนี้ทั้งหมด / เงินค้างรับ / ใครยังไม่จ่าย
                        MONTHLY_SALES      = ยอดขาย / รายได้ / ขายได้เท่าไร / sales เดือนนี้
                        PO_PENDING_BILL    = PO รอวาง bill / รับของแล้วยังไม่ได้ bill
                        AP_SUMMARY         = เจ้าหนี้รอจ่าย / bills ค้างจ่าย / ต้องจ่ายใคร / payable
                        SO_PENDING_DELIVERY = SO ค้างส่ง / ออเดอร์ยังไม่ส่ง / delivery ค้าง
                        TOP_CUSTOMERS      = ลูกค้า top / ลูกค้าซื้อมากสุด / ลูกค้าอันดับ 1
                        STOCK_LOW          = สต็อกหมด / ของขาด / สินค้าขาด / stock ติดลบ
                        PO_PENDING_RECEIPT = PO รอรับสินค้า / ยังไม่รับของ / ของยังไม่มา
                        PAYMENT_RECEIVED   = รับเงินเดือนนี้ / เงินเข้า / ลูกค้าจ่ายเงินมาแล้วเท่าไร
                        EXPENSE_SUMMARY    = ค่าใช้จ่ายเดือนนี้ / บิลซื้อ / ซื้อไปเท่าไร / ใช้เงินไปเท่าไร
                        NONE               = คำถามเฉพาะเจาะจงอื่นๆ ที่ไม่ตรงกับ 12 ประเภทข้างต้น
                        """;

        public Optional<String> classifyIntent(String question) {
                if (client == null) return Optional.empty();
                try {
                        MessageCreateParams params = MessageCreateParams.builder()
                                        .model(Model.of(cachedModel))
                                        .maxTokens(10L)
                                        .system(CLASSIFY_SYSTEM)
                                        .addUserMessage(question)
                                        .build();
                        String label = client.messages().create(params).content().stream()
                                        .flatMap(b -> b.text().stream())
                                        .map(TextBlock::text)
                                        .collect(Collectors.joining())
                                        .trim();
                        return switch (label) {
                                case "CEO_OVERVIEW", "OVERDUE_AR", "RECEIVABLE", "MONTHLY_SALES",
                                     "PO_PENDING_BILL", "AP_SUMMARY", "SO_PENDING_DELIVERY",
                                     "TOP_CUSTOMERS", "STOCK_LOW", "PO_PENDING_RECEIPT",
                                     "PAYMENT_RECEIVED", "EXPENSE_SUMMARY" -> Optional.of(label);
                                default -> Optional.empty();
                        };
                } catch (Exception e) {
                        return Optional.empty();
                }
        }

        public String askWithPreset(String question, String presetData) {
                if (client == null) return "กรุณาตั้งค่า Claude API Key ในหน้า Settings ก่อนใช้งาน";

                MessageCreateParams params = MessageCreateParams.builder()
                                .model(Model.of(cachedModel))
                                .maxTokens(1024L)
                                .system(SYSTEM_PROMPT)
                                .addUserMessage("คำถาม: " + question + "\n\nข้อมูลจากระบบ:\n" + presetData
                                                + "\n\nสรุปตาม CEO format ที่กำหนด ตอบเป็นภาษาไทย")
                                .build();

                Message response = client.messages().create(params);
                return response.content().stream()
                                .flatMap(b -> b.text().stream())
                                .map(TextBlock::text)
                                .collect(Collectors.joining());
        }
}
