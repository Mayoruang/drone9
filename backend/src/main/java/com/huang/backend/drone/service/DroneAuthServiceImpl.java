package com.huang.backend.drone.service;

import com.huang.backend.drone.dto.DroneAuthRequestDto;
import com.huang.backend.drone.dto.DroneAuthResponseDto;
import com.huang.backend.drone.entity.Drone;
import com.huang.backend.drone.repository.DroneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of DroneAuthService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroneAuthServiceImpl implements DroneAuthService {
    
    private final DroneRepository droneRepository;
    private final PasswordEncoder passwordEncoder;
    
    // 30天过期时间
    private static final long TOKEN_EXPIRATION = 30 * 24 * 60 * 60 * 1000L;
    
    @Override
    public DroneAuthResponseDto authenticateDrone(DroneAuthRequestDto authRequest) {
        log.info("Authenticating drone: {}", authRequest.getSerialNumber());
        
        // 查找无人机
        Drone drone = droneRepository.findBySerialNumber(authRequest.getSerialNumber())
                .orElseThrow(() -> new BadCredentialsException("无效的无人机凭证"));
        
        // 验证密码
        if (!passwordEncoder.matches(authRequest.getPassword(), drone.getMqttPasswordHash())) {
            throw new BadCredentialsException("无效的无人机凭证");
        }
        
        // 更新状态为在线
        if (drone.getCurrentStatus() == Drone.DroneStatus.OFFLINE) {
            drone.setCurrentStatus(Drone.DroneStatus.ONLINE);
            droneRepository.save(drone);
        }
        
        // 生成响应
        String mqttResponseTopic = drone.getMqttTopicCommands().replace("commands", "responses");
        
        return DroneAuthResponseDto.builder()
                .droneId(drone.getDroneId().toString())
                .token("mock-token-" + System.currentTimeMillis()) // 实际应生成JWT
                .expiresIn(TOKEN_EXPIRATION)
                .mqttBrokerUrl(drone.getMqttBrokerUrl())
                .mqttUsername(drone.getMqttUsername())
                .mqttPassword(authRequest.getPassword())
                .mqttTopicTelemetry(drone.getMqttTopicTelemetry())
                .mqttTopicCommands(drone.getMqttTopicCommands())
                .mqttTopicResponses(mqttResponseTopic)
                .build();
    }
} 