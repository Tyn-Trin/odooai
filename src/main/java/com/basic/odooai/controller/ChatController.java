package com.basic.odooai.controller;

import com.basic.odooai.entity.ChatMessage;
import com.basic.odooai.entity.Conversation;
import com.basic.odooai.model.OdooSession;
import com.basic.odooai.repository.ChatMessageRepository;
import com.basic.odooai.repository.ConversationRepository;
import com.basic.odooai.service.ClaudeService;
import com.basic.odooai.service.OdooPresetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class ChatController {

    private final ClaudeService claudeService;
    private final OdooPresetService odooPresetService;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatController(ClaudeService claudeService, OdooPresetService odooPresetService,
                          ConversationRepository conversationRepository,
                          ChatMessageRepository chatMessageRepository) {
        this.claudeService = claudeService;
        this.odooPresetService = odooPresetService;
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @GetMapping("/chat")
    public String chatPage(Authentication authentication, Model model) {
        OdooSession session = (OdooSession) authentication.getPrincipal();
        model.addAttribute("username", session.getUsername());
        return "chat";
    }

    @GetMapping("/api/conversations")
    @ResponseBody
    public List<Conversation> listConversations(Authentication authentication) {
        OdooSession session = (OdooSession) authentication.getPrincipal();
        return conversationRepository.findByUidSorted(session.getUid());
    }

    @PatchMapping("/api/conversations/{id}")
    @ResponseBody
    public ResponseEntity<?> updateConversation(@PathVariable Long id,
                                                @RequestBody Map<String, Object> body,
                                                Authentication authentication) {
        OdooSession session = (OdooSession) authentication.getPrincipal();
        return conversationRepository.findById(id)
                .filter(c -> c.getUid().equals(session.getUid()))
                .map(c -> {
                    if (body.containsKey("title")) {
                        String title = (String) body.get("title");
                        if (title != null && !title.isBlank())
                            c.setTitle(title.length() > 60 ? title.substring(0, 60) + "…" : title);
                    }
                    if (body.containsKey("favourite"))
                        c.setFavourite((Boolean) body.get("favourite"));
                    return ResponseEntity.ok(conversationRepository.save(c));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/api/conversations/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteConversation(@PathVariable Long id, Authentication authentication) {
        OdooSession session = (OdooSession) authentication.getPrincipal();
        return conversationRepository.findById(id)
                .filter(c -> c.getUid().equals(session.getUid()))
                .map(c -> {
                    chatMessageRepository.deleteByConversationId(id);
                    conversationRepository.deleteById(id);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/conversations/{id}/messages")
    @ResponseBody
    public ResponseEntity<?> loadMessages(@PathVariable Long id, Authentication authentication) {
        OdooSession session = (OdooSession) authentication.getPrincipal();
        return conversationRepository.findById(id)
                .filter(c -> c.getUid().equals(session.getUid()))
                .map(c -> ResponseEntity.ok(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(id)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/ask")
    @ResponseBody
    public Map<String, Object> ask(@RequestParam String question,
                                   @RequestParam(required = false) Long conversationId,
                                   Authentication authentication) {
        OdooSession session = (OdooSession) authentication.getPrincipal();

        // สร้าง conversation ใหม่ถ้ายังไม่มี
        if (conversationId == null) {
            Conversation conv = new Conversation();
            conv.setUid(session.getUid());
            conv.setTitle(question.length() > 60 ? question.substring(0, 60) + "…" : question);
            conversationId = conversationRepository.save(conv).getId();
        }

        // โหลด history ก่อน save เพื่อไม่ให้ question ซ้ำ
        List<ChatMessage> history = chatMessageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);

        // บันทึก user message
        saveMessage(session.getUid(), conversationId, "user", question);

        // ถาม AI
        Optional<String> presetData = odooPresetService.matchAndRun(question, session);
        if (presetData.isEmpty()) {
            presetData = claudeService.classifyIntent(question)
                    .flatMap(id -> odooPresetService.runById(id, session));
        }
        String answer = presetData
                .map(data -> claudeService.askWithPreset(question, data))
                .orElseGet(() -> claudeService.ask(question, session, history));

        // บันทึก AI response
        saveMessage(session.getUid(), conversationId, "assistant", answer);

        return Map.of("answer", answer, "conversationId", conversationId);
    }

    private void saveMessage(Long uid, Long conversationId, String role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setUid(uid);
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }
}
