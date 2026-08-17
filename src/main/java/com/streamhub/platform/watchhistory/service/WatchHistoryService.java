package com.streamhub.platform.watchhistory.service;

import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.video.entity.Video;
import com.streamhub.platform.watchhistory.entity.WatchHistory;
import com.streamhub.platform.watchhistory.repository.WatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WatchHistoryService {

    private final WatchHistoryRepository watchHistoryRepository;

    @Transactional
    public WatchHistory upsert(User user, Video video) {
        WatchHistory history = watchHistoryRepository.findByUserIdAndVideoId(user.getId(), video.getId())
                .orElseGet(() -> WatchHistory.builder().user(user).video(video).build());
        history.setWatchedAt(LocalDateTime.now());
        return watchHistoryRepository.save(history);
    }

    public Page<WatchHistory> getUserHistory(Long userId, Pageable pageable) {
        return watchHistoryRepository.findByUserIdOrderByWatchedAtDesc(userId, pageable);
    }
}
