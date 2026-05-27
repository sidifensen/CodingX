package com.codingx.common.support.web;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/**
 * 识别客户端主动断开连接导致的响应写失败。
 * 业务意图：这类异常通常由浏览器刷新、取消请求或 SSE 断线触发，不代表后端业务失败。
 */
public final class ClientAbortExceptionDetector {

    /**
     * 不同容器、操作系统和代理对客户端断开的提示不完全一致，统一用保守关键字兜底。
     */
    private static final String[] CLIENT_ABORT_MESSAGES = {
        "Broken pipe",
        "Connection reset",
        "Connection reset by peer",
        "An established connection was aborted",
        "你的主机中的软件中止了一个已建立的连接",
        "ServletOutputStream failed to write",
        "Response not usable after response errors"
    };

    private ClientAbortExceptionDetector() {
    }

    /**
     * 判断异常链是否来自客户端断开连接。
     * @param exception 待判断异常。
     * @return 客户端断开返回 true；普通后端异常返回 false。
     */
    public static boolean isClientAbort(Throwable exception) {
        if (exception == null) {
            return false;
        }
        for (Throwable throwable : ExceptionUtil.getThrowableList(exception)) {
            if (throwable instanceof AsyncRequestNotUsableException || isTomcatClientAbort(throwable)) {
                return true;
            }
            String message = ExceptionUtil.getMessage(throwable);
            if (StrUtil.containsAnyIgnoreCase(message, CLIENT_ABORT_MESSAGES)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 避免业务代码直接依赖 Tomcat 专有异常类型，同时兼容嵌入式容器抛出的 ClientAbortException。
     * @param throwable 异常链节点。
     * @return 是否为 Tomcat 客户端断开异常。
     */
    private static boolean isTomcatClientAbort(Throwable throwable) {
        return StrUtil.equals("ClientAbortException", throwable.getClass().getSimpleName());
    }
}
