package com.codingx.cli.tui;

import java.nio.file.Path;

/**
 * 渲染 TUI 底部状态栏，只展示当前 TUI 已真实维护的模式和运行状态。
 */
public class TuiStatusBarRenderer {

    /**
     * 渲染底部状态栏。
     *
     * @param planMode 计划模式是否开启。
     * @param status 当前运行状态。
     * @param workspace 当前工作区路径，用于提示本轮任务会作用在哪个目录。
     * @return 单行状态栏文本。
     */
    public String render(boolean planMode, String status, Path workspace) {
        String planLabel = planMode ? "Plan mode" : "Chat mode";
        return "server selected · " + workspace
            + " · " + status
            + " · " + planLabel + " (Shift+Tab)";
    }
}
