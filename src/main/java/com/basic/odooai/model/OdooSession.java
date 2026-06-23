package com.basic.odooai.model;

import java.io.Serializable;

public class OdooSession implements Serializable {

    private String sessionId;
    private Long uid;
    private String username;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Long getUid() { return uid; }
    public void setUid(Long uid) { this.uid = uid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
