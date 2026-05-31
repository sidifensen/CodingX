package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatTraceNode;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * 基于注解自动记录聊天链路的 Trace 子节点。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class ConversationTraceAspect {

    /** Trace 记录服务，用于把被注解方法的开始、成功和失败状态写入链路节点。 */
    private final ConversationTraceRecordService conversationTraceRecordService;

    /**
     * 拦截被注解标记的方法，并在当前 trace 上自动记录一个子节点。
     * @param joinPoint 切点。
     * @param traceNode 注解配置。
     * @return 原方法返回值。
     * @throws Throwable 原方法异常。
     */
    @Around("@annotation(traceNode)")
    public Object around(ProceedingJoinPoint joinPoint, ConversationTraceNode traceNode) throws Throwable {
        if (ConversationTraceContext.current() == null) {
            return joinPoint.proceed();
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        LocalDateTime startedAt = LocalDateTime.now();
        ChatTraceNode node = conversationTraceRecordService.startNode(
            traceNode.name(),
            traceNode.type(),
            signature.getDeclaringTypeName(),
            signature.getMethod().getName(),
            startedAt
        );
        try {
            Object result = joinPoint.proceed();
            conversationTraceRecordService.finishNode(node, "SUCCESS", null, Duration.between(startedAt, LocalDateTime.now()).toMillis());
            return result;
        } catch (Throwable throwable) {
            conversationTraceRecordService.finishNode(node, "ERROR", throwable.getMessage(), Duration.between(startedAt, LocalDateTime.now()).toMillis());
            throw throwable;
        }
    }
}
