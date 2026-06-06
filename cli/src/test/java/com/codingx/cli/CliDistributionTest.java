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
     * 验证安装脚本会写入用户目录 bin，并生成 cmd / PowerShell 两种入口，方便 Windows 终端直接输入 `codingx`。
     *
     * @throws Exception 脚本读取失败时让测试直接失败。
     */
    @Test
    void installScriptShouldCreateWindowsLaunchersAndUpdateUserPath() throws Exception {
        Path installScript = Path.of(System.getProperty("user.dir"))
            .getParent()
            .resolve("script")
            .resolve("install-codingx.ps1");
        String script = Files.readString(installScript);

        assertTrue(script.contains(".codingx"));
        assertTrue(script.contains("codingx.cmd"));
        assertTrue(script.contains("codingx.ps1"));
        assertTrue(script.contains("[Environment]::SetEnvironmentVariable"));
        assertTrue(script.contains("java -jar"));
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
}
