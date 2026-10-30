package com.stockFlow.stockflow_api.products;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * Soft delete (T-05): a entidade NÃO tem @SQLRestriction/@Where, pois
 * Movements.product precisa carregar produtos excluídos. O filtro de ativos é
 * explícito (métodos ...DeletedAtIsNull / findActiveByIdForUpdate e
 * ProductSpecifications). As verificações de unicidade de nome/SKU abaixo NÃO
 * filtram deleted_at de propósito: nome e SKU de produto excluído continuam
 * reservados (D-6).
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    List<Product> findAllByDeletedAtIsNull();

    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    /** SELECT ... FOR UPDATE, somente produtos ativos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.deletedAt is null")
    Optional<Product> findActiveByIdForUpdate(@Param("id") Long id);

    boolean existsByNameIgnoreCaseOrSkuIgnoreCase(
            String name,
            String sku);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);
}
