package com.stockFlow.stockflow_api.products.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.stockFlow.stockflow_api.products.Product;
import com.stockFlow.stockflow_api.products.dto.ProductResponse;
import com.stockFlow.stockflow_api.products.dto.ProductResponseAll;

class ProductMapperTest {

    private final ProductMapper mapper = new ProductMapper();

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 30, 10, 0, 0);
    private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 10, 30, 11, 0, 0);

    private Product product(String sku, String name, int quantity, int minStock) {
        Product p = new Product();
        p.setId(1L);
        p.setSku(sku);
        p.setName(name);
        p.setDescription("Descricao");
        p.setPrice(new BigDecimal("129.90"));
        p.setQuantity(quantity);
        p.setMin_stock(minStock);
        p.setCreated_at(CREATED);
        p.setUpdated_at(UPDATED);
        return p;
    }

    @Test
    void toDtoAll_mapeiaCamposNaOrdemCorreta_estoqueNormal() {
        // seed MOUSE-001: quantity 25, min_stock 10
        ProductResponseAll dto = mapper.toDtoAll(product("MOUSE-001", "Mouse Gamer", 25, 10));

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.sku()).isEqualTo("MOUSE-001");
        assertThat(dto.name()).isEqualTo("Mouse Gamer");
        assertThat(dto.description()).isEqualTo("Descricao");
        assertThat(dto.price()).isEqualByComparingTo("129.90");
        assertThat(dto.quantity()).isEqualTo(25);
        assertThat(dto.min_stock()).isEqualTo(10);
        assertThat(dto.low_stock()).isFalse();
        assertThat(dto.created_at()).isEqualTo(CREATED);
        assertThat(dto.updated_at()).isEqualTo(UPDATED);
    }

    @Test
    void toDtoAll_estoqueBaixo_quandoQuantidadeMenorQueMinimo() {
        // seed KEYBOARD-001: 8 < 10
        ProductResponseAll dto = mapper.toDtoAll(product("KEYBOARD-001", "Teclado Mecanico", 8, 10));

        assertThat(dto.quantity()).isEqualTo(8);
        assertThat(dto.min_stock()).isEqualTo(10);
        assertThat(dto.low_stock()).isTrue();
    }

    @Test
    void lowStock_naoEBaixoQuandoQuantidadeIgualAoMinimo() {
        assertThat(mapper.toDtoAll(product("A", "A", 10, 10)).low_stock()).isFalse();
        assertThat(mapper.toDto(product("A", "A", 10, 10)).low_stock()).isFalse();
    }

    @Test
    void toDto_temOsMesmosCamposQueToDtoAll() {
        Product p = product("MOUSE-001", "Mouse Gamer", 5, 10);

        ProductResponse dto = mapper.toDto(p);
        ProductResponseAll all = mapper.toDtoAll(p);

        assertThat(dto.id()).isEqualTo(all.id());
        assertThat(dto.sku()).isEqualTo("MOUSE-001");
        assertThat(dto.name()).isEqualTo("Mouse Gamer");
        assertThat(dto.description()).isEqualTo(all.description());
        assertThat(dto.price()).isEqualTo(all.price());
        assertThat(dto.quantity()).isEqualTo(5);
        assertThat(dto.min_stock()).isEqualTo(10);
        assertThat(dto.low_stock()).isTrue();
        assertThat(dto.created_at()).isEqualTo(all.created_at());
        assertThat(dto.updated_at()).isEqualTo(all.updated_at());
    }

    @Test
    void quantidadeZeroComMinimoZero_naoEBaixo() {
        assertThat(mapper.toDto(product("A", "A", 0, 0)).low_stock()).isFalse();
        assertThat(mapper.toDto(product("A", "A", 0, 1)).low_stock()).isTrue();
    }

    @Test
    void nulos_retornamNulo() {
        assertThat(mapper.toDto(null)).isNull();
        assertThat(mapper.toDtoAll(null)).isNull();
    }
}
