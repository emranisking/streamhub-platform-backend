package com.streamhub.platform.video.service;

import com.streamhub.platform.category.entity.Category;
import com.streamhub.platform.common.aop.ResponseSourceContext;
import com.streamhub.platform.common.cache.RedisCacheService;
import com.streamhub.platform.common.exception.ResourceNotFoundException;
import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.video.dto.VideoDetailResponse;
import com.streamhub.platform.video.entity.Video;
import com.streamhub.platform.video.repository.VideoRepository;
import com.streamhub.platform.watchhistory.service.WatchHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final WatchHistoryService watchHistoryService;
    private final RedisCacheService redisCacheService;

    public Page<Video> getVideos(Optional<UUID> categoryId, Pageable pageable) {
        String cacheKey = "videos:list:" + categoryId.map(UUID::toString).orElse("all")
                + ":" + pageable.getPageNumber() + ":" + pageable.getPageSize();
        // Page objects don't serialize cleanly for generic caching without extra
        // Jackson config, so list pages are read straight from the DB and the
        // response source is marked explicitly for the AOP logger.
        ResponseSourceContext.mark(ResponseSourceContext.Source.DATABASE);
        return categoryId.map(id -> videoRepository.findByCategoryId(id, pageable))
                .orElseGet(() -> videoRepository.findAll(pageable));
    }

    public Video getEntityById(UUID id) {
        return videoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found: " + id));
    }

    /**
     * Cached video detail lookup - demonstrates the required "response came
     * from Redis vs. the database" behaviour: repeated calls for the same
     * video within the TTL window are served straight from Redis and the
     * {@link com.streamhub.platform.common.aop.LoggingAspect} logs
     * `source=REDIS_CACHE`; the first call (and any call after the cache
     * entry expires or is evicted) is logged as `source=DATABASE`.
     */
    public VideoDetailResponse getDetailCached(UUID id) {
        return redisCacheService.getOrLoad("video:detail:" + id, VideoDetailResponse.class, Duration.ofMinutes(15),
                () -> VideoDetailResponse.from(getEntityById(id)));
    }

    @Transactional
    public Video incrementViews(UUID id, User userOrNull) {
        videoRepository.incrementViews(id);
        Video video = getEntityById(id);
        if (userOrNull != null) {
            watchHistoryService.upsert(userOrNull, video);
        }
        redisCacheService.delete("video:detail:" + id);
        return video;
    }

    @Transactional
    public Video assignCategory(UUID videoId, Category category) {
        Video video = getEntityById(videoId);
        video.setCategory(category);
        video = videoRepository.save(video);
        redisCacheService.delete("video:detail:" + videoId);
        return video;
    }

    @Transactional
    public void delete(UUID id) {
        Video video = getEntityById(id);
        video.markDeleted();
        videoRepository.save(video);
        redisCacheService.delete("video:detail:" + id);
    }
}
