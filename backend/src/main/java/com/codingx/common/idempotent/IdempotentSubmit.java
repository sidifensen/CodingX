package com.codingx.common.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等提交注解，用于防止同一用户在短时间内重复提交同类请求。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentSubmit {

    /**
     * 幂等锁业务键，支持 SpEL；为空时回退到「路径 + 用户 + 参数摘要」默认策略。
     */
    String key() default "";

    /**
     * 命中重复提交时返回给前端的中文提示文案。
     */
    String message() default "当前会话处理中，请稍后再试";

    /**
     * 命中重复提交时返回的业务错误码。
     */
    String code() default "IDEMPOTENT_CONFLICT";

    /**
     * 获取锁最大等待毫秒数，0 表示不等待直接失败。
     */
    long waitTimeMs() default 0L;

    /**
     * 锁租约毫秒数，超过该时间未释放会自动过期，防止异常中断导致锁泄漏。
     */
    long leaseTimeMs() default 30_000L;
}
