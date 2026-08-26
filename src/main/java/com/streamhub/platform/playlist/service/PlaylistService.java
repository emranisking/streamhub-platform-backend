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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Fixes original bugs: ownership is verified via the authenticated
 * SecurityContext user (not a manually decoded, unverified JWT), and
 * `newPosition` is validated before being applied.
 *
 * ⭐ NEW: Added eager loading for playlist items to prevent LazyInitializationException
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistItemRepository playlistItemRepository;
    private final VideoService videoService;

    /**
     * ⭐ FIXED: Get all playlists with items eagerly loaded
     */
    @Transactional(readOnly = true)
    public List<Playlist> getAllForUser(User user) {
        log.info("📋 Fetching playlists for user: {}", user.getUsername());
        List<Playlist> playlists = playlistRepository.findByUserWithItems(user);
        log.info("📋 Found {} playlists", playlists.size());
        return playlists;
    }

    /**
     * ⭐ FIXED: Get all playlists by user ID (with eager loading)
     */
    @Transactional(readOnly = true)
    public List<Playlist> getAllForUserById(Long userId) {
        log.info("📋 Fetching playlists for user ID: {}", userId);
        List<Playlist> playlists = playlistRepository.findByUserIdWithItems(userId);
        log.info("📋 Found {} playlists", playlists.size());
        return playlists;
    }

    @Transactional
    public Playlist create(User user, String title) {
        log.info("📝 Creating playlist for user: {}, title: {}", user.getUsername(), title);
        Playlist playlist = Playlist.builder()
                .title(title)
                .user(user)
                .build();
        return playlistRepository.save(playlist);
    }

    /**
     * ⭐ FIXED: Get playlist by ID with items eagerly loaded
     */
    @Transactional(readOnly = true)
    public Playlist getByIdWithItems(UUID id, User user) {
        log.info("📋 Fetching playlist with items: {} for user: {}", id, user.getUsername());
        return playlistRepository.findByIdWithItems(id, user)
                .orElseThrow(() -> {
                    log.warn("❌ Playlist not found: {}", id);
                    return new ResourceNotFoundException("Playlist not found: " + id);
                });
    }

    /**
     * Get playlist by ID without items (for simple operations)
     */
    @Transactional(readOnly = true)
    public Playlist getById(UUID id) {
        return playlistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Playlist not found: " + id));
    }

    /**
     * Get owned playlist with items eagerly loaded
     */
    @Transactional(readOnly = true)
    private Playlist getOwnedWithItems(UUID id, User user) {
        Playlist playlist = getByIdWithItems(id, user);
        if (!playlist.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("This playlist does not belong to you");
        }
        return playlist;
    }

    /**
     * Get owned playlist without items
     */
    @Transactional(readOnly = true)
    private Playlist getOwned(UUID id, Long userId) {
        Playlist playlist = getById(id);
        if (!playlist.getUser().getId().equals(userId)) {
            throw new ForbiddenException("This playlist does not belong to you");
        }
        return playlist;
    }

    @Transactional
    public Playlist addVideo(UUID playlistId, UUID videoId, User user) {
        log.info("➕ Adding video {} to playlist {} for user {}", videoId, playlistId, user.getUsername());

        // Get the playlist with items loaded
        Playlist playlist = getOwnedWithItems(playlistId, user);

        if (playlistItemRepository.existsByPlaylistIdAndVideoId(playlistId, videoId)) {
            log.warn("⚠️ Video {} already in playlist {}", videoId, playlistId);
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

        // Add to the loaded collection
        playlist.getItems().add(item);

        log.info("✅ Video {} added to playlist {} at position {}", videoId, playlistId, nextPosition);
        return playlist;
    }

    @Transactional
    public Playlist removeVideo(UUID playlistId, UUID videoId, User user) {
        log.info("➖ Removing video {} from playlist {} for user {}", videoId, playlistId, user.getUsername());

        Playlist playlist = getOwnedWithItems(playlistId, user);

        PlaylistItem item = playlistItemRepository.findByPlaylistIdAndVideoId(playlistId, videoId)
                .orElseThrow(() -> {
                    log.warn("❌ Video not found in playlist: {}", videoId);
                    return new ResourceNotFoundException("Video not found in playlist");
                });

        playlist.getItems().remove(item);
        playlistItemRepository.delete(item);
        reindex(playlistId);

        log.info("✅ Video {} removed from playlist {}", videoId, playlistId);
        return playlist;
    }

    @Transactional
    public Playlist moveVideo(UUID playlistId, UUID videoId, int newPosition, User user) {
        log.info("🔀 Moving video {} to position {} in playlist {} for user {}", videoId, newPosition, playlistId, user.getUsername());

        Playlist playlist = getOwnedWithItems(playlistId, user);

        if (newPosition < 0) {
            throw new BadRequestException("newPosition must be >= 0");
        }

        List<PlaylistItem> items = playlistItemRepository.findByPlaylistIdOrderByPositionAsc(playlistId);
        PlaylistItem target = items.stream()
                .filter(i -> i.getVideo().getId().equals(videoId))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("❌ Video not found in playlist: {}", videoId);
                    return new ResourceNotFoundException("Video not found in playlist");
                });

        // Clamp position to valid range
        int clampedPosition = Math.min(newPosition, items.size() - 1);

        // Remove and re-insert at new position
        items.remove(target);
        items.add(clampedPosition, target);

        // Update positions
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setPosition(i);
        }
        playlistItemRepository.saveAll(items);

        // Refresh the playlist's items collection
        playlist.getItems().clear();
        playlist.getItems().addAll(items);

        log.info("✅ Video {} moved to position {} in playlist {}", videoId, clampedPosition, playlistId);
        return playlist;
    }

    @Transactional
    public void delete(UUID playlistId, User user) {
        log.info("🗑️ Deleting playlist {} for user {}", playlistId, user.getUsername());

        Playlist playlist = getOwnedWithItems(playlistId, user);
        playlist.markDeleted();
        playlistRepository.save(playlist);

        log.info("✅ Playlist {} deleted", playlistId);
    }

    private void reindex(UUID playlistId) {
        List<PlaylistItem> items = playlistItemRepository.findByPlaylistIdOrderByPositionAsc(playlistId);
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setPosition(i);
        }
        playlistItemRepository.saveAll(items);
    }
}