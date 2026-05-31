package com.codingx.config;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 基础配置，统一声明实体主键生成策略。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 创建默认雪花 ID 生成器。
     * @return MyBatis-Plus 使用的主键生成器。
     */
    @Bean
    public IdentifierGenerator identifierGenerator() {
        // 步骤 1：使用 MyBatis-Plus 默认实现生成 Long 主键，保持各业务表 ID 策略一致。
        return new DefaultIdentifierGenerator();
    }
}
