package com.streamhub.platform.analytics.repository;

import com.streamhub.platform.analytics.entity.VisitLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VisitLogRepository extends JpaRepository<VisitLog, java.util.UUID> {

    @Query("select count(v) from VisitLog v where v.visitedAt between :start and :end")
    long countVisits(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select count(distinct coalesce(cast(v.user.id as string), coalesce(v.sessionId, v.ipAddress))) " +
           "from VisitLog v where v.visitedAt between :start and :end")
    long countUniqueVisitors(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select count(v) from VisitLog v where v.visitedAt between :start and :end and v.user is not null")
    long countRegisteredUserVisits(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select count(v) from VisitLog v where v.visitedAt between :start and :end and v.newRegistration = true")
    long countNewRegistrations(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "select date_trunc('day', visited_at) as bucket, " +
                   "count(*) as total_visits, " +
                   "count(distinct coalesce(cast(user_id as text), coalesce(session_id, ip_address))) as unique_visitors, " +
                   "count(*) filter (where user_id is not null) as registered_visits, " +
                   "count(*) filter (where new_registration = true) as new_registrations " +
                   "from visit_logs " +
                   "where visited_at between :start and :end and deleted = false " +
                   "group by bucket order by bucket asc", nativeQuery = true)
    List<Object[]> dailyBuckets(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "select date_trunc('month', visited_at) as bucket, " +
                   "count(*) as total_visits, " +
                   "count(distinct coalesce(cast(user_id as text), coalesce(session_id, ip_address))) as unique_visitors, " +
                   "count(*) filter (where user_id is not null) as registered_visits, " +
                   "count(*) filter (where new_registration = true) as new_registrations " +
                   "from visit_logs " +
                   "where visited_at between :start and :end and deleted = false " +
                   "group by bucket order by bucket asc", nativeQuery = true)
    List<Object[]> monthlyBuckets(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
