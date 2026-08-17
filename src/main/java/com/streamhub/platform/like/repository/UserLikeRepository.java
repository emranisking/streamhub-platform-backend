package com.streamhub.platform.like.repository;

import com.streamhub.platform.like.entity.UserLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserLikeRepository extends JpaRepository<UserLike, UUID> {
    Optional<UserLike> findByUserIdAndVideoId(Long userId, UUID videoId);
    boolean existsByUserIdAndVideoId(Long userId, UUID videoId);
    Page<UserLike> findByUserIdOrderByLikedAtDesc(Long userId, Pageable pageable);
}
