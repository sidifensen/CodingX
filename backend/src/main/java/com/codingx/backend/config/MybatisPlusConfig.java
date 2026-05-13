package com.codingx.backend.config;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Spring beans and infrastructure required by MybatisPlusConfig.
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * Executes the logic defined by identifierGenerator.
     * @return processing result.
     */
    @Bean
    public IdentifierGenerator identifierGenerator() {
        return new DefaultIdentifierGenerator();
    }
}
