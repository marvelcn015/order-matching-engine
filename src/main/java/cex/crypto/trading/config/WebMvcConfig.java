package cex.crypto.trading.config;

import cex.crypto.trading.interceptor.RateLimiterInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for registering interceptors
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private RateLimiterInterceptor rateLimiterInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimiterInterceptor)
                .addPathPatterns("/api/**") // Apply to all API endpoints
                .excludePathPatterns(
                    "/api-docs/**",           // Exclude Swagger docs
                    "/swagger-ui/**",         // Exclude Swagger UI
                    "/swagger-ui.html"        // Exclude Swagger UI
                );
    }
}
