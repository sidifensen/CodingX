# Java CLI Terminal MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first Java-based CodingX terminal entry point so `codingx` can render a terminal-style Agent event stream locally before the full backend Agent Runtime is implemented.

**Architecture:** Create an isolated `cli/` Maven project that does not modify the existing backend or frontend. The CLI has a small domain model for `AgentEvent`, a local mock event source for MVP demonstration, a renderer that maps events to terminal output, and a command entry point supporting `exec`, `login`, `resume`, and `sessions` placeholders. Later phases will replace the mock source with the backend Agent API from the approved spec.

**Tech Stack:** JDK 21, Maven, Java `HttpClient` boundary, SnakeYAML for config, JUnit 5 for tests, terminal text renderer first; tui4j remains the target TUI dependency for the next interactive UI pass after the basic terminal is stable.

---

## Scope

This plan intentionally implements the smallest useful terminal foundation:

- A standalone Java CLI project under `cli/`.
- A `codingx exec "..."` flow that prints a realistic Agent event stream.
- Config loading/saving that keeps tokens outside the project workspace.
- Event parsing and rendering for assistant, tool, command, approval, completion, interruption, and error events.
- Tests for config, event parsing, rendering, and command behavior.

This plan does not create Agent database tables, does not rewrite `ChatStreamController`, and does not require the backend to compile in the current dirty worktree.

## File Structure

- Create `cli/pom.xml`: Java 21 CLI Maven project with JUnit 5 and SnakeYAML.
- Create `cli/src/main/java/com/codingx/cli/CodingXCli.java`: command entry point.
- Create `cli/src/main/java/com/codingx/cli/config/CliConfig.java`: immutable config value object.
- Create `cli/src/main/java/com/codingx/cli/config/CliConfigStore.java`: reads/writes YAML config under the user home directory.
- Create `cli/src/main/java/com/codingx/cli/agent/AgentEvent.java`: event envelope and factory helpers.
- Create `cli/src/main/java/com/codingx/cli/agent/AgentEventType.java`: supported MVP event types.
- Create `cli/src/main/java/com/codingx/cli/agent/AgentEventSource.java`: event source interface.
- Create `cli/src/main/java/com/codingx/cli/agent/MockAgentEventSource.java`: local MewCode/Codex-style demo stream.
- Create `cli/src/main/java/com/codingx/cli/render/TerminalRenderer.java`: maps events to terminal lines.
- Create `cli/src/main/java/com/codingx/cli/command/CliCommandRunner.java`: parses commands and orchestrates config/source/rendering.
- Create `cli/src/test/java/com/codingx/cli/config/CliConfigStoreTest.java`: config path and YAML behavior tests.
- Create `cli/src/test/java/com/codingx/cli/render/TerminalRendererTest.java`: event rendering tests.
- Create `cli/src/test/java/com/codingx/cli/command/CliCommandRunnerTest.java`: command flow tests.
- Create `docs/features/agent/java-cli-terminal-mvp.md`: feature note for current true implementation.

## Task 1: CLI Project Skeleton

**Files:**
- Create: `cli/pom.xml`
- Create: `cli/src/main/java/com/codingx/cli/CodingXCli.java`
- Create: `cli/src/test/java/com/codingx/cli/CodingXCliSmokeTest.java`

- [ ] **Step 1: Write the failing smoke test**

Create `cli/src/test/java/com/codingx/cli/CodingXCliSmokeTest.java`:

```java
package com.codingx.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodingXCliSmokeTest {

    @Test
    void mainClassShouldExposeProductName() {
        assertEquals("CodingX CLI", CodingXCli.productName());
    }
}
```

- [ ] **Step 2: Run the test to verify RED**

Run:

```bash
cd cli
mvn -Dtest=CodingXCliSmokeTest test
```

Expected: FAIL because `cli/pom.xml` or `CodingXCli` does not exist yet.

- [ ] **Step 3: Create the Maven project and minimal main class**

Create `cli/pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.codingx</groupId>
    <artifactId>codingx-cli</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>CodingX CLI</name>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <junit.version>5.11.4</junit.version>
        <snakeyaml.version>2.3</snakeyaml.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.yaml</groupId>
            <artifactId>snakeyaml</artifactId>
            <version>${snakeyaml.version}</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
        </plugins>
    </build>
</project>
```

Create `cli/src/main/java/com/codingx/cli/CodingXCli.java`:

```java
package com.codingx.cli;

/**
 * CodingX 独立终端入口，第一阶段只负责启动本地 CLI 命令分发。
 */
public final class CodingXCli {

    private CodingXCli() {
    }

    /**
     * 返回 CLI 产品名，供启动横幅和烟雾测试复用。
     *
     * @return 产品名。
     */
    public static String productName() {
        return "CodingX CLI";
    }

    /**
     * Java CLI 进程入口。
     *
     * @param args 命令行参数。
     */
    public static void main(String[] args) {
        System.out.println(productName());
    }
}
```

- [ ] **Step 4: Run GREEN**

Run:

```bash
cd cli
mvn -Dtest=CodingXCliSmokeTest test
```

Expected: PASS.

## Task 2: Config Storage Outside Workspace

**Files:**
- Create: `cli/src/main/java/com/codingx/cli/config/CliConfig.java`
- Create: `cli/src/main/java/com/codingx/cli/config/CliConfigStore.java`
- Create: `cli/src/test/java/com/codingx/cli/config/CliConfigStoreTest.java`

- [ ] **Step 1: Write failing config tests**

Create `cli/src/test/java/com/codingx/cli/config/CliConfigStoreTest.java`:

```java
package com.codingx.cli.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliConfigStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadShouldUseUserHomeConfigDirectory() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Path userHome = tempDir.resolve("home");
        Files.createDirectories(workspace);
        Files.createDirectories(userHome);
        CliConfigStore store = new CliConfigStore(userHome);

        store.save(new CliConfig("http://localhost:5001", "test-token", "conservative", null));

        Path configFile = userHome.resolve(".codingx").resolve("cli.yml");
        assertTrue(Files.exists(configFile));
        assertFalse(Files.exists(workspace.resolve("cli.yml")));
        CliConfig loaded = store.load();
        assertEquals("http://localhost:5001", loaded.serverUrl());
        assertEquals("test-token", loaded.token());
        assertEquals("conservative", loaded.approvalPolicy());
    }
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
cd cli
mvn -Dtest=CliConfigStoreTest test
```

Expected: FAIL because config classes do not exist.

- [ ] **Step 3: Implement config classes**

Create `cli/src/main/java/com/codingx/cli/config/CliConfig.java`:

```java
package com.codingx.cli.config;

/**
 * CLI 本机配置快照，令牌只允许存放在用户主目录配置文件中。
 *
 * @param serverUrl 后端服务地址。
 * @param token satoken 登录令牌。
 * @param approvalPolicy 默认审批策略。
 * @param lastSessionId 最近会话标识，可为空。
 */
public record CliConfig(
    String serverUrl,
    String token,
    String approvalPolicy,
    String lastSessionId
) {
    /**
     * 构造缺省配置，便于首次运行时给出稳定默认值。
     *
     * @return 默认配置。
     */
    public static CliConfig defaults() {
        return new CliConfig("http://localhost:5001", "", "conservative", null);
    }
}
```

Create `cli/src/main/java/com/codingx/cli/config/CliConfigStore.java`:

```java
package com.codingx.cli.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 负责读写 CodingX CLI 用户级配置，避免把 satoken 写入项目仓库。
 */
public class CliConfigStore {

    /**
     * 用户主目录路径，用于隔离 CLI 配置和当前工作区。
     */
    private final Path userHome;

    /**
     * @param userHome 当前系统用户主目录。
     */
    public CliConfigStore(Path userHome) {
        this.userHome = userHome;
    }

    /**
     * 加载配置；配置不存在时返回默认值。
     *
     * @return CLI 配置。
     */
    public CliConfig load() {
        Path configFile = configFile();
        if (!Files.exists(configFile)) {
            return CliConfig.defaults();
        }
        Yaml yaml = new Yaml();
        try (Reader reader = Files.newBufferedReader(configFile)) {
            Object loaded = yaml.load(reader);
            if (!(loaded instanceof Map<?, ?> map)) {
                return CliConfig.defaults();
            }
            return new CliConfig(
                stringValue(map.get("serverUrl"), CliConfig.defaults().serverUrl()),
                stringValue(map.get("token"), ""),
                stringValue(map.get("approvalPolicy"), CliConfig.defaults().approvalPolicy()),
                stringValue(map.get("lastSessionId"), null)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("读取 CLI 配置失败", exception);
        }
    }

    /**
     * 保存配置到用户主目录。
     *
     * @param config 配置快照。
     */
    public void save(CliConfig config) {
        Path configFile = configFile();
        try {
            Files.createDirectories(configFile.getParent());
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("serverUrl", config.serverUrl());
            values.put("token", config.token());
            values.put("approvalPolicy", config.approvalPolicy());
            values.put("lastSessionId", config.lastSessionId());
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                new Yaml().dump(values, writer);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("保存 CLI 配置失败", exception);
        }
    }

    /**
     * @return 配置文件路径。
     */
    public Path configFile() {
        return userHome.resolve(".codingx").resolve("cli.yml");
    }

    private String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }
}
```

- [ ] **Step 4: Run GREEN**

Run:

```bash
cd cli
mvn -Dtest=CliConfigStoreTest test
```

Expected: PASS.

## Task 3: Agent Event Model And Renderer

**Files:**
- Create: `cli/src/main/java/com/codingx/cli/agent/AgentEventType.java`
- Create: `cli/src/main/java/com/codingx/cli/agent/AgentEvent.java`
- Create: `cli/src/main/java/com/codingx/cli/render/TerminalRenderer.java`
- Create: `cli/src/test/java/com/codingx/cli/render/TerminalRendererTest.java`

- [ ] **Step 1: Write failing renderer tests**

Create `cli/src/test/java/com/codingx/cli/render/TerminalRendererTest.java`:

```java
package com.codingx.cli.render;

import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalRendererTest {

    @Test
    void rendererShouldPrintAssistantToolCommandAndApprovalEvents() {
        TerminalRenderer renderer = new TerminalRenderer();
        List<String> lines = renderer.render(List.of(
            AgentEvent.of("s1", "t1", 1, AgentEventType.TURN_STARTED, Map.of("task", "分析项目")),
            AgentEvent.of("s1", "t1", 2, AgentEventType.ASSISTANT_DELTA, Map.of("delta", "我先查看项目结构。")),
            AgentEvent.of("s1", "t1", 3, AgentEventType.TOOL_STARTED, Map.of("toolId", "ls")),
            AgentEvent.of("s1", "t1", 4, AgentEventType.COMMAND_OUTPUT_DELTA, Map.of("stream", "stdout", "delta", "backend\\nfrontend\\n")),
            AgentEvent.of("s1", "t1", 5, AgentEventType.APPROVAL_REQUESTED, Map.of("risk", "HIGH", "reason", "需要修改文件", "summary", "写入 README.md")),
            AgentEvent.of("s1", "t1", 6, AgentEventType.TURN_COMPLETED, Map.of("status", "COMPLETED"))
        ));

        String output = String.join("\n", lines);
        assertTrue(output.contains("开始任务: 分析项目"));
        assertTrue(output.contains("我先查看项目结构。"));
        assertTrue(output.contains("工具: ls"));
        assertTrue(output.contains("stdout"));
        assertTrue(output.contains("需要审批"));
        assertTrue(output.contains("任务完成"));
    }
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
cd cli
mvn -Dtest=TerminalRendererTest test
```

Expected: FAIL because event and renderer classes do not exist.

- [ ] **Step 3: Implement event model and renderer**

Create `cli/src/main/java/com/codingx/cli/agent/AgentEventType.java`:

```java
package com.codingx.cli.agent;

/**
 * CLI MVP 支持的 Agent 事件类型，命名映射后端统一事件协议。
 */
public enum AgentEventType {
    SESSION_STARTED("session.started"),
    TURN_STARTED("turn.started"),
    ASSISTANT_DELTA("assistant.delta"),
    THINKING_DELTA("thinking.delta"),
    TOOL_STARTED("tool.started"),
    TOOL_OUTPUT_DELTA("tool.output.delta"),
    TOOL_COMPLETED("tool.completed"),
    COMMAND_STARTED("command.started"),
    COMMAND_OUTPUT_DELTA("command.output.delta"),
    COMMAND_COMPLETED("command.completed"),
    FILE_DIFF("file.diff"),
    APPROVAL_REQUESTED("approval.requested"),
    APPROVAL_RESOLVED("approval.resolved"),
    TURN_COMPLETED("turn.completed"),
    TURN_INTERRUPTED("turn.interrupted"),
    ERROR("error"),
    UNKNOWN("unknown");

    private final String wireName;

    AgentEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
```

Create `cli/src/main/java/com/codingx/cli/agent/AgentEvent.java`:

```java
package com.codingx.cli.agent;

import java.time.Instant;
import java.util.Map;

/**
 * Agent 事件统一信封，CLI 和 Web 后续都按该结构消费运行时事件。
 *
 * @param sessionId 会话标识。
 * @param turnId 任务轮次标识。
 * @param sequence 会话内递增序号。
 * @param eventType 事件类型。
 * @param payload 事件载荷。
 * @param createdAt 创建时间。
 */
public record AgentEvent(
    String sessionId,
    String turnId,
    long sequence,
    AgentEventType eventType,
    Map<String, Object> payload,
    Instant createdAt
) {
    public static AgentEvent of(
        String sessionId,
        String turnId,
        long sequence,
        AgentEventType eventType,
        Map<String, Object> payload
    ) {
        return new AgentEvent(sessionId, turnId, sequence, eventType, payload, Instant.now());
    }

    public String payloadText(String key) {
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
```

Create `cli/src/main/java/com/codingx/cli/render/TerminalRenderer.java`:

```java
package com.codingx.cli.render;

import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventType;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 Agent 事件转换为终端可读文本；后续 tui4j UI 可复用同一渲染语义。
 */
public class TerminalRenderer {

    /**
     * 批量渲染事件。
     *
     * @param events Agent 事件列表。
     * @return 终端输出行。
     */
    public List<String> render(List<AgentEvent> events) {
        List<String> lines = new ArrayList<>();
        for (AgentEvent event : events) {
            lines.add(render(event));
        }
        return lines;
    }

    /**
     * 渲染单个事件。
     *
     * @param event Agent 事件。
     * @return 终端输出文本。
     */
    public String render(AgentEvent event) {
        AgentEventType type = event.eventType();
        return switch (type) {
            case SESSION_STARTED -> "[session] " + event.sessionId();
            case TURN_STARTED -> "[turn] 开始任务: " + event.payloadText("task");
            case ASSISTANT_DELTA -> event.payloadText("delta");
            case THINKING_DELTA -> "[thinking] " + event.payloadText("delta");
            case TOOL_STARTED -> "[tool] 工具: " + event.payloadText("toolId");
            case TOOL_OUTPUT_DELTA -> "[tool:output] " + event.payloadText("delta");
            case TOOL_COMPLETED -> "[tool] 完成: " + event.payloadText("toolId");
            case COMMAND_STARTED -> "[cmd] " + event.payloadText("command");
            case COMMAND_OUTPUT_DELTA -> "[cmd:" + event.payloadText("stream") + "] " + event.payloadText("delta");
            case COMMAND_COMPLETED -> "[cmd] 退出码: " + event.payloadText("exitCode");
            case FILE_DIFF -> "[diff] " + event.payloadText("path");
            case APPROVAL_REQUESTED -> "[approval] 需要审批 [" + event.payloadText("risk") + "] "
                + event.payloadText("reason") + " - " + event.payloadText("summary");
            case APPROVAL_RESOLVED -> "[approval] 已处理: " + event.payloadText("result");
            case TURN_COMPLETED -> "[turn] 任务完成: " + event.payloadText("status");
            case TURN_INTERRUPTED -> "[turn] 已中断";
            case ERROR -> "[error] " + event.payloadText("message");
            case UNKNOWN -> "[event] 未知事件";
        };
    }
}
```

- [ ] **Step 4: Run GREEN**

Run:

```bash
cd cli
mvn -Dtest=TerminalRendererTest test
```

Expected: PASS.

## Task 4: Mock Agent Event Source

**Files:**
- Create: `cli/src/main/java/com/codingx/cli/agent/AgentEventSource.java`
- Create: `cli/src/main/java/com/codingx/cli/agent/MockAgentEventSource.java`
- Create: `cli/src/test/java/com/codingx/cli/agent/MockAgentEventSourceTest.java`

- [ ] **Step 1: Write failing mock source test**

Create `cli/src/test/java/com/codingx/cli/agent/MockAgentEventSourceTest.java`:

```java
package com.codingx.cli.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockAgentEventSourceTest {

    @Test
    void mockSourceShouldReturnOrderedTerminalDemoEvents() {
        MockAgentEventSource source = new MockAgentEventSource();

        List<AgentEvent> events = source.startTurn("分析这个项目", Path.of("D:/code/CodingX"));

        assertTrue(events.size() >= 6);
        assertEquals(1, events.get(0).sequence());
        assertEquals(AgentEventType.SESSION_STARTED, events.get(0).eventType());
        assertTrue(events.stream().anyMatch(event -> event.eventType() == AgentEventType.TOOL_STARTED));
        assertTrue(events.stream().anyMatch(event -> event.eventType() == AgentEventType.COMMAND_OUTPUT_DELTA));
        assertTrue(events.stream().anyMatch(event -> event.eventType() == AgentEventType.TURN_COMPLETED));
    }
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
cd cli
mvn -Dtest=MockAgentEventSourceTest test
```

Expected: FAIL because mock source does not exist.

- [ ] **Step 3: Implement source interface and mock stream**

Create `cli/src/main/java/com/codingx/cli/agent/AgentEventSource.java`:

```java
package com.codingx.cli.agent;

import java.nio.file.Path;
import java.util.List;

/**
 * Agent 事件来源；MVP 用 mock，后续替换为后端 Agent API 客户端。
 */
public interface AgentEventSource {

    /**
     * 启动一轮任务并返回事件流快照。
     *
     * @param task 用户任务。
     * @param workspace 当前工作区。
     * @return 有序事件。
     */
    List<AgentEvent> startTurn(String task, Path workspace);
}
```

Create `cli/src/main/java/com/codingx/cli/agent/MockAgentEventSource.java`:

```java
package com.codingx.cli.agent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 本地模拟 Agent 事件流，用于先把终端体验跑起来。
 */
public class MockAgentEventSource implements AgentEventSource {

    @Override
    public List<AgentEvent> startTurn(String task, Path workspace) {
        String sessionId = "local-demo-session";
        String turnId = "local-demo-turn";
        List<AgentEvent> events = new ArrayList<>();
        events.add(AgentEvent.of(sessionId, turnId, 1, AgentEventType.SESSION_STARTED, Map.of(
            "workspace", workspace.toString()
        )));
        events.add(AgentEvent.of(sessionId, turnId, 2, AgentEventType.TURN_STARTED, Map.of(
            "task", task
        )));
        events.add(AgentEvent.of(sessionId, turnId, 3, AgentEventType.ASSISTANT_DELTA, Map.of(
            "delta", "我会先查看当前仓库结构，再给出下一步建议。"
        )));
        events.add(AgentEvent.of(sessionId, turnId, 4, AgentEventType.TOOL_STARTED, Map.of(
            "toolId", "ls"
        )));
        events.add(AgentEvent.of(sessionId, turnId, 5, AgentEventType.COMMAND_OUTPUT_DELTA, Map.of(
            "stream", "stdout",
            "delta", "backend/\nfrontend/\ndocs/\ncli/\n"
        )));
        events.add(AgentEvent.of(sessionId, turnId, 6, AgentEventType.TOOL_COMPLETED, Map.of(
            "toolId", "ls"
        )));
        events.add(AgentEvent.of(sessionId, turnId, 7, AgentEventType.ASSISTANT_DELTA, Map.of(
            "delta", "基础终端已可接收 AgentEvent；下一步可以把事件源替换为后端 Agent API。"
        )));
        events.add(AgentEvent.of(sessionId, turnId, 8, AgentEventType.TURN_COMPLETED, Map.of(
            "status", "COMPLETED"
        )));
        return events;
    }
}
```

- [ ] **Step 4: Run GREEN**

Run:

```bash
cd cli
mvn -Dtest=MockAgentEventSourceTest test
```

Expected: PASS.

## Task 5: Command Runner And CLI Commands

**Files:**
- Create: `cli/src/main/java/com/codingx/cli/command/CliCommandRunner.java`
- Modify: `cli/src/main/java/com/codingx/cli/CodingXCli.java`
- Create: `cli/src/test/java/com/codingx/cli/command/CliCommandRunnerTest.java`

- [ ] **Step 1: Write failing command tests**

Create `cli/src/test/java/com/codingx/cli/command/CliCommandRunnerTest.java`:

```java
package com.codingx.cli.command;

import com.codingx.cli.agent.MockAgentEventSource;
import com.codingx.cli.config.CliConfigStore;
import com.codingx.cli.render.TerminalRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliCommandRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void execShouldRenderMockAgentEvents() {
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            new MockAgentEventSource(),
            new TerminalRenderer(),
            tempDir.resolve("workspace")
        );

        CliCommandRunner.Result result = runner.run(new String[] {"exec", "分析这个项目"});

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("开始任务: 分析这个项目"));
        assertTrue(result.output().contains("基础终端已可接收 AgentEvent"));
    }

    @Test
    void loginShouldSaveConfigOutsideWorkspace() {
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            new MockAgentEventSource(),
            new TerminalRenderer(),
            tempDir.resolve("workspace")
        );

        CliCommandRunner.Result result = runner.run(new String[] {"login", "http://localhost:5001", "token-123"});

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("登录配置已保存"));
    }
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
cd cli
mvn -Dtest=CliCommandRunnerTest test
```

Expected: FAIL because command runner does not exist.

- [ ] **Step 3: Implement command runner and wire main**

Create `cli/src/main/java/com/codingx/cli/command/CliCommandRunner.java`:

```java
package com.codingx.cli.command;

import com.codingx.cli.CodingXCli;
import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventSource;
import com.codingx.cli.config.CliConfig;
import com.codingx.cli.config.CliConfigStore;
import com.codingx.cli.render.TerminalRenderer;

import java.nio.file.Path;
import java.util.List;

/**
 * CLI 命令分发器，负责把用户输入转成配置读写或 Agent 事件流渲染。
 */
public class CliCommandRunner {

    private final CliConfigStore configStore;
    private final AgentEventSource eventSource;
    private final TerminalRenderer renderer;
    private final Path workspace;

    public CliCommandRunner(
        CliConfigStore configStore,
        AgentEventSource eventSource,
        TerminalRenderer renderer,
        Path workspace
    ) {
        this.configStore = configStore;
        this.eventSource = eventSource;
        this.renderer = renderer;
        this.workspace = workspace;
    }

    /**
     * 执行 CLI 命令。
     *
     * @param args 命令参数。
     * @return 命令结果。
     */
    public Result run(String[] args) {
        if (args.length == 0) {
            return help();
        }
        return switch (args[0]) {
            case "login" -> login(args);
            case "exec" -> exec(args);
            case "resume" -> new Result(0, "resume: 后续接入后端 AgentSession 后启用\n");
            case "sessions" -> new Result(0, "sessions: 后续接入后端 AgentSession 后启用\n");
            default -> exec(new String[] {"exec", String.join(" ", args)});
        };
    }

    private Result login(String[] args) {
        if (args.length < 3) {
            return new Result(1, "用法: codingx login <serverUrl> <satoken>\n");
        }
        configStore.save(new CliConfig(args[1], args[2], "conservative", null));
        return new Result(0, "登录配置已保存: " + configStore.configFile() + "\n");
    }

    private Result exec(String[] args) {
        if (args.length < 2 || args[1].trim().isEmpty()) {
            return new Result(1, "用法: codingx exec <任务>\n");
        }
        String task = args[1];
        List<AgentEvent> events = eventSource.startTurn(task, workspace);
        String output = String.join(System.lineSeparator(), renderer.render(events)) + System.lineSeparator();
        return new Result(0, output);
    }

    private Result help() {
        return new Result(0, CodingXCli.productName() + System.lineSeparator()
            + "用法:" + System.lineSeparator()
            + "  codingx login <serverUrl> <satoken>" + System.lineSeparator()
            + "  codingx exec <任务>" + System.lineSeparator()
            + "  codingx resume" + System.lineSeparator()
            + "  codingx sessions" + System.lineSeparator());
    }

    /**
     * CLI 命令执行结果。
     *
     * @param exitCode 进程退出码。
     * @param output 标准输出内容。
     */
    public record Result(int exitCode, String output) {
    }
}
```

Update `cli/src/main/java/com/codingx/cli/CodingXCli.java`:

```java
package com.codingx.cli;

import com.codingx.cli.agent.MockAgentEventSource;
import com.codingx.cli.command.CliCommandRunner;
import com.codingx.cli.config.CliConfigStore;
import com.codingx.cli.render.TerminalRenderer;

import java.nio.file.Path;

/**
 * CodingX 独立终端入口，第一阶段只负责启动本地 CLI 命令分发。
 */
public final class CodingXCli {

    private CodingXCli() {
    }

    /**
     * 返回 CLI 产品名，供启动横幅和烟雾测试复用。
     *
     * @return 产品名。
     */
    public static String productName() {
        return "CodingX CLI";
    }

    /**
     * Java CLI 进程入口。
     *
     * @param args 命令行参数。
     */
    public static void main(String[] args) {
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(Path.of(System.getProperty("user.home"))),
            new MockAgentEventSource(),
            new TerminalRenderer(),
            Path.of(System.getProperty("user.dir"))
        );
        CliCommandRunner.Result result = runner.run(args);
        System.out.print(result.output());
        if (result.exitCode() != 0) {
            System.exit(result.exitCode());
        }
    }
}
```

- [ ] **Step 4: Run GREEN**

Run:

```bash
cd cli
mvn -Dtest=CliCommandRunnerTest test
```

Expected: PASS.

## Task 6: Feature Documentation

**Files:**
- Create: `docs/features/agent/java-cli-terminal-mvp.md`
- Modify: `docs/features/index.md`

- [ ] **Step 1: Add feature document**

Create `docs/features/agent/java-cli-terminal-mvp.md`:

```markdown
# Java CLI 基础终端

## 功能用途

提供 CodingX 独立 CLI 的第一版基础终端入口，让开发者可以先在本地通过 Java CLI 查看 AgentEvent 风格的终端输出。当前版本使用 mock 事件源，不直接执行模型调用和工具调用。

## 使用入口

- `codingx login <serverUrl> <satoken>`：把后端地址和登录令牌保存到用户主目录 `.codingx/cli.yml`。
- `codingx exec "<任务>"`：在当前目录启动一轮本地 mock Agent 任务并打印终端事件流。
- `codingx resume` / `codingx sessions`：预留给后端 AgentSession 接入后的恢复和列表能力。

## 核心流程

1. 用户运行 CLI 命令后，`CodingXCli` 创建 `CliCommandRunner`，并把用户主目录、当前工作目录、事件源和终端渲染器注入命令分发器。
2. `login` 命令通过 `CliConfigStore` 使用 SnakeYAML 写入用户主目录配置文件，避免 satoken 落入项目仓库。
3. `exec` 命令把用户任务和当前工作区传给 `MockAgentEventSource`，生成一组有序 `AgentEvent`。
4. `TerminalRenderer` 按事件类型输出助手正文、工具状态、命令输出、审批提示和任务完成状态。
5. 当前版本不绕过后端执行模型或 MCP；后续接入后端 Agent API 时替换 `AgentEventSource` 即可。

## 关键文件

- `cli/src/main/java/com/codingx/cli/CodingXCli.java`：CLI 进程入口。
- `cli/src/main/java/com/codingx/cli/command/CliCommandRunner.java`：命令解析和流程编排。
- `cli/src/main/java/com/codingx/cli/agent/AgentEvent.java`：Agent 事件信封。
- `cli/src/main/java/com/codingx/cli/agent/MockAgentEventSource.java`：MVP mock 事件流。
- `cli/src/main/java/com/codingx/cli/render/TerminalRenderer.java`：终端文本渲染。
- `cli/src/main/java/com/codingx/cli/config/CliConfigStore.java`：用户级配置读写。

## 测试与验证

- `cd cli && mvn test`
- `cd cli && mvn exec:java -Dexec.mainClass=com.codingx.cli.CodingXCli -Dexec.args="exec 分析这个项目"`
```

- [ ] **Step 2: Update feature index**

Modify `docs/features/index.md` by adding:

```markdown
## Agent

- [Java CLI 基础终端](agent/java-cli-terminal-mvp.md)
```

Keep existing sections unchanged.

## Task 7: Verification

**Files:**
- Read-only verification over `cli/` and docs.

- [ ] **Step 1: Run CLI tests**

Run:

```bash
cd cli
mvn test
```

Expected: PASS.

- [ ] **Step 2: Run CLI demo command**

Run:

```bash
cd cli
mvn -q exec:java -Dexec.mainClass=com.codingx.cli.CodingXCli -Dexec.args="exec 分析这个项目"
```

Expected output contains:

```text
[turn] 开始任务: 分析这个项目
[tool] 工具: ls
[cmd:stdout]
[turn] 任务完成: COMPLETED
```

- [ ] **Step 3: Confirm no unrelated files are staged**

Run:

```bash
git diff --name-only --cached
```

Expected: only this plan/docs and `cli/` files from this task are staged before committing.
