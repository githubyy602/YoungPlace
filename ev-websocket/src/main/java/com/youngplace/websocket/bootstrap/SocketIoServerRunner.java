package com.youngplace.websocket.bootstrap;

import com.corundumstudio.socketio.SocketIOServer;
import com.youngplace.websocket.config.SocketIoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SocketIoServerRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketIoServerRunner.class);

    private final SocketIOServer socketIOServer;
    private final SocketIoProperties socketIoProperties;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public SocketIoServerRunner(SocketIOServer socketIOServer, SocketIoProperties socketIoProperties) {
        this.socketIOServer = socketIOServer;
        this.socketIoProperties = socketIoProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (started.compareAndSet(false, true)) {
            socketIOServer.start();
            LOGGER.info("Socket.IO server started at {}:{}", socketIoProperties.getHost(), socketIoProperties.getPort());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (started.compareAndSet(true, false)) {
            socketIOServer.stop();
            LOGGER.info("Socket.IO server stopped");
        }
    }
}
