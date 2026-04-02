package com.youngplace.websocket.config;

import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.Transport;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SocketIoProperties.class)
public class SocketIoServerConfiguration {

    @Bean
    public SocketIOServer socketIOServer(SocketIoProperties properties) {
        com.corundumstudio.socketio.Configuration configuration = new com.corundumstudio.socketio.Configuration();
        configuration.setHostname(properties.getHost());
        configuration.setPort(properties.getPort());
        configuration.setOrigin(properties.getOrigin());
        configuration.setPingInterval(properties.getPingIntervalMs());
        configuration.setPingTimeout(properties.getPingTimeoutMs());
        configuration.setTransports(Transport.WEBSOCKET, Transport.POLLING);
        return new SocketIOServer(configuration);
    }
}
