package cex.crypto.trading.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis configuration class
 * Configures mapper scanning and custom components
 */
@Configuration
@MapperScan("cex.crypto.trading.mapper")
public class MyBatisConfig {

    /**
     * Configure Jackson ObjectMapper bean for JSON serialization
     * Used by TypeHandlers and potentially other components
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Register JavaTimeModule to handle Java 8 date/time types
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
