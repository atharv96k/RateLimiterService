package com.ratelimiter.repository;

import com.ratelimiter.entity.ClientConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientConfigRepository extends JpaRepository<ClientConfig, String> {
}
