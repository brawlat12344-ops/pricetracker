package com.example.pricetracker.repo;

import com.example.pricetracker.domain.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    @Query(value = """
            SELECT a.* FROM alerts a
            JOIN products p ON p.id = a.product_id
            WHERE p.user_id = :userId AND NOT a.is_read
            ORDER BY a.created_at DESC
            """, countQuery = """
            SELECT count(*) FROM alerts a
            JOIN products p ON p.id = a.product_id
            WHERE p.user_id = :userId AND NOT a.is_read
            """, nativeQuery = true)
    Page<Alert> findUnreadByUser(@Param("userId") UUID userId, Pageable pageable);
}
