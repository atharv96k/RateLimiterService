package com.ratelimiter.repository;

import com.ratelimiter.entity.TokenBucketState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TokenBucketStateRepository extends JpaRepository<TokenBucketState, String> {

    /**
     * PESSIMISTIC_WRITE = SQL "SELECT ... FOR UPDATE".
     * This locks the row for the duration of the enclosing @Transactional method,
     * so if two requests for the SAME clientKey arrive at the same instant,
     * the second one physically WAITS at the database level until the first
     * transaction commits (or rolls back). That's what prevents the classic
     * "both read tokens=1, both decrement, both allow" double-spend bug.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TokenBucketState t where t.clientKey = :clientKey")
    Optional<TokenBucketState> findByClientKeyForUpdate(@Param("clientKey") String clientKey);
}
