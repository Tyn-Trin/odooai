package com.basic.odooai.repository;

import com.basic.odooai.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("SELECT c FROM Conversation c WHERE c.uid = :uid ORDER BY c.favourite DESC, c.createdAt DESC")
    List<Conversation> findByUidSorted(@Param("uid") Long uid);
}
