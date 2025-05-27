package com.huang.backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for web pages (non-API endpoints)
 */
@Controller
public class WebPageController {

    /**
     * Serve the WebSocket test page
     * 
     * @return redirect to static HTML file
     */
    @GetMapping("/websocket-test")
    public String websocketTest() {
        return "redirect:/test-websocket.html";
    }
    
    /**
     * Serve the MQTT test page
     * 
     * @return redirect to static HTML file
     */
    @GetMapping("/mqtt-test")
    public String mqttTest() {
        return "redirect:/mqtt-test.html";
    }
} 