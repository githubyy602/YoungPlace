package com.youngplace.iam.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

@Entity
@Table(name = "iam_refresh_session")
public class IamRefreshSessionEntity {

    @Id
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "refresh_jti", nullable = false, length = 64)
    private String refreshJti;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "expire_at_epoch_millis", nullable = false)
    private long expireAtEpochMillis;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at_epoch_millis", nullable = false)
    private long createdAtEpochMillis;

    @Column(name = "updated_at_epoch_millis", nullable = false)
    private long updatedAtEpochMillis;

    @PrePersist
    public void onCreate() {
        long now = System.currentTimeMillis();
        this.createdAtEpochMillis = now;
        this.updatedAtEpochMillis = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAtEpochMillis = System.currentTimeMillis();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRefreshJti() {
        return refreshJti;
    }

    public void setRefreshJti(String refreshJti) {
        this.refreshJti = refreshJti;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(int tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public long getExpireAtEpochMillis() {
        return expireAtEpochMillis;
    }

    public void setExpireAtEpochMillis(long expireAtEpochMillis) {
        this.expireAtEpochMillis = expireAtEpochMillis;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public long getCreatedAtEpochMillis() {
        return createdAtEpochMillis;
    }

    public void setCreatedAtEpochMillis(long createdAtEpochMillis) {
        this.createdAtEpochMillis = createdAtEpochMillis;
    }

    public long getUpdatedAtEpochMillis() {
        return updatedAtEpochMillis;
    }

    public void setUpdatedAtEpochMillis(long updatedAtEpochMillis) {
        this.updatedAtEpochMillis = updatedAtEpochMillis;
    }
}
