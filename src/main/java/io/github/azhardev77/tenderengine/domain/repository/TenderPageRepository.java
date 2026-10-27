package io.github.azhardev77.tenderengine.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.azhardev77.tenderengine.domain.entity.TenderPageEntity;

public interface TenderPageRepository extends JpaRepository<TenderPageEntity, UUID> {
}
