package com.streamhub.platform.video.controller;

import com.streamhub.platform.common.pagination.PageResponse;
import com.streamhub.platform.common.pagination.PaginationRequest;
import com.streamhub.platform.common.pagination.PaginationService;
import com.streamhub.platform.common.response.ApiResponse;
import com.streamhub.platform.playback.service.GuestTrackingService;
import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.user.service.UserService;
import com.streamhub.platform.video.dto.CheckLimitResponse;
import com.streamhub.platform.video.dto.VideoDetailResponse;
import com.streamhub.platform.video.dto.VideoSummaryResponse;
import com.streamhub.platform.video.entity.Video;
import com.streamhub.platform.video.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
@Tag(name = "Videos")
public class VideoController {

    private final VideoService videoService;
    private final GuestTrackingService guestTrackingService;
    private final UserService userService;
    private final PaginationService paginationService;
    private final com.streamhub.platform.category.service.CategoryService categoryService;

    @Value("${app.media.thumbnail-directory:/home/emran/project/thumbnails}")
    private String thumbnailDirectory;

    @GetMapping
    @Operation(summary = "Paginated list of videos (public)", description = "Optional categoryId query param filters by category.")
    public ResponseEntity<ApiResponse<PageResponse<VideoSummaryResponse>>> list(
            @RequestParam(required = false) UUID categoryId,
            PaginationRequest pagination) {
        Page<Video> page = videoService.getVideos(Optional.ofNullable(categoryId), paginationService.resolve(pagination));
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.from(page, VideoSummaryResponse::from, paginationService.getCursorService())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full video details (public, Redis-cached)")
    public ResponseEntity<ApiResponse<VideoDetailResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(videoService.getDetailCached(id)));
    }

    @GetMapping("/{id}/thumbnail")
    @Operation(summary = "Get video thumbnail image")
    public ResponseEntity<Resource> getThumbnail(@PathVariable UUID id) {
        log.info("=========================================");
        log.info("🚀 Thumbnail request for video: {}", id);

        try {
            Video video = videoService.getEntityById(id);
            String thumbnailPath = video.getThumbnailUrl();

            log.info("📸 thumbnailUrl from DB: '{}'", thumbnailPath);

            if (thumbnailPath == null || thumbnailPath.isBlank()) {
                log.warn("❌ No thumbnail URL for video: {}", id);
                return ResponseEntity.notFound().build();
            }

            File thumbnailFile = null;

            // TRY 1: Check if the path is a full absolute path and file exists
            File fullPathFile = new File(thumbnailPath);
            log.info("🔍 Checking full path: {}", fullPathFile.getAbsolutePath());
            if (fullPathFile.exists() && fullPathFile.isFile() && fullPathFile.canRead()) {
                thumbnailFile = fullPathFile;
                log.info("✅ Found thumbnail using full path: {}", fullPathFile.getAbsolutePath());
            }

            // TRY 2: If not, extract filename and look in configured directory
            if (thumbnailFile == null) {
                String filename = thumbnailPath;
                if (filename.contains("/")) {
                    filename = filename.substring(filename.lastIndexOf("/") + 1);
                }
                if (filename.contains("\\")) {
                    filename = filename.substring(filename.lastIndexOf("\\") + 1);
                }

                log.info("📝 Extracted filename: '{}'", filename);
                log.info("📁 thumbnailDirectory: {}", thumbnailDirectory);

                Path configuredPath = Paths.get(thumbnailDirectory).resolve(filename).normalize();
                File configuredFile = configuredPath.toFile();

                log.info("🔍 Checking configured path: {}", configuredFile.getAbsolutePath());
                if (configuredFile.exists() && configuredFile.isFile() && configuredFile.canRead()) {
                    thumbnailFile = configuredFile;
                    log.info("✅ Found thumbnail in configured directory: {}", configuredFile.getAbsolutePath());
                }
            }

            // TRY 3: Try alternative locations
            if (thumbnailFile == null) {
                String filename = thumbnailPath;
                if (filename.contains("/")) {
                    filename = filename.substring(filename.lastIndexOf("/") + 1);
                }

                String[] alternativeLocations = {
                        "/home/emran/project/thumbnails/" + filename,
                        "/home/emran/project/" + filename,
                        System.getProperty("user.home") + "/project/thumbnails/" + filename,
                        System.getProperty("user.home") + "/thumbnails/" + filename
                };

                for (String altPath : alternativeLocations) {
                    File altFile = new File(altPath);
                    if (altFile.exists() && altFile.isFile() && altFile.canRead()) {
                        thumbnailFile = altFile;
                        log.info("✅ Found thumbnail at alternative location: {}", altPath);
                        break;
                    }
                }
            }

            if (thumbnailFile == null) {
                log.warn("❌ Thumbnail file not found for video {}", id);
                return ResponseEntity.notFound().build();
            }

            // Get content type from filename
            String filename = thumbnailFile.getName();
            String contentType = getContentType(filename);

            Resource resource = new FileSystemResource(thumbnailFile);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(thumbnailFile.length()))
                    .body(resource);

        } catch (Exception e) {
            log.error("❌ Error serving thumbnail for video {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ⭐⭐⭐ NEW ENDPOINT: Get thumbnail by filename directly ⭐⭐⭐
    // This handles requests like: GET /api/v1/videos/thumbnail/final_race_f1_the_movie_2025_thumb.jpg
    @GetMapping("/thumbnail/{filename:.+}")
    @Operation(summary = "Get video thumbnail by filename directly")
    public ResponseEntity<Resource> getThumbnailByFilename(@PathVariable String filename) {
        log.info("📸 Serving thumbnail by filename: {}", filename);

        try {
            // Clean the filename (remove any path traversal attempts)
            String cleanFilename = filename.replaceAll("[/\\\\]", "");

            // Try to find the thumbnail file
            File thumbnailFile = findThumbnailFile(cleanFilename);

            if (thumbnailFile == null || !thumbnailFile.exists() || !thumbnailFile.canRead()) {
                log.warn("❌ Thumbnail not found: {}", cleanFilename);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(thumbnailFile);
            String contentType = getContentType(cleanFilename);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(thumbnailFile.length()))
                    .body(resource);

        } catch (Exception e) {
            log.error("❌ Error serving thumbnail: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PatchMapping("/{id}/views")
    @Operation(summary = "Increment a video's view count",
            description = "Public - guests increment the raw counter; if a valid Authorization header is present, a watch-history entry is also recorded.")
    public ResponseEntity<ApiResponse<VideoDetailResponse>> incrementViews(@PathVariable UUID id) {
        User user = userService.getCurrentUserOptional().orElse(null);
        videoService.incrementViews(id, user);
        return ResponseEntity.ok(ApiResponse.ok(videoService.getDetailCached(id)));
    }

    @GetMapping("/{id}/check-limit")
    @Operation(summary = "Check remaining free plays without incrementing the counter")
    public ResponseEntity<ApiResponse<CheckLimitResponse>> checkLimit(
            @PathVariable UUID id,
            @RequestParam(required = false) String sessionId,
            @RequestHeader(value = "x-forwarded-for", required = false) String forwardedFor) {

        Optional<User> user = userService.getCurrentUserOptional();
        long count;
        boolean unlimited = false;

        if (user.isPresent() && user.get().hasActiveSubscription()) {
            unlimited = true;
            count = 0;
        } else if (user.isPresent()) {
            count = guestTrackingService.countForUser(user.get().getId());
        } else if (sessionId != null && !sessionId.isBlank()) {
            count = guestTrackingService.countForSession(sessionId);
        } else {
            count = guestTrackingService.countForIp(forwardedFor);
        }

        CheckLimitResponse response = CheckLimitResponse.builder()
                .unlimited(unlimited)
                .locked(!unlimited && guestTrackingService.isLocked(count))
                .remaining(unlimited ? -1 : guestTrackingService.freeRemaining(count))
                .build();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/increment-watch")
    @Operation(summary = "Increment the guest/user watch counter for a video without fetching the manifest",
            description = "Helper endpoint for frontend-side tracking, kept separate from /playback for UI use cases.")
    public ResponseEntity<ApiResponse<CheckLimitResponse>> incrementWatch(
            @PathVariable UUID id,
            @RequestParam(required = false) String sessionId,
            @RequestHeader(value = "x-forwarded-for", required = false) String forwardedFor) {

        Optional<User> user = userService.getCurrentUserOptional();
        long count;
        boolean unlimited = false;

        if (user.isPresent() && user.get().hasActiveSubscription()) {
            unlimited = true;
            count = 0;
        } else if (user.isPresent()) {
            count = guestTrackingService.incrementForUser(user.get().getId());
        } else if (sessionId != null && !sessionId.isBlank()) {
            count = guestTrackingService.incrementForSession(sessionId);
        } else {
            count = guestTrackingService.incrementForIp(forwardedFor);
        }

        CheckLimitResponse response = CheckLimitResponse.builder()
                .unlimited(unlimited)
                .locked(!unlimited && guestTrackingService.isLocked(count))
                .remaining(unlimited ? -1 : guestTrackingService.freeRemaining(count))
                .build();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{id}/category")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign a category to a video (ADMIN only)")
    public ResponseEntity<ApiResponse<VideoDetailResponse>> assignCategory(
            @PathVariable UUID id, @RequestParam UUID categoryId) {
        var category = categoryService.findById(categoryId);
        videoService.assignCategory(id, category);
        return ResponseEntity.ok(ApiResponse.ok(videoService.getDetailCached(id)));
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a video (ADMIN only, soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        videoService.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Video deleted"));
    }

    // ========== Helper Methods ==========

    /**
     * Find thumbnail file in multiple locations
     */
    private File findThumbnailFile(String filename) {
        // Try multiple possible locations based on your configuration
        String[] possiblePaths = {
                thumbnailDirectory + "/" + filename,
                "/home/emran/project/thumbnails/" + filename,
                "/home/emran/project/" + filename,
                "./thumbnails/" + filename,
                "./media/thumbnails/" + filename,
                "./uploads/thumbnails/" + filename,
                "./uploads/" + filename,
                "/tmp/thumbnails/" + filename,
                System.getProperty("user.home") + "/media/thumbnails/" + filename,
                System.getProperty("user.home") + "/project/thumbnails/" + filename,
                System.getProperty("user.home") + "/thumbnails/" + filename
        };

        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists() && file.canRead()) {
                log.debug("Found thumbnail at: {}", file.getAbsolutePath());
                return file;
            }
        }

        return null;
    }

    private String getContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG_VALUE;
        } else if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        } else if (lower.endsWith(".webp")) {
            return "image/webp";
        } else if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF_VALUE;
        } else if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}