package com.codingx.config;
import cn.dev33.satoken.exception.NotLoginException;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.common.exception.NotFoundException;
import com.codingx.common.model.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 负责配置 GlobalExceptionHandler 所需的 Spring Bean 与基础设施。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 执行 handleBusiness 定义的处理逻辑。
     * @param exception 输入参数。
     * @return 输入参数。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    /**
     * 执行 handleNotLogin 定义的处理逻辑。
     * @param exception 输入参数。
     * @return 输入参数。
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotLogin(NotLoginException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.failure("UNAUTHORIZED", exception.getMessage()));
    }

    /**
     * 执行 handleForbidden 定义的处理逻辑。
     * @param exception 输入参数。
     * @return 输入参数。
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    /**
     * 执行 handleNotFound 定义的处理逻辑。
     * @param exception 输入参数。
     * @return 输入参数。
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    /**
     * 执行 handleValidation 定义的处理逻辑。
     * @param exception 输入参数。
     * @return 输入参数。
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidation(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("VALIDATION_ERROR", exception.getMessage()));
    }

    /**
     * 执行 handleUnknown 定义的处理逻辑。
     * @param exception 输入参数。
     * @return 输入参数。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception exception) {
        log.error("Unexpected error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.failure("INTERNAL_ERROR", exception.getMessage()));
    }
}
