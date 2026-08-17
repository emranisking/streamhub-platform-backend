package com.streamhub.platform.subscription.dto;

import com.streamhub.platform.subscription.entity.Subscription;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class SubscriptionResponse {
    private UUID id;
    private String plan;
    private boolean active;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    public static SubscriptionResponse from(Subscription s) {
        return SubscriptionResponse.builder()
                .id(s.getId())
                .plan(s.getPlan())
                .active(s.isActive())
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .build();
    }
}
