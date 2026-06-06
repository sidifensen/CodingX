package com.codingx.cli.tui;

import java.nio.file.Path;

/**
 * 渲染 TUI 顶部启动卡片，只展示真实可确认的产品、模型来源和工作区信息。
 */
public class TuiHeaderRenderer {

    /**
     * 当前 CLI 壳版本；真实发版后可替换为构建元信息。
     */
    static final String VERSION = "v0.1.0";

    /**
     * 当前 CLI 还没有本地模型切换器，模型实际由后端聊天链路选择。
     */
    private static final String MODEL_LABEL = "server selected";

    /**
     * 渲染顶部 header。
     *
     * @param workspace 当前 CLI 工作区。
     * @return 多行 header 文本。
     */
    public String render(Path workspace) {
        String title = ">_ CodingX CLI (" + VERSION + ")";
        String model = "model:     " + MODEL_LABEL;
        String directory = "directory: " + workspace;
        int innerWidth = Math.max(52, Math.max(title.length(), Math.max(model.length(), directory.length())));

        return "┌" + "─".repeat(innerWidth + 2) + "┐" + System.lineSeparator()
            + line(title, innerWidth) + System.lineSeparator()
            + line("", innerWidth) + System.lineSeparator()
            + line(model, innerWidth) + System.lineSeparator()
            + line(directory, innerWidth) + System.lineSeparator()
            + "└" + "─".repeat(innerWidth + 2) + "┘";
    }

    /**
     * 渲染卡片中的单行内容，确保较短文本不会破坏边框对齐。
     *
     * @param text 单行文本。
     * @param innerWidth 卡片内部宽度。
     * @return 带左右边框的单行文本。
     */
    private String line(String text, int innerWidth) {
        return "│ " + rightPad(text, innerWidth) + " │";
    }

    /**
     * 右侧补空格；工作区路径很长时保留原文，避免隐藏用户当前目录。
     *
     * @param text 原始文本。
     * @param width 期望最小宽度。
     * @return 补齐后的文本。
     */
    private String rightPad(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        return text + " ".repeat(width - text.length());
    }
}
