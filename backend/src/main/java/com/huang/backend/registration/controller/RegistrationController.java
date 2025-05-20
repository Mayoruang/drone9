package com.huang.backend.registration.controller;

import com.huang.backend.registration.dto.AdminActionDto;
import com.huang.backend.registration.dto.AdminActionResponseDto;
import com.huang.backend.registration.dto.DroneRegistrationRequestDto;
import com.huang.backend.registration.dto.DroneRegistrationResponseDto;
import com.huang.backend.registration.dto.RegistrationStatusResponseDto;
import com.huang.backend.registration.entity.DroneRegistrationRequest;
import com.huang.backend.registration.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for handling drone registration requests
 */
@RestController
@RequestMapping("/api/v1")
public class RegistrationController {

    private final RegistrationService registrationService;

    @Autowired
    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * Register a new drone
     * 
     * @param requestDto the registration request
     * @return 202 Accepted with registration details
     */
    @PostMapping("/drones/register")
    public ResponseEntity<DroneRegistrationResponseDto> registerDrone(@Valid @RequestBody DroneRegistrationRequestDto requestDto) {
        DroneRegistrationResponseDto responseDto = registrationService.registerDrone(requestDto);
        return new ResponseEntity<>(responseDto, HttpStatus.ACCEPTED);
    }
    
    /**
     * Get the status of a drone registration request
     * 
     * @param requestId the ID of the registration request
     * @return 200 OK with registration status details
     */
    @GetMapping("/drones/registration/{requestId}/status")
    public ResponseEntity<RegistrationStatusResponseDto> getRegistrationStatus(@PathVariable UUID requestId) {
        RegistrationStatusResponseDto statusDto = registrationService.getRegistrationStatus(requestId);
        return ResponseEntity.ok(statusDto);
    }
    
    /**
     * Get a paginated list of registration requests
     * 
     * @param status optional status filter
     * @param page page number (0-based)
     * @param size page size
     * @return 200 OK with paginated registration requests
     */
    @GetMapping("/drones/registration/list")
    public ResponseEntity<Page<RegistrationStatusResponseDto>> getRegistrationList(
            @RequestParam(required = false) DroneRegistrationRequest.RegistrationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<RegistrationStatusResponseDto> registrations = registrationService.getRegistrationList(
                status, PageRequest.of(page, size, Sort.by("requestedAt").descending()));
        return ResponseEntity.ok(registrations);
    }
    
    /**
     * Process an admin action on a registration request (approve or reject)
     * 
     * @param actionDto the admin action data
     * @return 200 OK with action result
     */
    @PostMapping("/admin/registrations/action")
    // 添加安全注解，允许任何访问者访问此端点
    // @PreAuthorize("hasRole('ADMIN')") // 如果只允许管理员访问，取消注释此行并注释下一行
    public ResponseEntity<AdminActionResponseDto> processAdminAction(@Valid @RequestBody AdminActionDto actionDto) {
        AdminActionResponseDto responseDto = registrationService.processAdminAction(actionDto);
        return ResponseEntity.ok(responseDto);
    }
} 