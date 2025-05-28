package com.huang.backend.controller.v1;

import com.huang.backend.payload.request.LoginRequest;
import com.huang.backend.payload.response.ApiResponse;
import com.huang.backend.payload.response.JwtResponse;
import com.huang.backend.security.service.UserDetailsImpl;
import com.huang.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthV1Controller {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<JwtResponse> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        return ApiResponse.success(authService.login(loginRequest, response));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ApiResponse.success();
    }

    @PostMapping("/refresh")
    public ApiResponse<String> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(authService.refreshToken(request, response));
    }

    /**
     * 获取用户权限码
     */
    @GetMapping("/codes")
    public ApiResponse<List<String>> getAccessCodes(@RequestParam String username) {
        return ApiResponse.success(authService.getPermissionCodes(username));
    }
} 