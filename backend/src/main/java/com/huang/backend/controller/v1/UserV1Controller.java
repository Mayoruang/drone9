package com.huang.backend.controller.v1;

import com.huang.backend.payload.response.ApiResponse;
import com.huang.backend.payload.response.UserInfoResponse;
import com.huang.backend.security.service.UserDetailsImpl;
import com.huang.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserV1Controller {

    private final UserService userService;

    @GetMapping("/info")
    public ApiResponse<UserInfoResponse> getUserInfo(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ApiResponse.success(userService.getUserInfo(userDetails.getUsername()));
    }
} 