package com.streamhub.platform.user.repository;

import com.streamhub.platform.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByUid(UUID uid);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    long countByCreatedAtBetween(java.time.LocalDateTime from, java.time.LocalDateTime to);
}
