package com.stockFlow.stockflow_api.movements;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;

public interface MovementsRepository extends JpaRepository<Movements, Long>, JpaSpecificationExecutor<Movements> {

    Optional<Movements> findFirstByProductIdAndTypeOrderByCreatedAtDesc(Long productId, MovementTypeEnum type);

    List<Movements> findByProductId(Long productId);
}
