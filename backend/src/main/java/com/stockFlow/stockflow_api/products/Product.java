package com.stockFlow.stockflow_api.products;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import lombok.Data;

@Entity
@Table(name = "products")
@Data
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    @Size(max = 50, message = "Limitado a 50 o campo sku")
    private String sku;

    @Column()
    private String name;

    private String description;

    @NotNull(message = "O preço é obrigatório")
    @DecimalMin(value = "0.00", message = "O preço não pode ser negativo")
    @Digits(integer = 8, fraction = 2, message = "O preço deve ter até 8 dígitos inteiros e 2 decimais")
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    private int quantity;

    private int min_stock;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updated_at;

    public Product() {

    }
}
