package com.streamhub.platform.playlist.controller;

import com.streamhub.platform.common.response.ApiResponse;
import com.streamhub.platform.playlist.dto.CreatePlaylistRequest;
import com.streamhub.platform.playlist.dto.PlaylistResponse;
import com.streamhub.platform.playlist.entity.Playlist;
import com.streamhub.platform.playlist.service.PlaylistService;
import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Storyline: this is Bob's module - he curates "My Favorites" while
 * browsing as a free user. See /docs/API_STORYLINE.md section 3.
 * All endpoints require authentication (enforced by SecurityConfig for
 * anything not explicitly public).
 */
@RestController
@RequestMapping("/api/v1/playlists")
@RequiredArgsConstructor
@Tag(name = "Playlists")
public class PlaylistController {

    private final PlaylistService playlistService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "List all playlists for the current user")
    public ResponseEntity<ApiResponse<List<PlaylistResponse>>> list() {
        User user = userService.getCurrentUser();
        List<PlaylistResponse> playlists = playlistService.getAllForUser(user.getId()).stream()
                .map(PlaylistResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.ok(playlists));
    }

    @PostMapping
    @Operation(summary = "Create a new playlist")
    public ResponseEntity<ApiResponse<PlaylistResponse>> create(@Valid @RequestBody CreatePlaylistRequest request) {
        User user = userService.getCurrentUser();
        Playlist playlist = playlistService.create(user, request.getTitle());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(PlaylistResponse.from(playlist)));
    }

    @PostMapping("/{playlistId}/add/{videoId}")
    @Operation(summary = "Add a video to a playlist")
    public ResponseEntity<ApiResponse<PlaylistResponse>> addVideo(@PathVariable UUID playlistId, @PathVariable UUID videoId) {
        User user = userService.getCurrentUser();
        Playlist playlist = playlistService.addVideo(playlistId, videoId, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(PlaylistResponse.from(playlist)));
    }

    @DeleteMapping("/{playlistId}/remove/{videoId}")
    @Operation(summary = "Remove a video from a playlist")
    public ResponseEntity<ApiResponse<PlaylistResponse>> removeVideo(@PathVariable UUID playlistId, @PathVariable UUID videoId) {
        User user = userService.getCurrentUser();
        Playlist playlist = playlistService.removeVideo(playlistId, videoId, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(PlaylistResponse.from(playlist)));
    }

    @PatchMapping("/{playlistId}/move/{videoId}/{newPosition}")
    @Operation(summary = "Move a video to a new position within a playlist")
    public ResponseEntity<ApiResponse<PlaylistResponse>> moveVideo(
            @PathVariable UUID playlistId, @PathVariable UUID videoId, @PathVariable int newPosition) {
        User user = userService.getCurrentUser();
        Playlist playlist = playlistService.moveVideo(playlistId, videoId, newPosition, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(PlaylistResponse.from(playlist)));
    }

    @DeleteMapping("/{playlistId}")
    @Operation(summary = "Delete an entire playlist")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID playlistId) {
        User user = userService.getCurrentUser();
        playlistService.delete(playlistId, user.getId());
        return ResponseEntity.ok(ApiResponse.message("Playlist deleted"));
    }
}
