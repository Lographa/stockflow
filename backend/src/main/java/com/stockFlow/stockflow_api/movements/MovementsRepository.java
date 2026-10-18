package com.stockFlow.stockflow_api.movements;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;

public interface MovementsRepository extends JpaRepository<Movements, Long> {

    Optional<Movements> findFirstByProductIdAndTypeOrderByCreatedAtDesc(Long productId, MovementTypeEnum type);
}
