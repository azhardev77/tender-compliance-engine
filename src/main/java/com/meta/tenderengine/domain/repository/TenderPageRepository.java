package com.meta.tenderengine.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.meta.tenderengine.domain.entity.TenderPageEntity;

public interface TenderPageRepository extends JpaRepository<TenderPageEntity, UUID> {
}
