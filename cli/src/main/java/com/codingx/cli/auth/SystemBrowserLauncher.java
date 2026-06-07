package com.codingx.cli.auth;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

/**
 * 使用系统默认浏览器打开 CLI 登录页。
 */
public class SystemBrowserLauncher implements BrowserLauncher {

    @Override
    public void open(String url) throws IOException {
        // 步骤 1：优先使用 Desktop API，适配 Windows/macOS/Linux 常见桌面环境。
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("当前环境不支持自动打开浏览器");
        }
        // 步骤 2：仅把已构造好的登录 URL 交给系统浏览器，不在这里拼接业务参数。
        Desktop.getDesktop().browse(URI.create(url));
    }
}

