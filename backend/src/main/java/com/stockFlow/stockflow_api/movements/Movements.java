package com.stockFlow.stockflow_api.movements;

import java.time.LocalDateTime;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;
import com.stockFlow.stockflow_api.products.Product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_movement_product_date", columnList = "product_id, created_at")
})
@Getter
@Setter
@ToString
public class Movements {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Exclude
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

    // P-09: a coluna é preenchida pelo MySQL (DEFAULT CURRENT_TIMESTAMP) e não é inserida
    // pelo Hibernate (insertable=false). Antes, @CreationTimestamp gerava um valor em memória
    // (relógio/fuso da JVM) que nunca era gravado, podendo divergir do valor real do banco.
    // @Generated(INSERT) faz o Hibernate reler o valor gravado pelo MySQL após o INSERT.
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Movements() {
    }

}
