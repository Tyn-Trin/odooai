package com.basic.odooai.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ai_provider")
public class AiProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "api_key")
    private String apiKey;

    private String model;

    @Column(name = "is_active")
    private boolean active;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
