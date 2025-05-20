package com.huang.backend.registration.service;

import com.huang.backend.drone.entity.Drone;
import com.huang.backend.drone.repository.DroneRepository;
import com.huang.backend.exception.ResourceNotFoundException;
import com.huang.backend.registration.dto.AdminActionDto;
import com.huang.backend.registration.dto.AdminActionResponseDto;
import com.huang.backend.registration.dto.DroneRegistrationRequestDto;
import com.huang.backend.registration.dto.DroneRegistrationResponseDto;
import com.huang.backend.registration.dto.MqttCredentialsDto;
import com.huang.backend.registration.dto.RegistrationNotificationDto;
import com.huang.backend.registration.dto.RegistrationStatusResponseDto;
import com.huang.backend.registration.entity.DroneRegistrationRequest;
import com.huang.backend.registration.repository.DroneRegistrationRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.Random;
import java.util.Optional;

/**
 * Implementation of the RegistrationService
 */
@Service
public class RegistrationServiceImpl implements RegistrationService {

    private final DroneRegistrationRequestRepository registrationRepository;
    private final DroneRepository droneRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SimpMessagingTemplate messagingTemplate;
    
    @Value("${application.base-url:http://localhost:8080}")
    private String baseUrl;
    
    @Value("${application.mqtt.broker-url:tcp://localhost:1883}")
    private String mqttBrokerUrl;
    
    // 添加MQTT主题配置
    @Value("${mqtt.topics.telemetry:drones/+/telemetry}")
    private String mqttTopicTelemetryPattern;
    
    @Value("${mqtt.topics.commands:drones/+/commands}")
    private String mqttTopicCommandsPattern;
    
    @Value("${mqtt.topics.responses:drones/+/responses}")
    private String mqttTopicResponsesPattern;

    @Autowired
    public RegistrationServiceImpl(
            DroneRegistrationRequestRepository registrationRepository,
            DroneRepository droneRepository,
            BCryptPasswordEncoder passwordEncoder,
            SimpMessagingTemplate messagingTemplate) {
        this.registrationRepository = registrationRepository;
        this.droneRepository = droneRepository;
        this.passwordEncoder = passwordEncoder;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Process a new drone registration request
     * 
     * @param requestDto the registration request data
     * @return the registration response with request ID and status check URL
     */
    @Override
    @Transactional
    public DroneRegistrationResponseDto registerDrone(DroneRegistrationRequestDto requestDto) {
        // Check if a drone with this serial number already exists
        if (registrationRepository.existsBySerialNumber(requestDto.getSerialNumber())) {
            throw new IllegalArgumentException("A drone with serial number " + requestDto.getSerialNumber() + " is already registered or pending approval");
        }
        
        // Create a new registration request entity
        DroneRegistrationRequest registrationRequest = DroneRegistrationRequest.builder()
                .serialNumber(requestDto.getSerialNumber())
                .model(requestDto.getModel())
                .adminNotes(requestDto.getNotes())
                .status(DroneRegistrationRequest.RegistrationStatus.PENDING_APPROVAL)
                .requestedAt(ZonedDateTime.now())
                .build();
        
        // Save the registration request
        DroneRegistrationRequest savedRequest = registrationRepository.save(registrationRequest);
        
        // Send WebSocket notification for new registration
        sendRegistrationNotification(
                RegistrationNotificationDto.NotificationType.NEW_REGISTRATION,
                savedRequest
        );
        
        // Build the status check URL
        String statusCheckUrl = baseUrl + "/spring/api/v1/drones/registration/" + savedRequest.getRequestId() + "/status";
        
        // Return the response DTO
        return DroneRegistrationResponseDto.builder()
                .requestId(savedRequest.getRequestId())
                .message("Your registration request has been received and is pending approval. Please check the status periodically.")
                .statusCheckUrl(statusCheckUrl)
                .build();
    }

    /**
     * Get the status of a drone registration request
     *
     * @param requestId the ID of the registration request
     * @return the registration status response
     * @throws ResourceNotFoundException if request not found
     */
    @Override
    @Transactional(readOnly = true)
    public RegistrationStatusResponseDto getRegistrationStatus(UUID requestId) {
        // Find the registration request
        DroneRegistrationRequest request = registrationRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration request", "id", requestId));
        
        // Build response with basic fields
        RegistrationStatusResponseDto.RegistrationStatusResponseDtoBuilder responseBuilder = RegistrationStatusResponseDto.builder()
                .requestId(request.getRequestId())
                .serialNumber(request.getSerialNumber())
                .model(request.getModel())
                .status(request.getStatus())
                .requestedAt(request.getRequestedAt())
                .processedAt(request.getProcessedAt());
        
        // Add message based on status
        String message;
        switch (request.getStatus()) {
            case PENDING_APPROVAL:
                message = "您的注册请求已收到，正在等待管理员审批。请稍后检查状态。";
                break;
            case APPROVED:
                message = "您的注册请求已批准。请使用提供的MQTT凭证连接到服务器。";
                break;
            case REJECTED:
                message = "很抱歉，您的注册请求被拒绝" + (request.getAdminNotes() != null ? "，原因: " + request.getAdminNotes() : "。");
                break;
            default:
                message = "未知状态: " + request.getStatus();
                break;
        }
        responseBuilder.message(message);
        
        // If approved, check for drone record and add credentials
        if (request.getStatus() == DroneRegistrationRequest.RegistrationStatus.APPROVED && request.getDroneId() != null) {
            Optional<Drone> droneOpt = droneRepository.findById(request.getDroneId());
            if (droneOpt.isPresent()) {
                Drone drone = droneOpt.get();
                responseBuilder.droneId(drone.getDroneId());
            
                // Generate a temporary MQTT password for the client
            String mqttPassword = generateRandomPassword(12);
            
            // In a real production system, we would need a more secure way to handle passwords
            // For example, using a one-time token system
            
            MqttCredentialsDto credentials = MqttCredentialsDto.builder()
                    .mqttBrokerUrl(drone.getMqttBrokerUrl())
                    .mqttUsername(drone.getMqttUsername())
                    .mqttPassword(mqttPassword) // Using regenerated password for demo
                    .mqttTopicTelemetry(drone.getMqttTopicTelemetry())
                    .mqttTopicCommands(drone.getMqttTopicCommands())
                    .build();
            
            // Add credentials to response
            responseBuilder.mqttCredentials(credentials);
            
            // Update password hash in database with new password
            drone.setMqttPasswordHash(passwordEncoder.encode(mqttPassword));
            droneRepository.save(drone);
            }
        }
        
        // Build and return the response DTO
        return responseBuilder.build();
    }
    
    /**
     * Get a paginated list of registration requests
     *
     * @param status optional status filter
     * @param pageable pagination information
     * @return a page of registration status responses
     */
    @Override
    @Transactional(readOnly = true)
    public Page<RegistrationStatusResponseDto> getRegistrationList(DroneRegistrationRequest.RegistrationStatus status, Pageable pageable) {
        // 根据状态过滤查询
        Page<DroneRegistrationRequest> requests;
        if (status != null) {
            // Explicit filter requested by caller
            requests = registrationRepository.findByStatus(status, pageable);
        } else {
            // Default: exclude DELETED records so UI does not show removed drones
            requests = registrationRepository.findByStatusNot(DroneRegistrationRequest.RegistrationStatus.DELETED, pageable);
        }
        
        // 转换为DTO
        return requests.map(request -> {
            // 构建消息
            String message;
            switch (request.getStatus()) {
                case PENDING_APPROVAL:
                    message = "等待管理员审批";
                    break;
                case APPROVED:
                    message = "已批准，无人机ID: " + request.getDroneId();
                    break;
                case REJECTED:
                    message = "已拒绝" + (request.getAdminNotes() != null ? "，原因: " + request.getAdminNotes() : "");
                    break;
                default:
                    message = "状态: " + request.getStatus();
                    break;
            }
            
            // 创建DTO
            return RegistrationStatusResponseDto.builder()
                    .requestId(request.getRequestId())
                    .serialNumber(request.getSerialNumber())
                    .model(request.getModel())
                    .status(request.getStatus())
                    .requestedAt(request.getRequestedAt())
                    .processedAt(request.getProcessedAt())
                    .droneId(request.getDroneId())
                    .message(message)
                    .build();
        });
    }
    
    /**
     * Process an admin action on a registration request
     *
     * @param actionDto the admin action data
     * @return the admin action response
     * @throws ResourceNotFoundException if request not found
     * @throws IllegalArgumentException if the action is invalid for the current request status
     */
    @Override
    @Transactional
    public AdminActionResponseDto processAdminAction(AdminActionDto actionDto) {
        // Find the registration request
        DroneRegistrationRequest request = registrationRepository.findById(actionDto.getRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("Registration request", "id", actionDto.getRequestId()));
        
        // Verify the request is in PENDING_APPROVAL status
        if (request.getStatus() != DroneRegistrationRequest.RegistrationStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException("Cannot process action on a request with status: " + request.getStatus());
        }
        
        // Set processed time
        request.setProcessedAt(ZonedDateTime.now());
        
        // Process based on action type
        String message;
        UUID droneId = null;
        
        if (actionDto.getAction() == AdminActionDto.Action.APPROVE) {
            // Approve the request
            request.setStatus(DroneRegistrationRequest.RegistrationStatus.APPROVED);
            
            // Create a new drone record
            droneId = createDroneFromRequest(request);
            request.setDroneId(droneId);
            
            message = "Registration request approved successfully. Drone ID: " + droneId;
        } else {
            // Reject the request
            request.setStatus(DroneRegistrationRequest.RegistrationStatus.REJECTED);
            request.setAdminNotes(actionDto.getRejectionReason());
            
            message = "Registration request rejected.";
        }
        
        // Save the updated request
        DroneRegistrationRequest savedRequest = registrationRepository.save(request);
        
        // Send WebSocket notification for registration update
        sendRegistrationNotification(
                RegistrationNotificationDto.NotificationType.REGISTRATION_UPDATE,
                savedRequest
        );
        
        // Return the response
        return AdminActionResponseDto.builder()
                .requestId(request.getRequestId())
                .action(actionDto.getAction())
                .droneId(droneId)
                .message(message)
                .build();
    }
    
    /**
     * Helper method to create a new Drone entity from an approved registration request
     *
     * @param request the approved registration request
     * @return the generated drone ID
     */
    private UUID createDroneFromRequest(DroneRegistrationRequest request) {
        // Generate UUID for the drone
        UUID droneId = UUID.randomUUID();
        
        // Generate MQTT credentials
        String mqttUsername = "drone_" + droneId.toString().replace("-", "").substring(0, 8);
        String mqttPassword = generateRandomPassword(12);
        String passwordHash = passwordEncoder.encode(mqttPassword);
        
        // Create MQTT topics based on patterns
        String droneIdentifier = request.getSerialNumber(); // 使用序列号作为唯一标识
        String telemetryTopic = mqttTopicTelemetryPattern.replace("+", droneIdentifier);
        String commandsTopic = mqttTopicCommandsPattern.replace("+", droneIdentifier);
        
        // Create new drone
        Drone drone = Drone.builder()
                .droneId(droneId)
                .serialNumber(request.getSerialNumber())
                .model(request.getModel())
                .registrationRequestId(request.getRequestId())
                .approvedAt(ZonedDateTime.now())
                .mqttBrokerUrl(mqttBrokerUrl)
                .mqttUsername(mqttUsername)
                .mqttPasswordHash(passwordHash)
                .mqttTopicTelemetry(telemetryTopic)
                .mqttTopicCommands(commandsTopic)
                .createdAt(ZonedDateTime.now())
                .updatedAt(ZonedDateTime.now())
                .currentStatus(Drone.DroneStatus.IDLE)
                .build();
        
        // Save the drone
        droneRepository.save(drone);
        
        // TODO: In a real system, we would need to securely communicate the plain text password
        // to the client. This could be via a one-time view, encrypted email, or other secure channel.
        // For now, the password is generated but not returned to keep the implementation simple.
        
        return droneId;
    }
    
    /**
     * Generate a random password with the specified length
     *
     * @param length the desired password length
     * @return a random password
     */
    private String generateRandomPassword(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+";
        StringBuilder password = new StringBuilder();
        Random random = new Random();
        
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(characters.length());
            password.append(characters.charAt(index));
        }
        
        return password.toString();
    }
    
    /**
     * Send registration notification via WebSocket
     *
     * @param type type of notification
     * @param request the registration request
     */
    private void sendRegistrationNotification(RegistrationNotificationDto.NotificationType type, DroneRegistrationRequest request) {
        RegistrationNotificationDto notification = RegistrationNotificationDto.builder()
                .type(type)
                .requestId(request.getRequestId())
                .serialNumber(request.getSerialNumber())
                .model(request.getModel())
                .status(request.getStatus())
                .requestedAt(request.getRequestedAt())
                .processedAt(request.getProcessedAt())
                .build();
        
        messagingTemplate.convertAndSend("/topic/registrations", notification);
    }
} 