package com.youngplace.iam.service;

import com.youngplace.iam.config.AuthSecurityProperties;
import com.youngplace.iam.entity.IamUserEntity;
import com.youngplace.iam.repository.IamUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class UserAccountService {

    private final AuthSecurityProperties authSecurityProperties;
    private final PasswordEncoder passwordEncoder;
    private final IamUserRepository iamUserRepository;

    public UserAccountService(AuthSecurityProperties authSecurityProperties,
                              PasswordEncoder passwordEncoder,
                              IamUserRepository iamUserRepository) {
        this.authSecurityProperties = authSecurityProperties;
        this.passwordEncoder = passwordEncoder;
        this.iamUserRepository = iamUserRepository;
    }

    public UserAccount findByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        Optional<IamUserEntity> userOptional = iamUserRepository.findByUsername(username.trim());
        return userOptional.map(this::toUserAccount).orElse(null);
    }

    public boolean matchesPassword(UserAccount account, String rawPassword) {
        return account != null && StringUtils.hasText(rawPassword) && passwordEncoder.matches(rawPassword, account.getPasswordHash());
    }

    public boolean isLocked(UserAccount account) {
        if (account == null) {
            return false;
        }
        long now = Instant.now().toEpochMilli();
        return account.getLockedUntilEpochMillis() > now;
    }

    public long lockRemainingSeconds(UserAccount account) {
        if (account == null) {
            return 0L;
        }
        long now = Instant.now().toEpochMilli();
        long remaining = account.getLockedUntilEpochMillis() - now;
        return remaining <= 0L ? 0L : Math.max(1L, remaining / 1000L);
    }

    public void recordLoginFailure(UserAccount account) {
        if (account == null || !StringUtils.hasText(account.getUsername())) {
            return;
        }
        Optional<IamUserEntity> optional = iamUserRepository.findByUsername(account.getUsername());
        if (!optional.isPresent()) {
            return;
        }
        IamUserEntity entity = optional.get();
        int nextFailures = entity.getFailedLoginCount() + 1;
        entity.setFailedLoginCount(nextFailures);
        if (nextFailures >= authSecurityProperties.getLoginMaxFailures()) {
            long lockMillis = authSecurityProperties.getLockMinutes() * 60L * 1000L;
            entity.setLockedUntilEpochMillis(Instant.now().toEpochMilli() + lockMillis);
            entity.setFailedLoginCount(0);
        }
        iamUserRepository.save(entity);
    }

    public void recordLoginSuccess(UserAccount account) {
        if (account == null || !StringUtils.hasText(account.getUsername())) {
            return;
        }
        Optional<IamUserEntity> optional = iamUserRepository.findByUsername(account.getUsername());
        if (!optional.isPresent()) {
            return;
        }
        IamUserEntity entity = optional.get();
        entity.setFailedLoginCount(0);
        entity.setLockedUntilEpochMillis(0L);
        entity.setLastLoginAtEpochMillis(Instant.now().toEpochMilli());
        iamUserRepository.save(entity);
    }

    public int increaseTokenVersion(String username) {
        if (!StringUtils.hasText(username)) {
            return -1;
        }
        Optional<IamUserEntity> optional = iamUserRepository.findByUsername(username.trim());
        if (!optional.isPresent()) {
            return -1;
        }
        IamUserEntity entity = optional.get();
        entity.setTokenVersion(entity.getTokenVersion() + 1);
        iamUserRepository.save(entity);
        return entity.getTokenVersion();
    }

    private UserAccount toUserAccount(IamUserEntity entity) {
        UserAccount account = new UserAccount();
        account.setUsername(entity.getUsername());
        account.setPasswordHash(entity.getPasswordHash());
        account.setEnabled(entity.isEnabled());
        account.setRoles(parseRoles(entity.getRoles()));
        account.setTokenVersion(entity.getTokenVersion());
        account.setFailedLoginCount(entity.getFailedLoginCount());
        account.setLockedUntilEpochMillis(entity.getLockedUntilEpochMillis());
        account.setLastLoginAtEpochMillis(entity.getLastLoginAtEpochMillis());
        return account;
    }

    private List<String> parseRoles(String rolesText) {
        if (!StringUtils.hasText(rolesText)) {
            return Collections.emptyList();
        }
        String[] segments = rolesText.split(",");
        List<String> roles = new ArrayList<String>();
        for (String segment : segments) {
            if (segment == null) {
                continue;
            }
            String role = segment.trim();
            if (StringUtils.hasText(role)) {
                roles.add(role);
            }
        }
        if (roles.isEmpty()) {
            return Collections.emptyList();
        }
        return roles;
    }

    public static class UserAccount {
        private String username;
        private String passwordHash;
        private boolean enabled;
        private List<String> roles;
        private int tokenVersion;
        private int failedLoginCount;
        private long lockedUntilEpochMillis;
        private long lastLoginAtEpochMillis;

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

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
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
    }
}
