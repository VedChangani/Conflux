package com.conflux.config;

import com.conflux.common.web.HealthController;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Baseline security for a stateless JSON API. No authentication mechanism is
 * configured yet: everything except the public endpoints below is rejected with 401.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.cors(Customizer.withDefaults())
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, HealthController.HEALTH_PATH).permitAll()
				.requestMatchers("/error").permitAll()
				.anyRequest().authenticated());
		return http.build();
	}

}
