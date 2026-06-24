package com.basic.odooai.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversation")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long uid;

    private String title;

    @Column(nullable = false)
    @ColumnDefault("false")
    private Boolean favourite = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUid() { return uid; }
    public void setUid(Long uid) { this.uid = uid; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public boolean isFavourite() { return favourite != null && favourite; }
    public void setFavourite(Boolean favourite) { this.favourite = favourite != null && favourite; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
