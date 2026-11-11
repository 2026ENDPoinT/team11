package com.team11.backend.events.repository;

import com.team11.backend.events.domain.Event;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    /** rangeEnd는 배타적 상한(to일 다음날 00:00)으로 전달한다. */
    @Query("""
            select e from Event e
            where e.userId = :userId
              and e.startAt < :rangeEnd
              and coalesce(e.endAt, e.startAt) >= :rangeStart
            order by e.startAt asc
            """)
    List<Event> findOverlapping(@Param("userId") Long userId, @Param("rangeStart") OffsetDateTime rangeStart,
                                 @Param("rangeEnd") OffsetDateTime rangeEnd);
}
