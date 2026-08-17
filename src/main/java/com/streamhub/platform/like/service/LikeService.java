package com.streamhub.platform.like.service;

import com.streamhub.platform.like.entity.UserLike;
import com.streamhub.platform.like.repository.UserLikeRepository;
import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.video.entity.Video;
import com.streamhub.platform.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Properly wired and registered this time (in the original codebase,
 * LikeService/LikesController existed but were never added to the module's
 * providers, so /likes was effectively dead code). Every like is now backed
 * by a real per-user UserLike record instead of a bare counter increment.
 */
@Service
@RequiredArgsConstructor
public class LikeService {

    private final UserLikeRepository userLikeRepository;
    private final VideoRepository videoRepository;

    @Transactional
    public boolean toggleLike(User user, Video video) {
        return userLikeRepository.findByUserIdAndVideoId(user.getId(), video.getId())
                .map(existing -> {
                    userLikeRepository.delete(existing);
                    videoRepository.decrementLikes(video.getId());
                    return false;
                })
                .orElseGet(() -> {
                    UserLike like = UserLike.builder().user(user).video(video).likedAt(LocalDateTime.now()).build();
                    userLikeRepository.save(like);
                    videoRepository.incrementLikes(video.getId());
                    return true;
                });
    }

    public Page<UserLike> getUserLikes(Long userId, Pageable pageable) {
        return userLikeRepository.findByUserIdOrderByLikedAtDesc(userId, pageable);
    }
}
