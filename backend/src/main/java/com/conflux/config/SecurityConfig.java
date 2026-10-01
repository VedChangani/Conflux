package com.conflux.config;

import java.io.IOException;

import com.conflux.auth.AuthController;
import com.conflux.common.web.ApiPaths;
import com.conflux.common.web.HealthController;
import com.conflux.listing.ListingController;
import com.conflux.user.UserController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			JwtAuthenticationConverter jwtAuthenticationConverter,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
		AuthenticationEntryPoint entryPoint = (request, response, ex) -> resolve(exceptionResolver, request,
				response, ex, HttpStatus.UNAUTHORIZED);
		AccessDeniedHandler accessDeniedHandler = (request, response, ex) -> resolve(exceptionResolver, request,
				response, ex, HttpStatus.FORBIDDEN);
		http
			.cors(Customizer.withDefaults())
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.oauth2ResourceServer(oauth2 -> oauth2
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
				.authenticationEntryPoint(entryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(entryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(ApiPaths.ADMIN + "/**").hasRole("ADMIN")
				.requestMatchers(HttpMethod.GET, HealthController.HEALTH_PATH).permitAll()
				.requestMatchers(HttpMethod.POST, AuthController.REGISTER_PATH, AuthController.LOGIN_PATH).permitAll()
				.requestMatchers(HttpMethod.GET, ListingController.MINE_PATH, ListingController.MINE_PATH + "/**")
				.authenticated()
				.requestMatchers(HttpMethod.GET, ListingController.BASE_PATH, ListingController.BASE_PATH + "/*")
				.permitAll()
				.requestMatchers(HttpMethod.GET, UserController.BASE_PATH + "/*").permitAll()
				.requestMatchers("/error").permitAll()
				.anyRequest().authenticated());
		return http.build();
	}

	private static void resolve(HandlerExceptionResolver resolver, HttpServletRequest request,
			HttpServletResponse response, Exception ex, HttpStatus fallbackStatus) throws IOException {
		if (resolver.resolveException(request, response, null, ex) == null) {
			response.sendError(fallbackStatus.value());
		}
	}

}
