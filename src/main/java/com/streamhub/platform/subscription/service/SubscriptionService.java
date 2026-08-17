package com.streamhub.platform.subscription.service;

import com.streamhub.platform.subscription.entity.Subscription;
import com.streamhub.platform.subscription.repository.SubscriptionRepository;
import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fixes original bug: creating a subscription now deactivates any existing
 * active subscriptions for the user first, so overlapping subscriptions can
 * no longer occur.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final int SUBSCRIPTION_DURATION_DAYS = 30;

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Transactional
    public Subscription subscribe(User user, String plan) {
        deactivateExistingSubscriptions(user.getId());

        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusDays(SUBSCRIPTION_DURATION_DAYS);

        Subscription subscription = Subscription.builder()
                .plan(plan == null || plan.isBlank() ? "basic" : plan)
                .active(true)
                .startDate(start)
                .endDate(end)
                .user(user)
                .build();
        subscription = subscriptionRepository.save(subscription);

        user.setSubscribed(true);
        user.setSubscriptionTier(subscription.getPlan());
        user.setSubscriptionExpiry(end);
        userRepository.save(user);

        return subscription;
    }

    @Transactional
    public void cancel(User user) {
        deactivateExistingSubscriptions(user.getId());
        user.setSubscribed(false);
        userRepository.save(user);
    }

    public boolean isUserSubscribed(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        return user.hasActiveSubscription();
    }

    public List<Subscription> history(Long userId) {
        return subscriptionRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    private void deactivateExistingSubscriptions(Long userId) {
        List<Subscription> active = subscriptionRepository.findActiveByUserId(userId);
        active.forEach(s -> s.setActive(false));
        subscriptionRepository.saveAll(active);
    }
}
