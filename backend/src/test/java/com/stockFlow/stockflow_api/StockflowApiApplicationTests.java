package com.stockFlow.stockflow_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import com.stockFlow.stockflow_api.integration.AbstractMySqlIntegrationTest;

/**
 * QA-03: o contexto COMPLETO sobe contra o MySQL real (Testcontainers) com
 * {@code ddl-auto=validate} — se qualquer entidade divergir do schema do
 * {@code database/init.sql}, o contexto falha aqui. Redis não está disponível
 * (porta inexistente) e a aplicação mesmo assim sobe: ela não usa Redis.
 */
@Tag("integration")
class StockflowApiApplicationTests extends AbstractMySqlIntegrationTest {

	@Autowired
	private Environment environment;

	@Test
	void contextLoads() {
		// Se chegou aqui, o contexto (incluindo a validação entidades × schema) subiu.
	}

	@Test
	void contextoSobeComDdlAutoValidate_semRedisDisponivel() {
		assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
		assertThat(environment.getProperty("spring.data.redis.port")).isEqualTo("1");
		assertThat(environment.getActiveProfiles()).contains("test");
	}

}
