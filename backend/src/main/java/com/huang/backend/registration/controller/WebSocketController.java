package com.huang.backend.registration.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

/**
 * Controller for handling WebSocket messages
 */
@Controller("registrationWebSocketController")
public class WebSocketController {

    /**
     * Simple echo endpoint for testing WebSocket connectivity
     *
     * @param message the message to echo
     * @return the echo response
     */
    @MessageMapping("/registration/echo")
    @SendTo("/topic/registration/echo")
    public String echo(String message) {
        return "[Server] Echo: " + message;
    }
} 