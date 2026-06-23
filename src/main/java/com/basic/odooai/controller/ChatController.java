package com.basic.odooai.controller;

import com.basic.odooai.model.OdooSession;
import com.basic.odooai.service.ClaudeService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ChatController {

    private final ClaudeService claudeService;

    public ChatController(ClaudeService claudeService) {
        this.claudeService = claudeService;
    }

    @GetMapping("/chat")
    public String chatPage() {
        return "chat";
    }

    @PostMapping("/api/ask")
    @ResponseBody
    public String ask(@RequestParam String question, Authentication authentication) {
        OdooSession session = (OdooSession) authentication.getPrincipal();
        return claudeService.ask(question, session);
    }
}
