package com.basic.odooai.service;

import com.basic.odooai.model.OdooSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class OdooQueryService {

    @Value("${odoo.url}")
    private String odooUrl;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String searchRead(OdooSession session, String model, Object domain, List<String> fields, int limit) {
        try {
            Map<String, Object> body = Map.of(
                "jsonrpc", "2.0",
                "method", "call",
                "params", Map.of(
                    "model", model,
                    "method", "search_read",
                    "args", List.of(domain),
                    "kwargs", Map.of(
                        "fields", fields,
                        "limit", limit
                    )
                )
            );

            Map<?, ?> response = restClient.post()
                .uri(odooUrl + "/web/dataset/call_kw")
                .header("Content-Type", "application/json")
                .header("Cookie", "session_id=" + session.getSessionId())
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
            return "เกิดข้อผิดพลาดในการดึงข้อมูลจาก Odoo: " + e.getMessage();
        }
    }
}
