package com.stockFlow.stockflow_api.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Segurança por perfil (T-07 / D-9):
 * <ul>
 * <li><b>dev / test</b>: /product e /movements abertos + CORS configurável
 * (stockflow.cors.allowed-origins).</li>
 * <li><b>sem perfil (padrão = produção)</b>: tudo exige autenticação, 401 via
 * HttpStatusEntryPoint e SEM CORS, até existir autenticação real (FUT-01).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	/** Perfis dev/test: API aberta para o frontend local. */
	@Bean
	@Profile({ "dev", "test" })
	SecurityFilterChain openSecurityFilterChain(HttpSecurity http,
			@Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource)
			throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/product", "/product/**", "/movements", "/movements/**").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable());
		return http.build();
	}

	/** Perfil padrão (produção): fechado, sem CORS. */
	@Bean
	@Profile("!dev & !test")
	SecurityFilterChain closedSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.disable())
				.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
				.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable());
		return http.build();
	}

	/** CORS apenas em dev/test. Origens separadas por vírgula. */
	@Bean
	@Profile({ "dev", "test" })
	CorsConfigurationSource corsConfigurationSource(
			@Value("${stockflow.cors.allowed-origins:http://localhost:3000}") List<String> allowedOrigins) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Content-Type", "Authorization"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

}
