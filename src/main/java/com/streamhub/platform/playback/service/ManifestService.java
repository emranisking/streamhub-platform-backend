package com.streamhub.platform.playback.service;

import com.streamhub.platform.common.exception.ResourceNotFoundException;
import com.streamhub.platform.video.entity.Video;
import com.streamhub.platform.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ManifestService {

    private final VideoRepository videoRepository;

    public String getManifestUrl(UUID videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found: " + videoId));
        if (video.getVideoUrl() == null) {
            throw new ResourceNotFoundException("This video has not finished processing yet");
        }
        return video.getVideoUrl();
    }
}
