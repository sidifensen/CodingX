package com.codingx.backend.config;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.codingx.backend.common.exception.ForbiddenException;
import com.codingx.backend.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tests the key scenarios covered by GlobalExceptionHandler.
 */
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new ExceptionThrowingController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    /**
     * Executes the logic defined by forbiddenExceptionMapsTo403.
     */
    @Test
    void forbiddenExceptionMapsTo403() throws Exception {

        mockMvc.perform(get("/forbidden"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("blocked"));
    }

    /**
     * Executes the logic defined by notFoundExceptionMapsTo404.
     */
    @Test
    void notFoundExceptionMapsTo404() throws Exception {

        mockMvc.perform(get("/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("missing"));
    }

    /**
     * Configures the Spring beans and infrastructure required by ExceptionThrowingController.
     */
    @RestController
    static class ExceptionThrowingController {

        /**
         * Executes the logic defined by forbidden.
         */
        @GetMapping("/forbidden")
        public void forbidden() {
            throw new ForbiddenException("blocked");
        }

        /**
         * Executes the logic defined by missing.
         */
        @GetMapping("/missing")
        public void missing() {
            throw new NotFoundException("missing");
        }
    }
}
