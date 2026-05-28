package com.codingx.common.idempotent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.common.exception.ConflictException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * 验证幂等切面的重复提交拦截行为。
 */
class IdempotentSubmitAspectTest {

    /**
     * 当幂等锁获取失败时应返回冲突异常并携带注解文案。
     */
    @Test
    void shouldRejectWhenLockNotAcquired() {
        IdempotentLockProvider lockProvider = mock(IdempotentLockProvider.class);
        IdempotentLockProvider.IdempotentLock lock = mock(IdempotentLockProvider.IdempotentLock.class);
        when(lockProvider.lock(org.mockito.ArgumentMatchers.contains("idempotent-submit"))).thenReturn(lock);
        when(lock.tryLock(0L, 30_000L)).thenReturn(false);

        IdempotentSubmitAspect aspect = new IdempotentSubmitAspect(lockProvider);
        SampleService target = new SampleService();
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(aspect);
        SampleService proxy = proxyFactory.getProxy();

        ConflictException exception = assertThrows(ConflictException.class, proxy::submit);

        assertEquals("CHAT_DUPLICATE", exception.getCode());
        assertEquals("重复提交", exception.getMessage());
    }

    /**
     * 当幂等锁获取成功时应放行原逻辑。
     */
    @Test
    void shouldProceedWhenLockAcquired() {
        IdempotentLockProvider lockProvider = mock(IdempotentLockProvider.class);
        IdempotentLockProvider.IdempotentLock lock = mock(IdempotentLockProvider.IdempotentLock.class);
        when(lockProvider.lock(org.mockito.ArgumentMatchers.contains("idempotent-submit"))).thenReturn(lock);
        when(lock.tryLock(0L, 30_000L)).thenReturn(true);

        IdempotentSubmitAspect aspect = new IdempotentSubmitAspect(lockProvider);
        SampleService target = new SampleService();
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(aspect);
        SampleService proxy = proxyFactory.getProxy();

        assertEquals("ok", proxy.submit());
        assertTrue(target.invoked);
    }

    /**
     * 回归测试：即使热重载场景丢失了外部工具类字节码，切面本身也应能解析 SpEL 键。
     */
    @Test
    void shouldResolveSpelKeyWithoutExternalHelperClass() throws Throwable {
        IdempotentLockProvider lockProvider = mock(IdempotentLockProvider.class);
        IdempotentLockProvider.IdempotentLock lock = mock(IdempotentLockProvider.IdempotentLock.class);
        when(lockProvider.lock(anyString())).thenReturn(lock);
        when(lock.tryLock(0L, 30_000L)).thenReturn(true);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        SpelKeySampleService target = new SpelKeySampleService();
        Method targetMethod = SpelKeySampleService.class.getDeclaredMethod("submit", String.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(new Object[] {"stream-1"});
        when(joinPoint.proceed()).thenReturn("ok");
        when(signature.getName()).thenReturn("submit");
        when(signature.getMethod()).thenReturn(targetMethod);

        try (MissingSpelHelperClassLoader classLoader = new MissingSpelHelperClassLoader()) {
            Class<?> aspectClass = classLoader.loadClass(IdempotentSubmitAspect.class.getName());
            Object aspect = aspectClass
                .getConstructor(IdempotentLockProvider.class)
                .newInstance(lockProvider);

            Method adviceMethod = aspectClass.getMethod("idempotentSubmit", ProceedingJoinPoint.class);
            Object result = adviceMethod.invoke(aspect, joinPoint);

            assertEquals("ok", result);
            verify(lockProvider).lock("idempotent-submit:key:stream-1");
        } catch (InvocationTargetException exception) {
            throw unwrapInvocationTarget(exception);
        }
    }

    /**
     * 最小业务样例。
     */
    static class SampleService {

        private boolean invoked;

        @IdempotentSubmit(message = "重复提交", code = "CHAT_DUPLICATE")
        public String submit() {
            invoked = true;
            return "ok";
        }
    }

    /**
     * 带 SpEL 键的样例方法，用于覆盖运行时锁键解析链路。
     */
    static class SpelKeySampleService {

        @IdempotentSubmit(key = "#requestId", message = "重复提交", code = "CHAT_DUPLICATE")
        public String submit(String requestId) {
            return requestId;
        }
    }

    /**
     * 通过隔离类加载器模拟热重载遗漏 `SpELUtil.class` 的运行态。
     */
    static final class MissingSpelHelperClassLoader extends URLClassLoader {

        private static final String TARGET_CLASS = IdempotentSubmitAspect.class.getName();
        private static final String HIDDEN_CLASS = "com.codingx.common.idempotent.SpELUtil";

        MissingSpelHelperClassLoader() {
            super(new URL[] {codeSourceUrl()}, IdempotentSubmitAspect.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (HIDDEN_CLASS.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            if (!TARGET_CLASS.equals(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = findClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private static URL codeSourceUrl() {
            return IdempotentSubmitAspect.class.getProtectionDomain().getCodeSource().getLocation();
        }
    }

    /**
     * 将反射包装的真实异常重新抛出，方便直接观察回归失败原因。
     */
    private static Exception unwrapInvocationTarget(InvocationTargetException exception) throws Exception {
        Throwable targetException = exception.getTargetException();
        if (targetException instanceof Exception runtimeException) {
            return runtimeException;
        }
        throw exception;
    }
}
