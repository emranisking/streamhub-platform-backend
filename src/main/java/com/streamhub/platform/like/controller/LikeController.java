package com.streamhub.platform.like.controller;

import com.streamhub.platform.common.pagination.PageResponse;
import com.streamhub.platform.common.pagination.PaginationRequest;
import com.streamhub.platform.common.pagination.PaginationService;
import com.streamhub.platform.common.response.ApiResponse;
import com.streamhub.platform.like.dto.UserLikeResponse;
import com.streamhub.platform.like.entity.UserLike;
import com.streamhub.platform.like.service.LikeService;
import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.user.service.UserService;
import com.streamhub.platform.video.dto.LikeToggleResponse;
import com.streamhub.platform.video.entity.Video;
import com.streamhub.platform.video.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/likes")
@RequiredArgsConstructor
@Tag(name = "Likes")
public class LikeController {

    private final LikeService likeService;
    private final VideoService videoService;
    private final UserService userService;
    private final PaginationService paginationService;

    @GetMapping
    @Operation(summary = "Get all videos the current user has liked")
    public ResponseEntity<ApiResponse<PageResponse<UserLikeResponse>>> myLikes(PaginationRequest pagination) {
        User user = userService.getCurrentUser();
        Page<UserLike> page = likeService.getUserLikes(user.getId(),
                paginationService.resolve(pagination, Sort.by(Sort.Direction.DESC, "likedAt")));
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.from(page, UserLikeResponse::from, paginationService.getCursorService())));
    }

    @PostMapping("/{videoId}")
    @Operation(summary = "Toggle like/unlike for a video")
    public ResponseEntity<ApiResponse<LikeToggleResponse>> toggle(@PathVariable UUID videoId) {
        User user = userService.getCurrentUser();
        Video video = videoService.getEntityById(videoId);
        boolean liked = likeService.toggleLike(user, video);
        Video refreshed = videoService.getEntityById(videoId);
        return ResponseEntity.ok(ApiResponse.ok(
                LikeToggleResponse.builder().liked(liked).likes(refreshed.getLikes()).build()));
    }
}
