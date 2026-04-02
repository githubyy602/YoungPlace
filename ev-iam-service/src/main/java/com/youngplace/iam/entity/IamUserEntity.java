package com.youngplace.iam.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

@Entity
@Table(name = "iam_user")
public class IamUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "roles", nullable = false, length = 255)
    private String roles;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 1;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "locked_until_epoch_millis", nullable = false)
    private long lockedUntilEpochMillis = 0L;

    @Column(name = "last_login_at_epoch_millis", nullable = false)
    private long lastLoginAtEpochMillis = 0L;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(int tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public void setFailedLoginCount(int failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    public long getLockedUntilEpochMillis() {
        return lockedUntilEpochMillis;
    }

    public void setLockedUntilEpochMillis(long lockedUntilEpochMillis) {
        this.lockedUntilEpochMillis = lockedUntilEpochMillis;
    }

    public long getLastLoginAtEpochMillis() {
        return lastLoginAtEpochMillis;
    }

    public void setLastLoginAtEpochMillis(long lastLoginAtEpochMillis) {
        this.lastLoginAtEpochMillis = lastLoginAtEpochMillis;
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
