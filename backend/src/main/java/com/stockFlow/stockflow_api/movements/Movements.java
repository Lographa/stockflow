package com.stockFlow.stockflow_api.movements;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;
import com.stockFlow.stockflow_api.products.Product;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_movement_product_date", columnList = "product_id, created_at")
})
@Data
public class Movements {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private MovementTypeEnum type;

    @Column()
    private Integer quantity;

    @Column()
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Movements() {
    }

}
