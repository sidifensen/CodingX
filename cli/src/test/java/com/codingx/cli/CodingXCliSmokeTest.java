package com.codingx.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CLI 工程烟雾测试，确保 Maven 子工程能加载入口类。
 */
class CodingXCliSmokeTest {

    @Test
    void mainClassShouldExposeProductName() {
        assertEquals("CodingX CLI", CodingXCli.productName());
    }
}
