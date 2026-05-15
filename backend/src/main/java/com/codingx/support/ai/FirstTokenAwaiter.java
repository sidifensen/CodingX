package com.codingx.support.ai;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;

/**
 * 在流式启动后等待首个有效事件，区分成功、超时、无内容完成和错误。
 */
public class FirstTokenAwaiter {

    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicBoolean hasContent = new AtomicBoolean(false);
    private final AtomicBoolean eventFired = new AtomicBoolean(false);
    private final AtomicReference<Throwable> error = new AtomicReference<>();

    /**
     * 标记收到有效首包事件。
     */
    public void markContent() {
        hasContent.set(true);
        fireEventOnce();
    }

    /**
     * 标记流在无错误情况下完成。
     */
    public void markComplete() {
        fireEventOnce();
    }

    /**
     * 标记流式过程发生错误。
     * @param throwable 异常对象。
     */
    public void markError(Throwable throwable) {
        error.set(throwable);
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
        boolean completed = latch.await(timeout, unit);
        if (error.get() != null) {
            return Result.error(error.get());
        }
        if (!completed) {
            return Result.timeout();
        }
        if (!hasContent.get()) {
            return Result.noContent();
        }
        return Result.success();
    }

    /**
     * 仅允许首个事件唤醒等待线程。
     */
    private void fireEventOnce() {
        if (eventFired.compareAndSet(false, true)) {
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

        private final Type type;
        private final Throwable error;

        private Result(Type type, Throwable error) {
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
