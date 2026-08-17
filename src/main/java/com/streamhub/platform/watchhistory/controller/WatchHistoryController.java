package com.streamhub.platform.watchhistory.controller;

import com.streamhub.platform.common.pagination.PageResponse;
import com.streamhub.platform.common.pagination.PaginationRequest;
import com.streamhub.platform.common.pagination.PaginationService;
import com.streamhub.platform.common.response.ApiResponse;
import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.user.service.UserService;
import com.streamhub.platform.video.entity.Video;
import com.streamhub.platform.video.service.VideoService;
import com.streamhub.platform.watchhistory.dto.WatchHistoryResponse;
import com.streamhub.platform.watchhistory.entity.WatchHistory;
import com.streamhub.platform.watchhistory.service.WatchHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
@Tag(name = "Watch History")
public class WatchHistoryController {

    private final WatchHistoryService watchHistoryService;
    private final VideoService videoService;
    private final UserService userService;
    private final PaginationService paginationService;

    @PostMapping("/{videoId}")
    @Operation(summary = "Add (or refresh) a video in the current user's watch history")
    public ResponseEntity<ApiResponse<WatchHistoryResponse>> add(@PathVariable UUID videoId) {
        User user = userService.getCurrentUser();
        Video video = videoService.getEntityById(videoId);
        WatchHistory history = watchHistoryService.upsert(user, video);
        return ResponseEntity.ok(ApiResponse.ok(WatchHistoryResponse.from(history)));
    }

    @GetMapping
    @Operation(summary = "Get the current user's watch history, most recent first")
    public ResponseEntity<ApiResponse<PageResponse<WatchHistoryResponse>>> myHistory(PaginationRequest pagination) {
        User user = userService.getCurrentUser();
        Page<WatchHistory> page = watchHistoryService.getUserHistory(user.getId(),
                paginationService.resolve(pagination, Sort.by(Sort.Direction.DESC, "watchedAt")));
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.from(page, WatchHistoryResponse::from, paginationService.getCursorService())));
    }
}
