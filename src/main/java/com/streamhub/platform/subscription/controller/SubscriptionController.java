package com.streamhub.platform.subscription.controller;

import com.streamhub.platform.common.response.ApiResponse;
import com.streamhub.platform.subscription.dto.SubscribeRequest;
import com.streamhub.platform.subscription.dto.SubscriptionResponse;
import com.streamhub.platform.subscription.entity.Subscription;
import com.streamhub.platform.subscription.service.SubscriptionService;
import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Storyline: this is Carol's module - she moves from "free user with a
 * watch limit" to "unlimited playback" by hitting /subscribe.
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final UserService userService;

    @PostMapping("/subscribe")
    @Operation(summary = "Activate a 30-day subscription for the current user")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> subscribe(@RequestBody(required = false) SubscribeRequest request) {
        User user = userService.getCurrentUser();
        String plan = request == null ? "basic" : request.getPlan();
        Subscription subscription = subscriptionService.subscribe(user, plan);
        return ResponseEntity.ok(ApiResponse.ok("Subscription activated", SubscriptionResponse.from(subscription)));
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel the current user's active subscription")
    public ResponseEntity<ApiResponse<Void>> cancel() {
        User user = userService.getCurrentUser();
        subscriptionService.cancel(user);
        return ResponseEntity.ok(ApiResponse.message("Subscription cancelled"));
    }

    @GetMapping("/me")
    @Operation(summary = "List the current user's subscription history")
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> myHistory() {
        User user = userService.getCurrentUser();
        List<SubscriptionResponse> history = subscriptionService.history(user.getId()).stream()
                .map(SubscriptionResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.ok(history));
    }
}
