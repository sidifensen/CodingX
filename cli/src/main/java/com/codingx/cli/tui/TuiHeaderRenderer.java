package com.codingx.cli.tui;

import java.nio.file.Path;

/**
 * 渲染 TUI 顶部品牌区，集中维护品牌、模型、工作区和 mock 工具连接状态。
 */
public class TuiHeaderRenderer {

    /**
     * 当前 CLI 壳版本；真实发版后可替换为构建元信息。
     */
    private static final String VERSION = "v0.1.0";

    /**
     * MCP 连接状态仍是 mock 文案，只用于先跑通截图风格外壳。
     */
    private static final String MOCK_TOOL_STATUS = "Connected to 1 MCP server(s), 2 tools registered";

    /**
     * 渲染顶部 header。
     *
     * @param workspace 当前 CLI 工作区。
     * @param modelName 当前展示模型名。
     * @return 多行 header 文本。
     */
    public String render(Path workspace, String modelName) {
        return " /\\_/\\    CodingX " + VERSION + System.lineSeparator()
            + "( o.o )   Model: " + modelName + System.lineSeparator()
            + " > ^ <    Workspace: " + workspace + System.lineSeparator()
            + System.lineSeparator()
            + MOCK_TOOL_STATUS;
    }
}
