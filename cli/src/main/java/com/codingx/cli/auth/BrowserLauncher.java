package com.codingx.cli.auth;

import java.io.IOException;

/**
 * 浏览器打开能力契约，隔离 CLI 登录流程与真实系统浏览器，便于测试替换。
 */
@FunctionalInterface
public interface BrowserLauncher {

    /**
     * 打开指定 URL。
     *
     * @param url 待打开地址。
     * @throws IOException 浏览器启动失败时抛出。
     */
    void open(String url) throws IOException;
}

