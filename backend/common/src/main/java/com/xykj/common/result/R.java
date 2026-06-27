package com.xykj.common.result;

import com.xykj.common.enums.ResultCode;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一响应体
 * 符合API文档规范：
 * {
 *   "code": "SUCCESS",
 *   "message": "操作成功",
 *   "data": {},
 *   "traceId": "xxx",
 *   "timestamp": "2026-03-16 18:00:00"
 * }
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 业务状态码
     */
    private String code;

    /**
     * 提示信息
     */
    private String message;

    /**
     * 业务数据
     */
    private T data;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 服务端响应时间
     */
    private String timestamp;

    public R() {
        this.timestamp = LocalDateTime.now().format(FORMATTER);
    }

    public R(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now().format(FORMATTER);
    }

    public R(String code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
        this.timestamp = LocalDateTime.now().format(FORMATTER);
    }

    // ==================== 成功响应 ====================

    public static <T> R<T> ok() {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> R<T> ok(String message, T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    // ==================== 失败响应 ====================

    public static <T> R<T> fail() {
        return new R<>(ResultCode.INTERNAL_ERROR.getCode(), ResultCode.INTERNAL_ERROR.getMessage(), null);
    }

    public static <T> R<T> fail(String message) {
        return new R<>(ResultCode.INTERNAL_ERROR.getCode(), message, null);
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        return new R<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> R<T> fail(ResultCode resultCode, String message) {
        return new R<>(resultCode.getCode(), message, null);
    }

    public static <T> R<T> fail(String code, String message) {
        return new R<>(code, message, null);
    }

    // ==================== 常用响应 ====================

    public static <T> R<T> badRequest(String message) {
        return new R<>(ResultCode.BAD_REQUEST.getCode(), message, null);
    }

    public static <T> R<T> unauthorized() {
        return new R<>(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMessage(), null);
    }

    public static <T> R<T> forbidden() {
        return new R<>(ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMessage(), null);
    }

    public static <T> R<T> notFound(String message) {
        return new R<>(ResultCode.NOT_FOUND.getCode(), message, null);
    }

    public static <T> R<T> conflict(String message) {
        return new R<>(ResultCode.CONFLICT.getCode(), message, null);
    }

    public static <T> R<T> validationFailed(String message) {
        return new R<>(ResultCode.VALIDATION_FAILED.getCode(), message, null);
    }

    // ==================== 链式设置 ====================

    public R<T> traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    public R<T> data(T data) {
        this.data = data;
        return this;
    }

    public R<T> message(String message) {
        this.message = message;
        return this;
    }
}