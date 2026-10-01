package com.conflux.ratelimit;

import com.conflux.auth.AuthController;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
