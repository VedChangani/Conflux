package com.conflux.ratelimit;

import com.conflux.auth.AuthController;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires the shared {@link RateLimiter}. Registration and login are limited per client IP
 * by an MVC interceptor (after the security filter chain, which permits them anyway).
 * Authenticated writes are limited per user (admin moderation per administrator) by their
 * services, after the authentication, suspension, ownership, visibility and state checks,
 * so those keep answering 400/401/403/404/409 and never turn into 429. Only requests that
 * actually change something use the allowance: idempotent no-ops (e.g. saving a listing
 * that is already saved) are answered normally without counting.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig implements WebMvcConfigurer {

	private final RateLimiter rateLimiter;

	public RateLimitConfig(RateLimitProperties properties) {
		this.rateLimiter = new RateLimiter(properties, System::nanoTime);
	}

	@Bean
	public RateLimiter rateLimiter() {
		return this.rateLimiter;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new ClientIpRateLimitInterceptor(this.rateLimiter, RateLimitOperation.REGISTER))
			.addPathPatterns(AuthController.REGISTER_PATH);
		registry.addInterceptor(new ClientIpRateLimitInterceptor(this.rateLimiter, RateLimitOperation.LOGIN))
			.addPathPatterns(AuthController.LOGIN_PATH);
	}

}
