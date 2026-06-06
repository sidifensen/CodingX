package com.codingx.cli.tui;

import java.nio.file.Path;

/**
 * 渲染 TUI 顶部品牌区，只展示真实可确认的产品和工作区信息。
 */
public class TuiHeaderRenderer {

    /**
     * 当前 CLI 壳版本；真实发版后可替换为构建元信息。
     */
    private static final String VERSION = "v0.1.0";

    /**
     * 渲染顶部 header。
     *
     * @param workspace 当前 CLI 工作区。
     * @return 多行 header 文本。
     */
    public String render(Path workspace) {
        return "CodingX CLI " + VERSION + System.lineSeparator()
            + "Workspace  " + workspace + System.lineSeparator()
            + "Ask CodingX to inspect, edit, or explain this workspace.";
    }
}
