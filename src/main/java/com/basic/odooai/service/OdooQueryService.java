package com.basic.odooai.service;

import com.basic.odooai.model.OdooSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class OdooQueryService {

    private static final Logger log = LoggerFactory.getLogger(OdooQueryService.class);

    @Value("${odoo.url}")
    private String odooUrl;

    @Value("${odoo.db}")
    private String odooDb;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getFieldMeta(OdooSession session, String model) {
        try {
            Map<String, Object> body = Map.of(
                "jsonrpc", "2.0",
                "method", "call",
                "id", 1,
                "params", Map.of(
                    "service", "object",
                    "method", "execute_kw",
                    "args", List.of(
                        odooDb,
                        session.getUid(),
                        session.getPassword(),
                        model,
                        "fields_get",
                        List.of(),
                        Map.of("attributes", List.of("string", "type", "required", "relation"))
                    )
                )
            );

            log.info("Calling fields_get: {} model={}", odooUrl + "/jsonrpc", model);
            Map<?, ?> response = restClient.post()
                .uri(odooUrl + "/jsonrpc")
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);

            Object result = response != null ? response.get("result") : null;
            if (result == null) {
                Object error = response != null ? response.get("error") : null;
                return "ไม่พบ field meta" + (error != null ? ": " + error : "");
            }
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Odoo fields_get error model={} : {}", model, e.getMessage(), e);
            return "เกิดข้อผิดพลาดในการดึง field meta: " + e.getMessage();
        }
    }

    public String searchCount(OdooSession session, String model, Object domain) {
        try {
            Map<String, Object> body = Map.of(
                "jsonrpc", "2.0",
                "method", "call",
                "id", 1,
                "params", Map.of(
                    "service", "object",
                    "method", "execute_kw",
                    "args", List.of(
                        odooDb,
                        session.getUid(),
                        session.getPassword(),
                        model,
                        "search_count",
                        List.of(domain)
                    )
                )
            );

            log.info("Calling search_count: {} model={}", odooUrl + "/jsonrpc", model);
            Map<?, ?> response = restClient.post()
                .uri(odooUrl + "/jsonrpc")
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);

            Object result = response != null ? response.get("result") : null;
            if (result == null) {
                Object error = response != null ? response.get("error") : null;
                return "ไม่พบข้อมูล" + (error != null ? ": " + error : "");
            }
            return result.toString();
        } catch (Exception e) {
            log.error("Odoo search_count error model={} : {}", model, e.getMessage(), e);
            return "เกิดข้อผิดพลาดในการนับข้อมูลจาก Odoo: " + e.getMessage();
        }
    }

    public String searchRead(OdooSession session, String model, Object domain, List<String> fields, int limit) {
        try {
            Map<String, Object> body = Map.of(
                "jsonrpc", "2.0",
                "method", "call",
                "id", 1,
                "params", Map.of(
                    "service", "object",
                    "method", "execute_kw",
                    "args", List.of(
                        odooDb,
                        session.getUid(),
                        session.getPassword(),
                        model,
                        "search_read",
                        List.of(domain),
                        Map.of("fields", fields, "limit", limit)
                    )
                )
            );

            log.info("Calling: {}", odooUrl + "/jsonrpc");
            Map<?, ?> response = restClient.post()
                .uri(odooUrl + "/jsonrpc")
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);

            Object result = response != null ? response.get("result") : null;
            if (result == null) {
                Object error = response != null ? response.get("error") : null;
                return "ไม่พบข้อมูล" + (error != null ? ": " + error : "");
            }

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Odoo query error model={} : {}", model, e.getMessage(), e);
            return "เกิดข้อผิดพลาดในการดึงข้อมูลจาก Odoo: " + e.getMessage();
        }
    }
}
