package com.codingx.chat.application.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要自动记录 Trace 子节点的方法。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConversationTraceNode {

    /**
     * 节点显示名称。
     * @return 节点名称。
     */
    String name();

    /**
     * 节点类型。
     * @return 节点类型。
     */
    String type();
}
