package com.codingx.common.idempotent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codingx.common.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

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
}
