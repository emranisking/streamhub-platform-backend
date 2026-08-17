package com.streamhub.platform.playlist.repository;

import com.streamhub.platform.playlist.entity.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {
    List<Playlist> findByUserIdOrderByCreatedAtDesc(Long userId);
}
