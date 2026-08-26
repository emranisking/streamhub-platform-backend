package com.streamhub.platform.playlist.repository;

import com.streamhub.platform.playlist.entity.Playlist;
import com.streamhub.platform.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

    /**
     * ⭐ NEW: Find all playlists with items eagerly loaded for a User
     */
    @Query("SELECT DISTINCT p FROM Playlist p " +
            "LEFT JOIN FETCH p.items i " +
            "LEFT JOIN FETCH i.video v " +
            "LEFT JOIN FETCH v.category " +
            "WHERE p.user = :user " +
            "ORDER BY p.createdAt DESC")
    List<Playlist> findByUserWithItems(@Param("user") User user);

    /**
     * ⭐ NEW: Find all playlists with items eagerly loaded by user ID
     */
    @Query("SELECT DISTINCT p FROM Playlist p " +
            "LEFT JOIN FETCH p.items i " +
            "LEFT JOIN FETCH i.video v " +
            "LEFT JOIN FETCH v.category " +
            "WHERE p.user.id = :userId " +
            "ORDER BY p.createdAt DESC")
    List<Playlist> findByUserIdWithItems(@Param("userId") Long userId);

    /**
     * ⭐ NEW: Find playlist by ID with items eagerly loaded for a User
     */
    @Query("SELECT p FROM Playlist p " +
            "LEFT JOIN FETCH p.items i " +
            "LEFT JOIN FETCH i.video v " +
            "LEFT JOIN FETCH v.category " +
            "WHERE p.id = :id AND p.user = :user")
    Optional<Playlist> findByIdWithItems(@Param("id") UUID id, @Param("user") User user);

    // Legacy methods (kept for backward compatibility)
    List<Playlist> findByUserIdOrderByCreatedAtDesc(Long userId);
}