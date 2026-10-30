package com.stockFlow.stockflow_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.junit.jupiter.api.Test;

import com.stockFlow.stockflow_api.movements.Movements;
import com.stockFlow.stockflow_api.products.Product;

/**
 * Valida o mapeamento das entidades (anotações) SEM conexão com banco: monta o
 * metamodelo do Hibernate com dialeto MySQL e inspeciona as colunas. Não substitui
 * o teste com MySQL real (ddl-auto=validate, QA-03 / Testcontainers).
 */
class EntityMappingTest {

    private Metadata buildMetadata() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build();
        return new MetadataSources(registry)
                .addAnnotatedClass(Product.class)
                .addAnnotatedClass(Movements.class)
                .buildMetadata();
    }

    @Test
    void mapeamentoDasEntidades_eValido_eDeletedAtMapeadoParaColunaDeletedAt() {
        Metadata metadata = buildMetadata();

        PersistentClass product = metadata.getEntityBinding(Product.class.getName());
        Property deletedAt = product.getProperty("deletedAt");
        assertThat(deletedAt.getColumns().get(0).getName()).isEqualTo("deleted_at");
        assertThat(deletedAt.getColumns().get(0).isNullable()).isTrue();

        // colunas geradas pelo MySQL: não inseridas/atualizadas pelo Hibernate, mas relidas
        for (String generated : new String[] { "created_at", "updated_at" }) {
            Property p = product.getProperty(generated);
            assertThat(p.isInsertable()).isFalse();
            assertThat(p.isUpdateable()).isFalse();
            assertThat(p.getValueGeneratorCreator()).as(generated + " @Generated").isNotNull();
        }

        PersistentClass movements = metadata.getEntityBinding(Movements.class.getName());
        Property createdAt = movements.getProperty("createdAt");
        assertThat(createdAt.isInsertable()).isFalse();
        assertThat(createdAt.getValueGeneratorCreator()).as("Movements.createdAt @Generated").isNotNull();
    }

    @Test
    void toString_naoEntraEmRecursaoEntreProdutoEMovimentacao() {
        Product product = new Product();
        product.setSku("A");
        Movements movement = new Movements();
        movement.setProduct(product);
        product.getMovements().add(movement);

        assertThat(product.toString()).contains("sku=A").doesNotContain("movements=");
        assertThat(movement.toString()).doesNotContain("product=");
    }
}
