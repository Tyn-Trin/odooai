package com.basic.odooai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.basic.odooai.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUid(Long uid);
}
