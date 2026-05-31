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

    /**
     * 幂等锁提供器，根据运行环境选择本地锁或 Redisson 分布式锁，保证重复提交判断与部署拓扑解耦。
     */
    private final IdempotentLockProvider idempotentLockProvider;

    /**
     * 增强标记了 {@link IdempotentSubmit} 的方法，防止重复提交。
     * @param joinPoint 切点上下文。
     * @return 原方法返回值。
     * @throws Throwable 原方法异常。
     */
    @Around("@annotation(com.codingx.common.idempotent.IdempotentSubmit)")
    public Object idempotentSubmit(ProceedingJoinPoint joinPoint) throws Throwable {
        // 步骤 1：读取方法注解并构造锁键，锁键必须能区分接口、用户和关键入参，避免误拦截正常请求。
        IdempotentSubmit idempotentSubmit = getIdempotentSubmitAnnotation(joinPoint);
        String lockKey = buildLockKey(joinPoint, idempotentSubmit);

        // 步骤 2：按注解配置尝试获取幂等锁；获取失败表示存在并发重复提交，直接返回业务冲突。
        IdempotentLockProvider.IdempotentLock lock = idempotentLockProvider.lock(lockKey);
        boolean locked = lock.tryLock(
            Math.max(0L, idempotentSubmit.waitTimeMs()),
            Math.max(1_000L, idempotentSubmit.leaseTimeMs())
        );
        if (!locked) {
            throw new ConflictException(idempotentSubmit.code(), idempotentSubmit.message());
        }
        try {
            // 步骤 3：锁获取成功后执行原方法，业务异常保持原样交给全局异常处理器转换响应。
            return joinPoint.proceed();
        } finally {
            // 步骤 4：无论原方法成功或失败都释放锁，避免后续请求被错误阻塞。
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
            // 步骤 1：业务显式指定 key 时优先使用，适合按资源 ID 或请求唯一标识做精确幂等。
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Object keyValue = parseLockKeyValue(idempotentSubmit.key(), signature.getMethod(), joinPoint.getArgs());
            return "idempotent-submit:key:" + keyValue;
        }
        // 步骤 2：未指定 key 时回退到“路径 + 当前用户 + 参数摘要”，兼顾通用性与锁键长度。
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
            // 非 Web 线程触发时仍返回稳定占位值，避免空指针破坏幂等切面。
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
            // 已登录请求使用 Sa-Token 登录标识参与锁键，避免不同用户之间互相阻塞。
            return Objects.toString(cn.dev33.satoken.stp.StpUtil.getLoginIdDefaultNull(), "anonymous");
        } catch (Exception ignored) {
            // 鉴权上下文不可用时降级为匿名用户，保证公开接口或测试场景仍可执行。
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
            // 普通字符串不走 SpEL，减少表达式解析开销并避免误把固定 key 当作变量。
            return keyExpression;
        }
        // 表达式 key 需要结合方法参数求值，保证锁粒度能跟随业务入参变化。
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
        // 步骤 1：编译注解中的 SpEL 表达式，表达式错误会在调用入口快速暴露。
        Expression expression = EXPRESSION_PARSER.parseExpression(keyExpression);
        String[] parameterNames = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
        StandardEvaluationContext context = new StandardEvaluationContext();
        if (ArrayUtil.isNotEmpty(parameterNames)) {
            // 步骤 2：把方法参数名映射到上下文变量，使 key 能通过 #request.id 等形式引用入参。
            for (int index = 0; index < parameterNames.length; index++) {
                context.setVariable(parameterNames[index], args[index]);
            }
        }
        // 步骤 3：返回表达式结果，调用方会追加统一前缀形成最终锁键。
        return expression.getValue(context);
    }
}
