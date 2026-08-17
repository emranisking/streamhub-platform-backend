package com.streamhub.platform.video.service;

import com.streamhub.platform.video.entity.Video;
import com.streamhub.platform.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scans the configured incoming-media directory for new MP4 files on
 * startup, converts each one to an HLS stream (m3u8 + segments) plus a
 * thumbnail using FFmpeg via {@link ProcessBuilder}, and persists a
 * {@link Video} row pointing at the generated files.
 * <p>
 * This replaces the original NestJS {@code OnModuleInit} scan-and-convert
 * flow. Runs asynchronously after the application context is fully up so it
 * never blocks startup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoScannerService {

    private final VideoRepository videoRepository;

    @Value("${app.media.source-directory}")
    private String sourceDirectory;

    @Value("${app.media.hls-output-directory}")
    private String hlsOutputDirectory;

    @Value("${app.media.public-route-prefix}")
    private String publicRoutePrefix;

    @Value("${app.media.ffmpeg-path}")
    private String ffmpegPath;

    @Value("${app.media.scan-on-startup}")
    private boolean scanOnStartup;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onStartup() {
        if (!scanOnStartup) {
            return;
        }
        scanAndConvertAll();
    }

    public void scanAndConvertAll() {
        Path source = Path.of(sourceDirectory);
        if (!Files.isDirectory(source)) {
            log.info("Media source directory {} does not exist yet - skipping scan.", source);
            return;
        }

        try (Stream<Path> files = Files.list(source)) {
            List<Path> mp4Files = files
                    .filter(p -> p.toString().toLowerCase().endsWith(".mp4"))
                    .toList();

            for (Path file : mp4Files) {
                String filename = file.getFileName().toString();
                if (videoRepository.existsBySourceFilename(filename)) {
                    continue;
                }
                try {
                    convertAndSave(file, filename);
                } catch (Exception e) {
                    log.error("Failed to convert video file {}: {}", filename, e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan media source directory {}: {}", source, e.getMessage(), e);
        }
    }

    private void convertAndSave(Path sourceFile, String filename) throws IOException, InterruptedException {
        String baseName = filename.substring(0, filename.lastIndexOf('.'));
        Path outputDir = Path.of(hlsOutputDirectory, baseName);
        Files.createDirectories(outputDir);

        Path manifestPath = outputDir.resolve(baseName + ".m3u8");
        Path thumbnailPath = outputDir.resolve(baseName + "_thumb.jpg");

        // Convert to HLS
        runFfmpeg(List.of(
                ffmpegPath, "-y", "-i", sourceFile.toString(),
                "-codec:", "copy", "-start_number", "0",
                "-hls_time", "6", "-hls_list_size", "0", "-f", "hls",
                manifestPath.toString()
        ));

        // Generate a thumbnail from the 1-second mark
        runFfmpeg(List.of(
                ffmpegPath, "-y", "-i", sourceFile.toString(),
                "-ss", "00:00:01.000", "-vframes", "1",
                thumbnailPath.toString()
        ));

        String videoUrl = publicRoutePrefix + "/" + baseName + "/" + baseName + ".m3u8";
        String thumbnailUrl = publicRoutePrefix + "/" + baseName + "/" + baseName + "_thumb.jpg";

        Video video = Video.builder()
                .title(baseName.replace('_', ' ').replace('-', ' '))
                .videoUrl(videoUrl)
                .thumbnailUrl(thumbnailUrl)
                .sourceFilename(filename)
                .build();
        videoRepository.save(video);
        log.info("Converted and registered video: {}", filename);
    }

    private void runFfmpeg(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        // Drain output to avoid the process blocking on a full pipe buffer.
        try (var reader = process.inputReader()) {
            reader.lines().forEach(line -> log.debug("[ffmpeg] {}", line));
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("ffmpeg exited with code " + exitCode);
        }
    }
}
