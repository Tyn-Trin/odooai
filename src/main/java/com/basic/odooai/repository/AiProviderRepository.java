package com.basic.odooai.repository;

import com.basic.odooai.model.AiProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {
    Optional<AiProvider> findByActive(boolean active);
}
