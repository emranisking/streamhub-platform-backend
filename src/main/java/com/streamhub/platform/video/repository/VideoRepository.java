package com.streamhub.platform.video.repository;

import com.streamhub.platform.video.entity.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VideoRepository extends JpaRepository<Video, UUID> {

    @EntityGraph(attributePaths = {"category"})
    Page<Video> findByCategoryId(UUID categoryId, Pageable pageable);

    @EntityGraph(attributePaths = {"category"})
    Page<Video> findAll(Pageable pageable);

    Optional<Video> findBySourceFilename(String sourceFilename);

    boolean existsBySourceFilename(String sourceFilename);

    @Modifying
    @Query("update Video v set v.views = v.views + 1 where v.id = :id")
    int incrementViews(@Param("id") UUID id);

    @Modifying
    @Query("update Video v set v.likes = v.likes + 1 where v.id = :id")
    int incrementLikes(@Param("id") UUID id);

    @Modifying
    @Query("""
        update Video v
        set v.likes = case when v.likes > 0 then v.likes - 1 else 0 end
        where v.id = :id
        """)
    int decrementLikes(@Param("id") UUID id);
}