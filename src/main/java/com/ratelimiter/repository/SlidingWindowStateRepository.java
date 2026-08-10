package com.ratelimiter.repository;

import com.ratelimiter.entity.SlidingWindowState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SlidingWindowStateRepository extends JpaRepository<SlidingWindowState, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SlidingWindowState s where s.clientKey = :clientKey")
    Optional<SlidingWindowState> findByClientKeyForUpdate(@Param("clientKey") String clientKey);
}
