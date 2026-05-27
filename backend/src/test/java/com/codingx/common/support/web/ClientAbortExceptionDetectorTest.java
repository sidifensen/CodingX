package com.codingx.common.support.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/**
 * 验证客户端断开连接异常识别规则，避免日志降噪误伤真实后端错误。
 */
class ClientAbortExceptionDetectorTest {

    /**
     * Spring 写响应失败时会包装为 AsyncRequestNotUsableException，应识别为客户端断开。
     */
    @Test
    void detectsSpringAsyncRequestNotUsableException() {
        boolean actual = ClientAbortExceptionDetector.isClientAbort(
            new AsyncRequestNotUsableException(
                "ServletOutputStream failed to write",
                new IOException("你的主机中的软件中止了一个已建立的连接。")
            )
        );

        assertTrue(actual);
    }

    /**
     * 常见操作系统网络断开提示也应识别为客户端断开。
     */
    @Test
    void detectsBrokenPipeMessage() {
        boolean actual = ClientAbortExceptionDetector.isClientAbort(new IOException("Broken pipe"));

        assertTrue(actual);
    }

    /**
     * 普通后端 IO 异常不能被当作客户端断开，否则会隐藏真实服务端故障。
     */
    @Test
    void rejectsRegularIOException() {
        boolean actual = ClientAbortExceptionDetector.isClientAbort(new IOException("读取配置文件失败"));

        assertFalse(actual);
    }
}
