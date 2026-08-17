package com.streamhub.platform.playlist.repository;

import com.streamhub.platform.playlist.entity.PlaylistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaylistItemRepository extends JpaRepository<PlaylistItem, UUID> {
    List<PlaylistItem> findByPlaylistIdOrderByPositionAsc(UUID playlistId);
    Optional<PlaylistItem> findByPlaylistIdAndVideoId(UUID playlistId, UUID videoId);
    boolean existsByPlaylistIdAndVideoId(UUID playlistId, UUID videoId);
    long countByPlaylistId(UUID playlistId);
}
