package com.codingx.common.idempotent;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.codingx.common.exception.ConflictException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 幂等提交切面，统一拦截重复提交并返回中文冲突提示。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentSubmitAspect {

    private static final DefaultParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();
    private static final List<String> SPEL_PREFIXES = ListUtil.of("#", "T(");

    private final IdempotentLockProvider idempotentLockProvider;

    /**
     * 增强标记了 {@link IdempotentSubmit} 的方法，防止重复提交。
     * @param joinPoint 切点上下文。
     * @return 原方法返回值。
     * @throws Throwable 原方法异常。
     */
    @Around("@annotation(com.codingx.common.idempotent.IdempotentSubmit)")
    public Object idempotentSubmit(ProceedingJoinPoint joinPoint) throws Throwable {
        IdempotentSubmit idempotentSubmit = getIdempotentSubmitAnnotation(joinPoint);
        String lockKey = buildLockKey(joinPoint, idempotentSubmit);
        IdempotentLockProvider.IdempotentLock lock = idempotentLockProvider.lock(lockKey);
        boolean locked = lock.tryLock(
            Math.max(0L, idempotentSubmit.waitTimeMs()),
            Math.max(1_000L, idempotentSubmit.leaseTimeMs())
        );
        if (!locked) {
            throw new ConflictException(idempotentSubmit.code(), idempotentSubmit.message());
        }
        try {
            return joinPoint.proceed();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 读取方法上的幂等注解。
     * @param joinPoint 切点上下文。
     * @return 幂等注解配置。
     * @throws NoSuchMethodException 未找到方法定义时抛出。
     */
    static IdempotentSubmit getIdempotentSubmitAnnotation(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method targetMethod = joinPoint.getTarget()
            .getClass()
            .getDeclaredMethod(methodSignature.getName(), methodSignature.getMethod().getParameterTypes());
        return targetMethod.getAnnotation(IdempotentSubmit.class);
    }

    /**
     * 构造幂等锁键：优先 SpEL 自定义键，否则回退请求路径 + 当前用户 + 参数摘要。
     * @param joinPoint 切点上下文。
     * @param idempotentSubmit 注解配置。
     * @return 幂等锁键。
     */
    private String buildLockKey(ProceedingJoinPoint joinPoint, IdempotentSubmit idempotentSubmit) {
        if (StrUtil.isNotBlank(idempotentSubmit.key())) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Object keyValue = parseLockKeyValue(idempotentSubmit.key(), signature.getMethod(), joinPoint.getArgs());
            return "idempotent-submit:key:" + keyValue;
        }
        return "idempotent-submit:path:"
            + getServletPath()
            + ":currentUserId:"
            + getCurrentUserId()
            + ":md5:"
            + calcArgsMD5(joinPoint);
    }

    /**
     * 获取当前请求路径。
     * @return servletPath。
     */
    private String getServletPath() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown-path";
        }
        return attributes.getRequest().getServletPath();
    }

    /**
     * 获取当前登录用户标识，未登录时使用 anonymous。
     * @return 用户标识字符串。
     */
    private String getCurrentUserId() {
        try {
            return Objects.toString(cn.dev33.satoken.stp.StpUtil.getLoginIdDefaultNull(), "anonymous");
        } catch (Exception ignored) {
            return "anonymous";
        }
    }

    /**
     * 对参数进行摘要，避免锁键过长。
     * @param joinPoint 切点上下文。
     * @return 参数 MD5 摘要。
     */
    private String calcArgsMD5(ProceedingJoinPoint joinPoint) {
        return DigestUtil.md5Hex(cn.hutool.json.JSONUtil.toJsonStr(joinPoint.getArgs()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 直接在切面内解析锁键，避免热重载时遗漏工具类字节码导致幂等校验失效。
     * @param keyExpression 注解中声明的键表达式。
     * @param method 目标方法。
     * @param args 调用参数。
     * @return 解析后的锁键值。
     */
    private Object parseLockKeyValue(String keyExpression, Method method, Object[] args) {
        Optional<String> spelPrefix = SPEL_PREFIXES.stream().filter(keyExpression::contains).findFirst();
        if (spelPrefix.isEmpty()) {
            return keyExpression;
        }
        return evaluateSpelKey(keyExpression, method, args);
    }

    /**
     * 解析 SpEL 锁键表达式，并把方法入参注入上下文。
     * @param keyExpression SpEL 表达式。
     * @param method 目标方法。
     * @param args 调用参数。
     * @return 表达式执行结果。
     */
    private Object evaluateSpelKey(String keyExpression, Method method, Object[] args) {
        Expression expression = EXPRESSION_PARSER.parseExpression(keyExpression);
        String[] parameterNames = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
        StandardEvaluationContext context = new StandardEvaluationContext();
        if (ArrayUtil.isNotEmpty(parameterNames)) {
            for (int index = 0; index < parameterNames.length; index++) {
                context.setVariable(parameterNames[index], args[index]);
            }
        }
        return expression.getValue(context);
    }
}
