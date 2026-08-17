package com.streamhub.platform.subscription.repository;

import com.streamhub.platform.subscription.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, java.util.UUID> {

    @Query("select s from Subscription s where s.user.id = :userId and s.active = true")
    List<Subscription> findActiveByUserId(@Param("userId") Long userId);

    @Query("select s from Subscription s where s.user.id = :userId order by s.createdAt desc")
    List<Subscription> findAllByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
}
