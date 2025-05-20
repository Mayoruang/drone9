package com.huang.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for the application
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configure the message broker for WebSocket communication
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Set prefix for messages from server to client
        config.enableSimpleBroker("/topic");

        // Set prefix for messages from clients to server
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Register STOMP endpoints
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register the endpoint for admin notifications
        registry.addEndpoint("/ws/admin")
                .setAllowedOriginPatterns("*") // In production, restrict this
                .withSockJS(); // Provides fallback options if WebSocket is not available

        // Register the endpoint for drone position data
        registry.addEndpoint("/ws/drones")
                .setAllowedOriginPatterns("*")  // Using patterns instead of specific origins
                .withSockJS()
                .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js")
                .setSessionCookieNeeded(false);
    }
}