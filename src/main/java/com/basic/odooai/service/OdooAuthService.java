package com.basic.odooai.service;

import com.basic.odooai.model.OdooSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class OdooAuthService {

    @Value("${odoo.url}")
    private String odooUrl;

    @Value("${odoo.db}")
    private String odooDb;

    private final RestClient restClient = RestClient.create();

    public OdooSession login(String username, String password) {
        Map<String, Object> body = Map.of(
            "jsonrpc", "2.0",
            "method", "call",
            "params", Map.of(
                "db", odooDb,
                "login", username,
                "password", password
            )
        );

   
        Map response = restClient.post()
            .uri(odooUrl + "/web/session/authenticate")
            .header("Content-Type", "application/json")
            .body(body)
            .retrieve()
            .body(Map.class);

        Map result = (Map) response.get("result");

        if (result == null || result.get("uid") == null) {
            throw new RuntimeException("Login failed");
        }

        OdooSession session = new OdooSession();
        session.setSessionId((String) result.get("session_id"));
        session.setUid(((Number) result.get("uid")).longValue());
        session.setUsername((String) result.get("name"));
        session.setPassword(password);

        return session;
    }
}
