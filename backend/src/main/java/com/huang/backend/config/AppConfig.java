package com.huang.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 全局应用配置
 */
@Configuration
@EnableScheduling  // 启用调度任务，用于WebSocket推送
public class AppConfig {
    // 配置声明
} 