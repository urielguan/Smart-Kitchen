package com.xykj.common.exception;

import com.xykj.common.enums.ResultCode;
import com.xykj.common.result.R;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理各类异常，返回符合API规范的响应
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("业务异常: {} - {}", request.getRequestURI(), e.getMessage());
        R<Void> result = R.fail(e.getCode(), e.getMessage());
        return ResponseEntity.status(getHttpStatus(e.getCode())).body(result);
    }

    /**
     * 处理参数校验异常 (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(this::hasText)
                .findFirst()
                .orElse("请求参数校验失败，请检查后重试");
        log.warn("参数校验失败: {}", detail);
        R<Void> result = R.badRequest(message);
        return ResponseEntity.badRequest().body(result);
    }

    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<R<Void>> handleBindException(BindException e) {
        String detail = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(this::hasText)
                .findFirst()
                .orElse("请求参数绑定失败，请检查后重试");
        log.warn("参数绑定失败: {}", detail);
        R<Void> result = R.badRequest(message);
        return ResponseEntity.badRequest().body(result);
    }

    /**
     * 处理约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Void>> handleConstraintViolationException(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(this::hasText)
                .findFirst()
                .orElse("请求参数校验失败，请检查后重试");
        log.warn("约束校验失败: {}", detail);
        R<Void> result = R.badRequest(message);
        return ResponseEntity.badRequest().body(result);
    }

    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        R<Void> result = R.badRequest("缺少必传参数：" + e.getParameterName());
        return ResponseEntity.badRequest().body(result);
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {} - {}", e.getName(), e.getValue());
        R<Void> result = R.badRequest("参数类型错误：" + e.getName());
        return ResponseEntity.badRequest().body(result);
    }

    /**
     * 处理请求体解析异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request) {
        String detail = mostSpecificMessage(e);
        String message = isIntegrationProviderRequest(request)
                ? "请求模板JSON格式不正确，请检查括号、逗号、引号是否规范"
                : "请求参数JSON格式不正确，请检查括号、逗号、引号是否规范";
        log.warn("请求体解析失败: {} - {}", request.getRequestURI(), detail);
        R<Void> result = R.badRequest(message);
        return ResponseEntity.badRequest().body(result);
    }

    /**
     * 处理请求方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<R<Void>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的请求方法: {}", e.getMethod());
        R<Void> result = R.fail(ResultCode.BAD_REQUEST, "不支持的请求方法：" + e.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(result);
    }

    /**
     * 处理404异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<R<Void>> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("接口不存在: {}", e.getRequestURL());
        R<Void> result = R.notFound("接口不存在");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    /**
     * 处理 Spring Boot 3 静态资源/路径不存在异常
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Void>> handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("接口不存在: {}", request.getRequestURI());
        R<Void> result = R.notFound("接口不存在");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<R<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("文件大小超限: {} - {}", request.getRequestURI(), e.getMessage());
        String message = isSupplierImportRequest(request)
                ? "上传文件不能超过10MB"
                : "文件大小超过限制";
        R<Void> result = R.fail(ResultCode.BAD_REQUEST, message);
        return ResponseEntity.badRequest().body(result);
    }

    /**
     * 处理数据库数据完整性异常（如字段超长）
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<R<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException e, HttpServletRequest request) {
        String detail = mostSpecificMessage(e);
        log.warn("数据完整性异常: {} - {}", request.getRequestURI(), detail);
        if (detail != null && detail.contains("Data too long")) {
            return ResponseEntity.badRequest().body(R.badRequest("输入内容超出长度限制，请缩短后重试"));
        }
        if (detail != null && (detail.contains("uk_tenant_supplier_code") || detail.contains("uk_supplier_code"))) {
            return ResponseEntity.badRequest().body(R.badRequest("当前租户下已存在相同供应商编码，请修改后重新保存"));
        }
        if (detail != null && detail.contains("uk_tenant_supplier_name")) {
            return ResponseEntity.badRequest().body(R.badRequest("当前租户下已存在相同供应商名称，请修改后重新保存"));
        }
        if (detail != null && detail.contains("uk_tenant_business_license_no")) {
            return ResponseEntity.badRequest().body(R.badRequest("当前租户下存在相同营业执照编号的有效供应商，请修改后重试"));
        }
        if (detail != null && detail.contains("uk_tenant_food_license_no")) {
            return ResponseEntity.badRequest().body(R.badRequest("当前租户下存在相同食品许可证号的有效供应商，请修改后重试"));
        }
        if (looksLikeJsonIntegrityError(detail)) {
            String message = isIntegrationProviderRequest(request)
                    ? "请求模板JSON格式不正确，请检查括号、逗号、引号是否规范"
                    : "JSON格式不正确，请检查括号、逗号、引号是否规范";
            return ResponseEntity.badRequest().body(R.badRequest(message));
        }
        return ResponseEntity.badRequest().body(R.badRequest("数据保存失败，请检查字段内容是否符合要求"));
    }

    /**
     * 处理 JWT Token 过期异常
     */
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<R<Void>> handleExpiredJwtException(ExpiredJwtException e) {
        log.warn("Token已过期: {}", e.getMessage());
        R<Void> result = R.fail(ResultCode.TOKEN_EXPIRED);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    /**
     * 处理 JWT Token 无效异常
     */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<R<Void>> handleJwtException(JwtException e) {
        log.warn("Token无效: {}", e.getMessage());
        R<Void> result = R.fail(ResultCode.TOKEN_INVALID);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    /**
     * 处理运行时异常（业务逻辑抛出的异常）
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<R<Void>> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        log.warn("业务运行时异常: {} - {}", request.getRequestURI(), e.getMessage(), e);
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = "请求处理失败，请稍后重试";
        }
        R<Void> result = R.fail(ResultCode.VALIDATION_FAILED, message);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
    }

    /**
     * 处理客户端主动断开连接导致的响应写出失败。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsableException(AsyncRequestNotUsableException e, HttpServletRequest request) {
        log.warn("客户端连接已断开: {} - {}", request.getRequestURI(), e.getMessage());
        return ResponseEntity.noContent().build();
    }

    /**
     * 处理其他未知异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: {} - {}", request.getRequestURI(), e.getMessage(), e);
        R<Void> result = R.fail(ResultCode.INTERNAL_ERROR, "系统异常，请稍后重试");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    /**
     * 根据错误码获取HTTP状态码
     * 认证/业务校验类错误统一返回200，错误码在响应体中由前端判断处理
     */
    private HttpStatus getHttpStatus(String code) {
        if (ResultCode.SUCCESS.getCode().equals(code)) {
            return HttpStatus.OK;
        } else if (ResultCode.UNAUTHORIZED.getCode().equals(code)
                || ResultCode.TOKEN_EXPIRED.getCode().equals(code)
                || ResultCode.TOKEN_INVALID.getCode().equals(code)
                || ResultCode.REFRESH_TOKEN_INVALID.getCode().equals(code)
                || ResultCode.FORCE_LOGOUT.getCode().equals(code)) {
            return HttpStatus.UNAUTHORIZED;
        } else if (ResultCode.FORBIDDEN.getCode().equals(code)
                || ResultCode.DATA_SCOPE_DENIED.getCode().equals(code)) {
            return HttpStatus.FORBIDDEN;
        } else if (ResultCode.NOT_FOUND.getCode().equals(code)) {
            return HttpStatus.NOT_FOUND;
        } else if (ResultCode.CONFLICT.getCode().equals(code)) {
            return HttpStatus.CONFLICT;
        } else {
            // 其他所有业务错误（认证失败、密码错误、业务校验等）返回200
            // 前端通过响应体中的code字段判断错误类型
            return HttpStatus.OK;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isIntegrationProviderRequest(HttpServletRequest request) {
        String uri = request == null ? null : request.getRequestURI();
        return uri != null && uri.startsWith("/api/v1/integration/providers");
    }

    private boolean isSupplierImportRequest(HttpServletRequest request) {
        String uri = request == null ? null : request.getRequestURI();
        return uri != null && uri.startsWith("/api/v1/scm/suppliers/import");
    }

    private boolean looksLikeJsonIntegrityError(String detail) {
        if (!hasText(detail)) {
            return false;
        }
        String lowerCaseDetail = detail.toLowerCase();
        return lowerCaseDetail.contains("invalid json text")
                || lowerCaseDetail.contains("json value")
                || lowerCaseDetail.contains("json text");
    }

    private String mostSpecificMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
