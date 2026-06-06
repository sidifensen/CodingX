package com.codingx.cli.tui;

/**
 * TUI 启动边界，命令层通过该接口进入交互终端，测试中可替换为 fake 实现。
 */
public interface TuiLauncher {

    /**
     * 启动 CodingX TUI；真实实现会在当前普通屏幕中运行直到用户退出。
     */
    void launch();
}
