package com.codingx.cli.tui;

/**
 * 渲染 TUI 底部状态栏，只展示当前 TUI 已真实维护的模式和运行状态。
 */
public class TuiStatusBarRenderer {

    /**
     * 渲染底部状态栏。
     *
     * @param planMode 计划模式是否开启。
     * @param status 当前运行状态。
     * @return 单行状态栏文本。
     */
    public String render(boolean planMode, String status) {
        String planLabel = planMode ? "Plan mode" : "Chat mode";
        return planLabel + " (Shift+Tab)"
            + "    Status: " + status
            + "    Enter sends, Ctrl+C exits";
    }
}
