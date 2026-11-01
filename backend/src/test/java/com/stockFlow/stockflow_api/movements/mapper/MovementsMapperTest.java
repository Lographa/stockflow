package com.stockFlow.stockflow_api.movements.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.stockFlow.stockflow_api.movements.Movements;
import com.stockFlow.stockflow_api.movements.dto.MovementResponse;
import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;
import com.stockFlow.stockflow_api.products.Product;

class MovementsMapperTest {

    private final MovementsMapper mapper = new MovementsMapper();

    private static Movements movement(Product product) {
        Movements m = new Movements();
        m.setId(10L);
        m.setProduct(product);
        m.setType(MovementTypeEnum.EXIT);
        m.setQuantity(5);
        m.setDescription("Separacao de pedidos");
        m.setCreatedAt(LocalDateTime.of(2026, 10, 30, 10, 5));
        return m;
    }

    private static Product product() {
        Product p = new Product();
        p.setId(1L);
        p.setSku("MOUSE-001");
        p.setName("Mouse Gamer");
        return p;
    }

    @Test
    void mapeiaTodosOsCampos_comStockAfter() {
        MovementResponse r = mapper.toResponse(movement(product()), 20);

        assertThat(r.id()).isEqualTo(10L);
        assertThat(r.product_id()).isEqualTo(1L);
        assertThat(r.product_sku()).isEqualTo("MOUSE-001");
        assertThat(r.product_name()).isEqualTo("Mouse Gamer");
        assertThat(r.product_deleted()).isFalse();
        assertThat(r.type()).isEqualTo(MovementTypeEnum.EXIT);
        assertThat(r.quantity()).isEqualTo(5);
        assertThat(r.description()).isEqualTo("Separacao de pedidos");
        assertThat(r.stock_after()).isEqualTo(20);
        assertThat(r.created_at()).isEqualTo(LocalDateTime.of(2026, 10, 30, 10, 5));
    }

    @Test
    void semStockAfter_campoVemNulo() {
        assertThat(mapper.toResponse(movement(product())).stock_after()).isNull();
    }

    @Test
    void produtoExcluido_marcaProductDeleted() {
        Product p = product();
        p.setDeletedAt(LocalDateTime.of(2026, 10, 29, 9, 0));

        assertThat(mapper.toResponse(movement(p)).product_deleted()).isTrue();
    }

    @Test
    void movimentacaoNula_retornaNulo() {
        assertThat(mapper.toResponse(null)).isNull();
        assertThat(mapper.toResponse(null, 1)).isNull();
    }
}
