package com.youngplace.websocket.service;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class OnlineUserRegistry {

    private final ConcurrentMap<String, Set<UUID>> userSessions = new ConcurrentHashMap<String, Set<UUID>>();

    public void addSession(String userId, UUID sessionId) {
        userSessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public void removeSession(String userId, UUID sessionId) {
        Set<UUID> sessions = userSessions.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(sessionId);
        if (sessions.isEmpty()) {
            userSessions.remove(userId);
        }
    }

    public Set<UUID> getSessions(String userId) {
        Set<UUID> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<UUID>(sessions);
    }

    public int onlineUserCount() {
        return userSessions.size();
    }
}
