package com.stockFlow.stockflow_api.products.mapper;

import com.stockFlow.stockflow_api.products.Product;
import com.stockFlow.stockflow_api.products.dto.ProductResponse;
import com.stockFlow.stockflow_api.products.dto.ProductResponseAll;

import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponseAll toDtoAll(Product product) {
        if (product == null)
            return null;

        return new ProductResponseAll(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getQuantity(),
                product.getMin_stock(),
                isLowStock(product),
                product.getCreated_at(),
                product.getUpdated_at());
    }

    public ProductResponse toDto(Product product) {
        if (product == null)
            return null;

        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getQuantity(),
                product.getMin_stock(),
                isLowStock(product),
                product.getCreated_at(),
                product.getUpdated_at());
    }

    /** Estoque baixo: quantity < min_stock (única fonte desta regra no backend). */
    private boolean isLowStock(Product product) {
        return product.getQuantity() < product.getMin_stock();
    }
}
