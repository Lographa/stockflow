package com.stockFlow.stockflow_api.products;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.stockFlow.stockflow_api.movements.Movements;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "products")
@Getter
@Setter
@ToString
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    @Size(max = 50, message = "Limitado a 50 o campo sku")
    private String sku;

    @Column()
    private String name;

    @Column()
    private String description;

    @NotNull(message = "O preço é obrigatório")
    @DecimalMin(value = "0.00", message = "O preço não pode ser negativo")
    @Digits(integer = 8, fraction = 2, message = "O preço deve ter até 8 dígitos inteiros e 2 decimais")
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    @Column()
    private int quantity;

    @Column()
    private int min_stock;

    // Preenchido pelo MySQL (DEFAULT CURRENT_TIMESTAMP). O Hibernate relê o valor
    // logo após o INSERT, então a entidade (e a resposta da API) já sai com a data.
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime created_at;

    // Preenchido pelo MySQL (DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP).
    // O Hibernate relê o valor após INSERT e após cada UPDATE efetivo.
    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updated_at;

    // Soft delete: null = ativo; preenchido = excluído logicamente (nunca removemos a linha).
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ToString.Exclude
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<Movements> movements = new ArrayList<>();

    public Product() {

    }
}
