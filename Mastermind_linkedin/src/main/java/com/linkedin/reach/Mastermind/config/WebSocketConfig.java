package com.linkedin.reach.Mastermind.config;

import com.linkedin.reach.Mastermind.server.MastermindWebSocketServer;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Class that enables the web socket configuration and the endpoints.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /**
     * Method used to define WebSocket endpoints and associate them with specific handlers. The handlers
     * are responsible for processing incoming websocket messages and sending responses back to the client.
     * @param registry interface provided by the Spring Framework to configure WebSocket handlers and their associated URL mappings.
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new MastermindWebSocketServer(), "/ws/singleplayer")
                .setAllowedOrigins("*");
        registry.addHandler(new MastermindWebSocketServer(), "/ws/multiplayer")
                .setAllowedOrigins("*"); // Accept all origin connections
    }
}
