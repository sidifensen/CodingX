package com.codingx.support.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 在确认首 token 到达前缓冲增量，避免路由切换时向上层泄漏半截脏数据。
 */
public class FirstTokenBufferingHandler implements AiStreamHandler {

    private final AiStreamHandler delegate;
    private final List<String> bufferedDeltas = new ArrayList<>();
    private boolean firstTokenReceived;

    /**
     * 使用真正的下游处理器构造首包缓冲包装器。
     * @param delegate 下游处理器。
     */
    public FirstTokenBufferingHandler(AiStreamHandler delegate) {
        this.delegate = delegate;
    }

    /**
     * 在首 token 确认前缓存增量。
     * @param delta 缓存的正文增量。
     */
    public void bufferDelta(String delta) {
        bufferedDeltas.add(delta);
    }

    @Override
    public void onContentDelta(String delta) {
        if (!firstTokenReceived) {
            firstTokenReceived = true;
            flushBufferedDeltas();
        }
        delegate.onContentDelta(delta);
    }

    @Override
    public void onComplete() {
        delegate.onComplete();
    }

    @Override
    public void onError(Throwable throwable) {
        delegate.onError(throwable);
    }

    /**
     * 将缓存的增量按原顺序释放给下游。
     */
    private void flushBufferedDeltas() {
        for (String bufferedDelta : bufferedDeltas) {
            delegate.onContentDelta(bufferedDelta);
        }
        bufferedDeltas.clear();
    }
}
