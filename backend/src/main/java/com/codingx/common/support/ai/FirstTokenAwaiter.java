package com.codingx.common.support.ai;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;

/**
 * 在流式启动后等待首个有效事件，区分成功、超时、无内容完成和错误。
 */
public class FirstTokenAwaiter {

    /**
     * 首个终态事件闩锁，用于阻塞路由线程直到 provider 产生内容、错误、完成或超时。
     */
    private final CountDownLatch latch = new CountDownLatch(1);

    /**
     * 是否已经收到 thinking、正文或工具调用等有效内容事件。
     */
    private final AtomicBoolean hasContent = new AtomicBoolean(false);

    /**
     * 首事件触发标记，确保并发回调下只有第一个事件唤醒等待线程。
     */
    private final AtomicBoolean eventFired = new AtomicBoolean(false);

    /**
     * provider 首包前返回的异常；非空时优先判定为 ERROR。
     */
    private final AtomicReference<Throwable> error = new AtomicReference<>();

    /**
     * 标记收到有效首包事件。
     */
    public void markContent() {
        // 步骤 1：先记录已收到有效内容，等待线程醒来后可判定为 SUCCESS。
        hasContent.set(true);
        // 步骤 2：唤醒等待线程，重复内容事件不会重复 countDown。
        fireEventOnce();
    }

    /**
     * 标记流在无错误情况下完成。
     */
    public void markComplete() {
        // 步骤 1：无内容完成也需要唤醒等待线程，由 await() 统一判定为 NO_CONTENT。
        fireEventOnce();
    }

    /**
     * 标记流式过程发生错误。
     * @param throwable 异常对象。
     */
    public void markError(Throwable throwable) {
        // 步骤 1：记录原始异常，方便路由层保留 provider 失败根因。
        error.set(throwable);
        // 步骤 2：唤醒等待线程，避免首包前错误被当作超时处理。
        fireEventOnce();
    }

    /**
     * 等待首个事件到达并返回结果。
     * @param timeout 超时时间。
     * @param unit 时间单位。
     * @return 首包等待结果。
     * @throws InterruptedException 等待被中断时抛出。
     */
    public Result await(long timeout, TimeUnit unit) throws InterruptedException {
        // 步骤 1：等待首事件或超时；中断由调用方处理并取消 provider 会话。
        boolean completed = latch.await(timeout, unit);
        if (error.get() != null) {
            // 步骤 2：错误优先级最高，即使同时出现完成信号也应暴露失败原因。
            return Result.error(error.get());
        }
        if (!completed) {
            // 步骤 3：未收到任何事件表示首包超时，路由层可安全 fallback。
            return Result.timeout();
        }
        if (!hasContent.get()) {
            // 步骤 4：收到完成但没有内容，表示 provider 空响应，同样允许 fallback。
            return Result.noContent();
        }
        // 步骤 5：收到有效内容后才算首包成功，可以提交缓冲事件。
        return Result.success();
    }

    /**
     * 仅允许首个事件唤醒等待线程。
     */
    private void fireEventOnce() {
        if (eventFired.compareAndSet(false, true)) {
            // 只让首个事件释放闩锁，后续事件由流式 session 的完成 Future 继续跟踪。
            latch.countDown();
        }
    }

    /**
     * 首包等待结果。
     */
    @Getter
    public static class Result {

        /**
         * 四种首包判定结果。
         */
        public enum Type {
            SUCCESS,
            ERROR,
            TIMEOUT,
            NO_CONTENT
        }

        /**
         * 首包判定类型，调用方根据该类型决定提交、fallback 或报错。
         */
        private final Type type;

        /**
         * provider 返回的原始异常，仅 ERROR 类型有值。
         */
        private final Throwable error;

        private Result(Type type, Throwable error) {
            // 步骤 1：Result 只通过静态工厂创建，保证 type 与 error 组合受控。
            this.type = type;
            this.error = error;
        }

        /**
         * 构造成功结果。
         * @return 成功结果。
         */
        public static Result success() {
            return new Result(Type.SUCCESS, null);
        }

        /**
         * 构造错误结果。
         * @param throwable 错误对象。
         * @return 错误结果。
         */
        public static Result error(Throwable throwable) {
            return new Result(Type.ERROR, throwable);
        }

        /**
         * 构造超时结果。
         * @return 超时结果。
         */
        public static Result timeout() {
            return new Result(Type.TIMEOUT, null);
        }

        /**
         * 构造无内容完成结果。
         * @return 无内容结果。
         */
        public static Result noContent() {
            return new Result(Type.NO_CONTENT, null);
        }

        /**
         * 是否表示首包成功。
         * @return 成功标记。
         */
        public boolean isSuccess() {
            return type == Type.SUCCESS;
        }
    }
}

