package com.streamhub.platform.playlist.controller;

import com.streamhub.platform.common.response.ApiResponse;
import com.streamhub.platform.playlist.dto.PlaylistResponse;
import com.streamhub.platform.playlist.entity.Playlist;
import com.streamhub.platform.playlist.service.PlaylistService;
import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/playlists")
@RequiredArgsConstructor
@Tag(name = "Playlists")
public class PlaylistController {

    private final PlaylistService playlistService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all playlists for current user")
    public ResponseEntity<ApiResponse<List<PlaylistResponse>>> list() {
        log.info("📋 GET /api/v1/playlists");
        User user = userService.getCurrentUser();
        List<Playlist> playlists = playlistService.getAllForUser(user);

        List<PlaylistResponse> responses = playlists.stream()
                .map(PlaylistResponse::from)
                .collect(Collectors.toList());

        log.info("📋 Returning {} playlists", responses.size());
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a new playlist")
    public ResponseEntity<ApiResponse<PlaylistResponse>> create(@RequestBody CreatePlaylistRequest request) {
        log.info("📝 POST /api/v1/playlists - title: {}", request.getTitle());
        User user = userService.getCurrentUser();
        Playlist playlist = playlistService.create(user, request.getTitle());
        return ResponseEntity.ok(ApiResponse.ok(PlaylistResponse.from(playlist)));
    }

    @PostMapping("/{playlistId}/add/{videoId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add video to playlist")
    public ResponseEntity<ApiResponse<PlaylistResponse>> addVideo(
            @PathVariable UUID playlistId,
            @PathVariable UUID videoId) {
        log.info("➕ POST /api/v1/playlists/{}/add/{}", playlistId, videoId);
        User user = userService.getCurrentUser();
        Playlist playlist = playlistService.addVideo(playlistId, videoId, user);
        return ResponseEntity.ok(ApiResponse.ok(PlaylistResponse.from(playlist)));
    }

    @DeleteMapping("/{playlistId}/remove/{videoId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remove video from playlist")
    public ResponseEntity<ApiResponse<PlaylistResponse>> removeVideo(
            @PathVariable UUID playlistId,
            @PathVariable UUID videoId) {
        log.info("➖ DELETE /api/v1/playlists/{}/remove/{}", playlistId, videoId);
        User user = userService.getCurrentUser();
        Playlist playlist = playlistService.removeVideo(playlistId, videoId, user);
        return ResponseEntity.ok(ApiResponse.ok(PlaylistResponse.from(playlist)));
    }

    @PatchMapping("/{playlistId}/move/{videoId}/{newPosition}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Move video to new position in playlist")
    public ResponseEntity<ApiResponse<PlaylistResponse>> moveItem(
            @PathVariable UUID playlistId,
            @PathVariable UUID videoId,
            @PathVariable int newPosition) {
        log.info("🔀 PATCH /api/v1/playlists/{}/move/{}/{}", playlistId, videoId, newPosition);
        User user = userService.getCurrentUser();
        Playlist playlist = playlistService.moveVideo(playlistId, videoId, newPosition, user);
        return ResponseEntity.ok(ApiResponse.ok(PlaylistResponse.from(playlist)));
    }

    @DeleteMapping("/{playlistId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete a playlist")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID playlistId) {
        log.info("🗑️ DELETE /api/v1/playlists/{}", playlistId);
        User user = userService.getCurrentUser();
        playlistService.delete(playlistId, user);
        return ResponseEntity.ok(ApiResponse.message("Playlist deleted"));
    }
}

// ⭐ DTO for create request
class CreatePlaylistRequest {
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}