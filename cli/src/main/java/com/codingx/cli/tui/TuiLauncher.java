package com.codingx.cli.tui;

/**
 * TUI 启动边界，命令层通过该接口进入全屏终端，测试中可替换为 fake 实现。
 */
public interface TuiLauncher {

    /**
     * 启动 CodingX TUI；真实实现会接管当前终端直到用户退出。
     */
    void launch();
}
