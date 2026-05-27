package com.codingx.config;

import cn.dev33.satoken.exception.NotLoginException;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.ConflictException;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.common.exception.NotFoundException;
import com.codingx.common.exception.UnauthorizedException;
import com.codingx.common.model.ApiResponse;
import com.codingx.common.support.web.ClientAbortExceptionDetector;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 负责统一处理后端异常：记录结构化中文日志，并返回可直接给前端展示的中文错误信息。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理认证失败异常，统一返回 401。
     * @param exception 认证异常。
     * @param request 当前请求。
     * @return 标准错误响应。
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, exception.getCode(), exception.getMessage(), exception, request, false);
    }

    /**
     * 处理业务异常，统一返回 400。
     * @param exception 业务异常。
     * @param request 当前请求。
     * @return 标准错误响应。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage(), exception, request, false);
    }

    /**
     * 处理请求冲突异常，统一返回 409。
     * @param exception 冲突异常。
     * @param request 当前请求。
     * @return 标准错误响应。
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage(), exception, request, false);
    }

    /**
     * 处理未登录异常。
     * @param exception 未登录异常。
     * @param request 当前请求。
     * @return 标准错误响应。
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotLogin(NotLoginException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ErrorMessageCatalog.UNAUTHORIZED, exception, request, false);
    }

    /**
     * 处理无权限异常。
     * @param exception 无权限异常。
     * @param request 当前请求。
     * @return 标准错误响应。
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException exception, HttpServletRequest request) {
        String message = sanitizeMessage(exception.getMessage(), ErrorMessageCatalog.FORBIDDEN);
        return buildResponse(HttpStatus.FORBIDDEN, exception.getCode(), message, exception, request, false);
    }

    /**
     * 处理资源不存在异常。
     * @param exception 资源不存在异常。
     * @param request 当前请求。
     * @return 标准错误响应。
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getCode(), exception.getMessage(), exception, request, false);
    }

    /**
     * 处理请求路由不存在或静态资源不存在异常，统一返回 404。
     * @param exception 未命中路由异常。
     * @param request 当前请求。
     * @return 标准错误响应。
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoRoute(Exception exception, HttpServletRequest request) {
        return buildResponse(
            HttpStatus.NOT_FOUND,
            "RESOURCE_NOT_FOUND",
            ErrorMessageCatalog.RESOURCE_NOT_FOUND,
            exception,
            request,
            false
        );
    }

    /**
     * 处理参数校验异常。
     * @param exception 参数校验异常。
     * @param request 当前请求。
     * @return 标准错误响应。
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidation(Exception exception, HttpServletRequest request) {
        String validationMessage = extractValidationMessage(exception);
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", validationMessage, exception, request, false);
    }

    /**
     * 处理路径参数类型转换异常，统一返回 400，避免临时字符串 ID 触发 500 系统异常。
     * @param exception 参数类型不匹配异常。
     * @param request 当前请求。
     * @return 标准错误响应。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ErrorMessageCatalog.VALIDATION_ERROR, exception, request, false);
    }

    /**
     * 处理上传体积超限异常，统一透出业务错误码和中文提示，避免前端看到容器层英文错误。
     * @param exception 上传超限异常。
     * @param request 当前请求。
     * @return 标准错误响应。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
        MaxUploadSizeExceededException exception,
        HttpServletRequest request
    ) {
        return buildResponse(
            HttpStatus.BAD_REQUEST,
            "CHAT_ATTACHMENT_TOO_LARGE",
            ErrorMessageCatalog.CHAT_ATTACHMENT_TOO_LARGE,
            exception,
            request,
            false
        );
    }

    /**
     * 处理未预期异常。
     * @param exception 未预期异常。
     * @param request 当前请求。
     * @return 标准错误响应。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnknown(Exception exception, HttpServletRequest request) {
        if (ClientAbortExceptionDetector.isClientAbort(exception)) {
            // 客户端已断开时响应体不可写，继续返回 ApiResponse 会造成二次异常和控制台刷屏。
            log.debug("客户端已断开连接，跳过错误响应 | 接口={}", buildEndpoint(request), exception);
            return ResponseEntity.noContent().build();
        }
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ErrorMessageCatalog.INTERNAL_ERROR, exception, request, true);
    }

    /**
     * 统一记录日志并构造响应。
     * @param status 响应状态码。
     * @param code 业务错误码。
     * @param message 给前端展示的中文错误信息。
     * @param exception 原始异常。
     * @param request 当前请求。
     * @param includeStack 是否打印堆栈。
     * @return 标准响应。
     */
    private ResponseEntity<ApiResponse<Void>> buildResponse(
        HttpStatus status,
        String code,
        String message,
        Exception exception,
        HttpServletRequest request,
        boolean includeStack
    ) {
        String normalizedCode = sanitizeMessage(code, "INTERNAL_ERROR");
        String normalizedMessage = sanitizeMessage(message, ErrorMessageCatalog.INTERNAL_ERROR);
        String endpoint = buildEndpoint(request);

        if (includeStack) {
            log.error("接口异常 | 状态码={} | 错误码={} | 接口={} | 返回信息={}", status.value(), normalizedCode, endpoint, normalizedMessage, exception);
        } else {
            log.warn("接口异常 | 状态码={} | 错误码={} | 接口={} | 返回信息={} | 原始异常={}", status.value(), normalizedCode, endpoint, normalizedMessage, exception.getMessage());
        }

        return ResponseEntity.status(status).body(ApiResponse.failure(normalizedCode, normalizedMessage));
    }

    /**
     * 拼接日志中的请求入口，保持所有异常日志格式一致。
     * @param request 当前请求。
     * @return HTTP 方法与路径。
     */
    private String buildEndpoint(HttpServletRequest request) {
        return request.getMethod() + " " + request.getRequestURI();
    }

    /**
     * 提取校验异常中的首条可读提示，优先返回业务友好的中文文案。
     * @param exception 校验异常。
     * @return 可展示提示文案。
     */
    private String extractValidationMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            FieldError fieldError = methodArgumentNotValidException.getBindingResult().getFieldError();
            if (fieldError != null && fieldError.getDefaultMessage() != null) {
                return fieldError.getDefaultMessage();
            }
        }
        if (exception instanceof BindException bindException) {
            FieldError fieldError = bindException.getBindingResult().getFieldError();
            if (fieldError != null && fieldError.getDefaultMessage() != null) {
                return fieldError.getDefaultMessage();
            }
        }
        if (exception instanceof ConstraintViolationException constraintViolationException
            && !constraintViolationException.getConstraintViolations().isEmpty()) {
            return constraintViolationException.getConstraintViolations().iterator().next().getMessage();
        }
        return sanitizeMessage(exception.getMessage(), ErrorMessageCatalog.VALIDATION_ERROR);
    }

    /**
     * 对字符串进行空值兜底，避免响应体或日志出现空信息。
     * @param value 原始值。
     * @param fallback 兜底值。
     * @return 非空字符串。
     */
    private String sanitizeMessage(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
