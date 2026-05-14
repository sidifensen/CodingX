package com.codingx.config;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 负责配置 MybatisPlusConfig 所需的 Spring Bean 与基础设施。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 执行 identifierGenerator 定义的处理逻辑。
     * @return 输入参数。
     */
    @Bean
    public IdentifierGenerator identifierGenerator() {
        return new DefaultIdentifierGenerator();
    }
}
