package com.codingx.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一配置 Jackson 的 Long 序列化策略，避免前端读取超大 Snowflake ID 时发生精度丢失。
 */
@Configuration
public class JacksonConfig {

    /**
     * 将 Long 与 long 一律序列化为字符串，保证浏览器端能够无损读取会话、消息、执行记录等主键。
     * @return Jackson 自定义器。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder
            .serializerByType(Long.class, ToStringSerializer.instance)
            .serializerByType(Long.TYPE, ToStringSerializer.instance)
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
