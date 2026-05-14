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
    }
}
