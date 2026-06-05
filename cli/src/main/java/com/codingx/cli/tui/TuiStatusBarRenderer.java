package com.codingx.cli.tui;

/**
 * 渲染 TUI 底部模式栏，后续真实规划策略接入时只替换状态来源。
 */
public class TuiStatusBarRenderer {

    /**
     * 渲染底部状态栏。
     *
     * @param planMode 计划模式是否开启。
     * @param status 当前运行状态。
     * @param modelName 当前展示模型名。
     * @return 单行状态栏文本。
     */
    public String render(boolean planMode, String status, String modelName) {
        String planLabel = planMode ? "Plan on" : "Plan off";
        return planLabel + " (shift+tab to cycle)"
            + "    Status: " + status
            + "    " + modelName;
    }
}
