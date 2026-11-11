package com.team11.backend.notifications.repository;

import com.team11.backend.notifications.domain.Notification;
import com.team11.backend.notifications.domain.NotificationType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            select n from Notification n
            where n.userId = :userId
              and (:cursor is null or n.id < :cursor)
              and (:unreadOnly = false or n.readAt is null)
            order by n.id desc
            """)
    List<Notification> findPage(@Param("userId") Long userId, @Param("cursor") Long cursor,
                                 @Param("unreadOnly") boolean unreadOnly, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    void markAllRead(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    boolean existsByUserIdAndTypeAndCreatedAtAfter(Long userId, NotificationType type, Instant after);
}
