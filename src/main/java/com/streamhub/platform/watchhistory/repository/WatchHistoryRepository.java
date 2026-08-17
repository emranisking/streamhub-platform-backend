package com.streamhub.platform.watchhistory.repository;

import com.streamhub.platform.watchhistory.entity.WatchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WatchHistoryRepository extends JpaRepository<WatchHistory, UUID> {
    Optional<WatchHistory> findByUserIdAndVideoId(Long userId, UUID videoId);
    Page<WatchHistory> findByUserIdOrderByWatchedAtDesc(Long userId, Pageable pageable);
}
