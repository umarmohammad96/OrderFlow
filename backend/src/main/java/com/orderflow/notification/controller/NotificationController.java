package com.orderflow.notification.controller;

import com.orderflow.auth.security.CurrentUserId;
import com.orderflow.common.response.ApiResponse;
import com.orderflow.notification.dto.NotificationResponse;
import com.orderflow.notification.service.NotificationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<NotificationResponse>> list(@CurrentUserId Long userId) {
        return ApiResponse.ok(notificationService.forUser(userId));
    }
}
