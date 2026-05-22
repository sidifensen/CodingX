package com.codingx.config;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 验证 GlobalExceptionHandler 的关键场景。
 */
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new ExceptionThrowingController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    /**
     * 执行 forbiddenExceptionMapsTo403 定义的处理逻辑。
     */
    @Test
    void forbiddenExceptionMapsTo403() throws Exception {

        mockMvc.perform(get("/forbidden"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("blocked"));
    }

    /**
     * 执行 notFoundExceptionMapsTo404 定义的处理逻辑。
     */
    @Test
    void notFoundExceptionMapsTo404() throws Exception {

        mockMvc.perform(get("/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("missing"));
    }

    /**
     * 上传大小超限异常应统一映射为 400 和中文错误文案。
     */
    @Test
    void maxUploadSizeExceededMapsTo400() throws Exception {
        mockMvc.perform(get("/upload-too-large"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CHAT_ATTACHMENT_TOO_LARGE"))
            .andExpect(jsonPath("$.message").value("上传文件大小不能超过 10MB"));
    }

    /**
     * 负责配置 ExceptionThrowingController 所需的 Spring Bean 与基础设施。
     */
    @RestController
    static class ExceptionThrowingController {

        /**
         * 执行 forbidden 定义的处理逻辑。
         */
        @GetMapping("/forbidden")
        public void forbidden() {
            throw new ForbiddenException("blocked");
        }

        /**
         * 执行 missing 定义的处理逻辑。
         */
        @GetMapping("/missing")
        public void missing() {
            throw new NotFoundException("missing");
        }

        /**
         * 模拟上传大小超限异常，验证全局异常处理输出。
         */
        @GetMapping("/upload-too-large")
        public void uploadTooLarge() {
            throw new MaxUploadSizeExceededException(10L * 1024L * 1024L);
        }
    }
}
