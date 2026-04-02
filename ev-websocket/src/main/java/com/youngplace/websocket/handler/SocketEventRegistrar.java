package com.youngplace.websocket.handler;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.youngplace.common.api.ApiResponse;
import com.youngplace.websocket.config.NacosDebugProperties;
import com.youngplace.websocket.model.BroadcastRequest;
import com.youngplace.websocket.model.PrivateMessageRequest;
import com.youngplace.websocket.model.SegmentBroadcastRequest;
import com.youngplace.websocket.model.TopicPublishRequest;
import com.youngplace.websocket.model.TopicSubscriptionRequest;
import com.youngplace.websocket.security.JwtTokenService;
import com.youngplace.websocket.service.OnlineUserRegistry;
import org.springframework.core.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class SocketEventRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketEventRegistrar.class);
    private static final String ATTR_USER_ID = "userId";

    private final SocketIOServer socketIOServer;
    private final JwtTokenService jwtTokenService;
    private final OnlineUserRegistry onlineUserRegistry;
    private final NacosDebugProperties nacosDebugProperties;
    private final Environment environment;

    public SocketEventRegistrar(SocketIOServer socketIOServer,
                                JwtTokenService jwtTokenService,
                                OnlineUserRegistry onlineUserRegistry,
                                NacosDebugProperties nacosDebugProperties,
                                Environment environment) {
        this.socketIOServer = socketIOServer;
        this.jwtTokenService = jwtTokenService;
        this.onlineUserRegistry = onlineUserRegistry;
        this.nacosDebugProperties = nacosDebugProperties;
        this.environment = environment;
    }

    @PostConstruct
    public void registerListeners() {
        socketIOServer.addConnectListener(this::onConnect);
        socketIOServer.addDisconnectListener(this::onDisconnect);

        socketIOServer.addEventListener("message:private:send", PrivateMessageRequest.class, this::onPrivateMessageSend);
        socketIOServer.addEventListener("topic:subscribe", TopicSubscriptionRequest.class, this::onTopicSubscribe);
        socketIOServer.addEventListener("topic:unsubscribe", TopicSubscriptionRequest.class, this::onTopicUnsubscribe);
        socketIOServer.addEventListener("topic:publish", TopicPublishRequest.class, this::onTopicPublish);
        socketIOServer.addEventListener("broadcast:all", BroadcastRequest.class, this::onBroadcastAll);
        socketIOServer.addEventListener("broadcast:segment", SegmentBroadcastRequest.class, this::onBroadcastSegment);
        socketIOServer.addEventListener("debug:config:get", Map.class, this::onDebugConfigGet);
    }

    private void onConnect(SocketIOClient client) {
        String token = jwtTokenService.resolveToken(client.getHandshakeData());
        String userId = jwtTokenService.parseSubject(token);
        if (!StringUtils.hasText(userId)) {
            client.sendEvent("ack:error", ApiResponse.fail(401, "unauthorized socket connection"));
            client.disconnect();
            return;
        }

        client.set(ATTR_USER_ID, userId);
        onlineUserRegistry.addSession(userId, client.getSessionId());

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("userId", userId);
        payload.put("sessionId", client.getSessionId().toString());
        payload.put("onlineUsers", onlineUserRegistry.onlineUserCount());
        client.sendEvent("ack:ok", ApiResponse.success(payload));

        LOGGER.info("Socket connected, userId={}, sessionId={}", userId, client.getSessionId());
    }

    private void onDisconnect(SocketIOClient client) {
        String userId = client.get(ATTR_USER_ID);
        if (StringUtils.hasText(userId)) {
            onlineUserRegistry.removeSession(userId, client.getSessionId());
            LOGGER.info("Socket disconnected, userId={}, sessionId={}", userId, client.getSessionId());
        }
    }

    private void onPrivateMessageSend(SocketIOClient client, PrivateMessageRequest request, AckRequest ackRequest) {
        String userId = ensureAuthenticated(client);
        if (userId == null) {
            return;
        }
        if (request == null || !StringUtils.hasText(request.getTargetUserId()) || request.getPayload() == null) {
            client.sendEvent("ack:error", ApiResponse.fail(400, "targetUserId and payload are required"));
            return;
        }

        Set<UUID> sessions = onlineUserRegistry.getSessions(request.getTargetUserId());
        if (sessions.isEmpty()) {
            client.sendEvent("ack:error", ApiResponse.fail(404, "target user is offline"));
            return;
        }

        Map<String, Object> message = new HashMap<String, Object>();
        message.put("traceId", request.getTraceId());
        message.put("event", "message:private:receive");
        message.put("fromUserId", userId);
        message.put("targetUserId", request.getTargetUserId());
        message.put("payload", request.getPayload());
        message.put("timestamp", System.currentTimeMillis());

        int delivered = 0;
        for (UUID sessionId : sessions) {
            SocketIOClient targetClient = socketIOServer.getClient(sessionId);
            if (targetClient != null) {
                targetClient.sendEvent("message:private:receive", message);
                delivered++;
            }
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("traceId", request.getTraceId());
        result.put("delivered", delivered);
        client.sendEvent("ack:ok", ApiResponse.success(result));
    }

    private void onTopicSubscribe(SocketIOClient client, TopicSubscriptionRequest request, AckRequest ackRequest) {
        String userId = ensureAuthenticated(client);
        if (userId == null) {
            return;
        }
        String topic = normalizeChannel(request == null ? null : request.getTopic());
        if (!StringUtils.hasText(topic)) {
            client.sendEvent("ack:error", ApiResponse.fail(400, "topic is required"));
            return;
        }

        client.joinRoom(topic);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("topic", topic);
        result.put("userId", userId);
        client.sendEvent("ack:ok", ApiResponse.success(result));
    }

    private void onTopicUnsubscribe(SocketIOClient client, TopicSubscriptionRequest request, AckRequest ackRequest) {
        String userId = ensureAuthenticated(client);
        if (userId == null) {
            return;
        }
        String topic = normalizeChannel(request == null ? null : request.getTopic());
        if (!StringUtils.hasText(topic)) {
            client.sendEvent("ack:error", ApiResponse.fail(400, "topic is required"));
            return;
        }

        client.leaveRoom(topic);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("topic", topic);
        result.put("userId", userId);
        client.sendEvent("ack:ok", ApiResponse.success(result));
    }

    private void onTopicPublish(SocketIOClient client, TopicPublishRequest request, AckRequest ackRequest) {
        String userId = ensureAuthenticated(client);
        if (userId == null) {
            return;
        }
        String topic = normalizeChannel(request == null ? null : request.getTopic());
        if (!StringUtils.hasText(topic) || request.getPayload() == null) {
            client.sendEvent("ack:error", ApiResponse.fail(400, "topic and payload are required"));
            return;
        }

        Map<String, Object> message = new HashMap<String, Object>();
        message.put("traceId", request.getTraceId());
        message.put("event", "topic:message");
        message.put("topic", topic);
        message.put("fromUserId", userId);
        message.put("payload", request.getPayload());
        message.put("timestamp", System.currentTimeMillis());
        socketIOServer.getRoomOperations(topic).sendEvent("topic:message", message);

        client.sendEvent("ack:ok", ApiResponse.success(message));
    }

    private void onBroadcastAll(SocketIOClient client, BroadcastRequest request, AckRequest ackRequest) {
        String userId = ensureAuthenticated(client);
        if (userId == null) {
            return;
        }
        if (request == null || request.getPayload() == null) {
            client.sendEvent("ack:error", ApiResponse.fail(400, "payload is required"));
            return;
        }

        Map<String, Object> message = new HashMap<String, Object>();
        message.put("traceId", request.getTraceId());
        message.put("event", "broadcast:message");
        message.put("fromUserId", userId);
        message.put("payload", request.getPayload());
        message.put("timestamp", System.currentTimeMillis());
        socketIOServer.getBroadcastOperations().sendEvent("broadcast:message", message);

        client.sendEvent("ack:ok", ApiResponse.success(message));
    }

    private void onBroadcastSegment(SocketIOClient client, SegmentBroadcastRequest request, AckRequest ackRequest) {
        String userId = ensureAuthenticated(client);
        if (userId == null) {
            return;
        }
        String segment = normalizeChannel(request == null ? null : request.getSegment());
        if (!StringUtils.hasText(segment) || request.getPayload() == null) {
            client.sendEvent("ack:error", ApiResponse.fail(400, "segment and payload are required"));
            return;
        }

        Map<String, Object> message = new HashMap<String, Object>();
        message.put("traceId", request.getTraceId());
        message.put("event", "broadcast:segment:message");
        message.put("segment", segment);
        message.put("fromUserId", userId);
        message.put("payload", request.getPayload());
        message.put("timestamp", System.currentTimeMillis());
        socketIOServer.getRoomOperations(segment).sendEvent("broadcast:segment:message", message);

        client.sendEvent("ack:ok", ApiResponse.success(message));
    }

    private void onDebugConfigGet(SocketIOClient client, Map request, AckRequest ackRequest) {
        String userId = ensureAuthenticated(client);
        if (userId == null) {
            return;
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("service", "websocket-service");
        result.put("userId", userId);
        result.put("activeProfiles", Arrays.asList(environment.getActiveProfiles()));
        result.put("nacosDiscoveryEnabled",
                environment.getProperty("spring.cloud.nacos.discovery.enabled", "false"));
        result.put("nacosConfigEnabled",
                environment.getProperty("spring.cloud.nacos.config.enabled", "false"));
        result.put("configSource", nacosDebugProperties.getConfigSource());
        result.put("topicPrefix", nacosDebugProperties.getTopicPrefix());
        result.put("strictToken", nacosDebugProperties.isStrictToken());
        client.sendEvent("ack:ok", ApiResponse.success(result));
    }

    private String ensureAuthenticated(SocketIOClient client) {
        String userId = client.get(ATTR_USER_ID);
        if (!StringUtils.hasText(userId)) {
            client.sendEvent("ack:error", ApiResponse.fail(401, "unauthorized socket session"));
            client.disconnect();
            return null;
        }
        return userId;
    }

    private String normalizeChannel(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String channel = value.trim();
        if (channel.length() > 128) {
            return null;
        }
        return channel;
    }
}
