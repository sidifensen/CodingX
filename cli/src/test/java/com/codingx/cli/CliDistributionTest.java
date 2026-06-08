package com.codingx.cli;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CLI 分发测试，锁定可执行 jar 和 Windows 本地命令安装脚本的关键契约。
 */
class CliDistributionTest {

    /**
     * 验证 Maven package 会生成带主类的单文件 jar，避免本地 `codingx` 命令继续依赖 `mvn exec:java`。
     *
     * @throws Exception XML 解析失败时让测试直接失败。
     */
    @Test
    void pomShouldBuildExecutableShadedJar() throws Exception {
        Path pom = Path.of(System.getProperty("user.dir")).resolve("pom.xml");
        Document document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(pom.toFile());

        String xmlText = document.getDocumentElement().getTextContent();
        NodeList artifactIds = document.getElementsByTagName("artifactId");

        assertTrue(hasTextNode(artifactIds, "maven-shade-plugin"));
        assertTrue(xmlText.contains("com.codingx.cli.CodingXCli"));
        assertTrue(xmlText.contains("${project.build.directory}/codingx.jar"));
    }

    /**
     * 验证 CI 打包脚本会生成发布 jar、校验文件，并在安装模式下更新 Windows 本地命令入口。
     *
     * @throws Exception 脚本读取失败时让测试直接失败。
     */
    @Test
    void packageScriptShouldBuildArtifactAndInstallWindowsLaunchers() throws Exception {
        Path packageScript = Path.of(System.getProperty("user.dir"))
            .getParent()
            .resolve("script")
            .resolve("package-codingx-cli.ps1");
        String script = Files.readString(packageScript);

        assertTrue(script.contains("target\\dist"));
        assertTrue(script.contains("codingx.jar.sha256"));
        assertTrue(script.contains("mvn @mavenArgs"));
        assertTrue(script.contains(".codingx\\bin"));
        assertTrue(script.contains("codingx.cmd"));
        assertTrue(script.contains("codingx.ps1"));
        assertTrue(script.contains("[Environment]::SetEnvironmentVariable"));
        assertTrue(script.contains("java -jar"));
    }

    /**
     * 验证 BAT 打包入口和 PowerShell 入口保持同等关键能力，便于 CI 或普通 cmd 环境直接调用。
     *
     * @throws Exception 脚本读取失败时让测试直接失败。
     */
    @Test
    void packageBatShouldBuildArtifactAndInstallWindowsLaunchers() throws Exception {
        Path packageScript = Path.of(System.getProperty("user.dir"))
            .getParent()
            .resolve("script")
            .resolve("package-codingx-cli.bat");
        String script = Files.readString(packageScript);

        assertTrue(script.contains("target\\dist"));
        assertTrue(script.contains("codingx.jar.sha256"));
        assertTrue(script.contains("call mvn"));
        assertTrue(script.contains(".codingx\\bin"));
        assertTrue(script.contains("codingx.cmd"));
        assertTrue(script.contains("codingx.ps1"));
        assertTrue(script.contains("setx Path"));
        assertTrue(script.contains("java -jar"));
        assertTrue(isAscii(script));
    }

    /**
     * 验证旧安装入口只代理到统一打包脚本，避免 CI 打包和本地更新维护两套逻辑。
     *
     * @throws Exception 脚本读取失败时让测试直接失败。
     */
    @Test
    void installScriptShouldDelegateToPackageScriptWithInstallMode() throws Exception {
        Path installScript = Path.of(System.getProperty("user.dir"))
            .getParent()
            .resolve("script")
            .resolve("install-codingx.ps1");
        String script = Files.readString(installScript);

        assertTrue(script.contains("package-codingx-cli.ps1"));
        assertTrue(script.contains("-Install"));
        assertTrue(script.contains("-Clean:$Clean"));
        assertTrue(script.contains("-SkipTests:$SkipTests"));
    }

    /**
     * 验证 BAT 安装入口只代理到统一 BAT 打包脚本，避免 Windows 用户维护两套安装逻辑。
     *
     * @throws Exception 脚本读取失败时让测试直接失败。
     */
    @Test
    void installBatShouldDelegateToPackageBatWithInstallMode() throws Exception {
        Path installScript = Path.of(System.getProperty("user.dir"))
            .getParent()
            .resolve("script")
            .resolve("install-codingx.bat");
        String script = Files.readString(installScript);

        assertTrue(script.contains("package-codingx-cli.bat"));
        assertTrue(script.contains("--install"));
        assertTrue(script.contains("%*"));
        assertTrue(isAscii(script));
    }

    /**
     * 判断 XML 节点列表中是否存在指定文本值。
     *
     * @param nodes XML 节点列表。
     * @param expected 期望文本。
     * @return 存在时返回 true。
     */
    private boolean hasTextNode(NodeList nodes, String expected) {
        for (int index = 0; index < nodes.getLength(); index++) {
            if (expected.equals(nodes.item(index).getTextContent().trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * BAT 文件在 cmd 解析阶段不可靠处理 UTF-8 中文注释，保持 ASCII 能避免代码页导致的命令破坏。
     *
     * @param value 脚本文本。
     * @return 只包含 ASCII 字符时返回 true。
     */
    private boolean isAscii(String value) {
        return value.chars().allMatch(character -> character < 128);
    }
}
