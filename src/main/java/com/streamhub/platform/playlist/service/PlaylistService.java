package com.streamhub.platform.playlist.service;

import com.streamhub.platform.common.exception.BadRequestException;
import com.streamhub.platform.common.exception.ForbiddenException;
import com.streamhub.platform.common.exception.ResourceNotFoundException;
import com.streamhub.platform.playlist.entity.Playlist;
import com.streamhub.platform.playlist.entity.PlaylistItem;
import com.streamhub.platform.playlist.repository.PlaylistItemRepository;
import com.streamhub.platform.playlist.repository.PlaylistRepository;
import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.video.entity.Video;
import com.streamhub.platform.video.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Fixes original bugs: ownership is verified via the authenticated
 * SecurityContext user (not a manually decoded, unverified JWT), and
 * `newPosition` is validated before being applied.
 */
@Service
@RequiredArgsConstructor
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistItemRepository playlistItemRepository;
    private final VideoService videoService;

    public List<Playlist> getAllForUser(Long userId) {
        return playlistRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public Playlist create(User user, String title) {
        Playlist playlist = Playlist.builder().title(title).user(user).build();
        return playlistRepository.save(playlist);
    }

    public Playlist getById(UUID id) {
        return playlistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Playlist not found: " + id));
    }

    private Playlist getOwned(UUID id, Long userId) {
        Playlist playlist = getById(id);
        if (!playlist.getUser().getId().equals(userId)) {
            throw new ForbiddenException("This playlist does not belong to you");
        }
        return playlist;
    }

    @Transactional
    public Playlist addVideo(UUID playlistId, UUID videoId, Long userId) {
        Playlist playlist = getOwned(playlistId, userId);
        if (playlistItemRepository.existsByPlaylistIdAndVideoId(playlistId, videoId)) {
            throw new BadRequestException("This video is already in the playlist");
        }
        Video video = videoService.getEntityById(videoId);
        long nextPosition = playlistItemRepository.countByPlaylistId(playlistId);

        PlaylistItem item = PlaylistItem.builder()
                .playlist(playlist)
                .video(video)
                .position((int) nextPosition)
                .build();
        playlistItemRepository.save(item);
        return getById(playlistId);
    }

    @Transactional
    public Playlist removeVideo(UUID playlistId, UUID videoId, Long userId) {
        getOwned(playlistId, userId);
        PlaylistItem item = playlistItemRepository.findByPlaylistIdAndVideoId(playlistId, videoId)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found in playlist"));
        playlistItemRepository.delete(item);
        reindex(playlistId);
        return getById(playlistId);
    }

    @Transactional
    public Playlist moveVideo(UUID playlistId, UUID videoId, int newPosition, Long userId) {
        getOwned(playlistId, userId);
        if (newPosition < 0) {
            throw new BadRequestException("newPosition must be >= 0");
        }
        List<PlaylistItem> items = playlistItemRepository.findByPlaylistIdOrderByPositionAsc(playlistId);
        PlaylistItem target = items.stream()
                .filter(i -> i.getVideo().getId().equals(videoId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Video not found in playlist"));

        int clampedPosition = Math.min(newPosition, items.size() - 1);
        items.remove(target);
        items.add(clampedPosition, target);

        for (int i = 0; i < items.size(); i++) {
            items.get(i).setPosition(i);
        }
        playlistItemRepository.saveAll(items);
        return getById(playlistId);
    }

    @Transactional
    public void delete(UUID playlistId, Long userId) {
        Playlist playlist = getOwned(playlistId, userId);
        playlist.markDeleted();
        playlistRepository.save(playlist);
    }

    private void reindex(UUID playlistId) {
        List<PlaylistItem> items = playlistItemRepository.findByPlaylistIdOrderByPositionAsc(playlistId);
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setPosition(i);
        }
        playlistItemRepository.saveAll(items);
    }
}
