package com.example.aitools.exception;

import com.example.aitools.common.ResultCode;
import lombok.extern.slf4j.Slf4j;

/**
 * 业务异常工厂。
 * <p>
 * <b>解决的问题</b>：直接把底层异常的 {@code e.getMessage()} 拼进返回给前端的提示，
 * 会把服务器路径、SDK 错误码、SQL 片段、类名等技术细节泄露给用户（CWE-209），
 * 且用户看不懂。
 * <p>
 * <b>约定</b>：
 * <ul>
 *   <li>返回给前端的 {@code message} —— 面向用户的可读文案，取自 {@link ResultCode}，
 *       不拼接任何原始异常信息</li>
 *   <li>原始异常（含堆栈）—— 只写服务端日志，供排查</li>
 * </ul>
 * <p>
 * <b>用法</b>：
 * <pre>
 *   // 带底层异常：日志记根因，前端只看到通用文案
 *   catch (Exception e) {
 *       throw ErrorFactory.of(ResultCode.OCR_FAILED, e);
 *   }
 *
 *   // 不带底层异常：仅指定错误码对应的文案
 *   if (file.isEmpty()) {
 *       throw ErrorFactory.of(ResultCode.FILE_TOO_LARGE);
 *   }
 *
 *   // 需要自定义用户文案时（文案须面向用户，禁止拼接 e.getMessage()）
 *   throw ErrorFactory.of(ResultCode.PARAM_ERROR, "请先选择要上传的文件");
 * </pre>
 */
@Slf4j
public final class ErrorFactory {

    private ErrorFactory() {}

    /**
     * 业务异常（带底层异常）：底层异常只进日志，前端拿到错误码对应的通用文案。
     *
     * @param resultCode 错误码（同时决定返回给前端的文案）
     * @param cause      原始异常，仅用于日志记录
     */
    public static BusinessException of(ResultCode resultCode, Throwable cause) {
        log.error("[business-error] code={} msg={}", resultCode.getCode(), resultCode.getMessage(), cause);
        return new BusinessException(resultCode.getCode(), resultCode.getMessage());
    }

    /**
     * 业务异常：使用错误码自带的文案。
     */
    public static BusinessException of(ResultCode resultCode) {
        log.warn("[business-error] code={} msg={}", resultCode.getCode(), resultCode.getMessage());
        return new BusinessException(resultCode.getCode(), resultCode.getMessage());
    }

    /**
     * 业务异常（自定义用户文案）：文案必须面向用户、可读，禁止拼接原始异常信息。
     *
     * @param resultCode 错误码
     * @param userMessage 面向用户的可读文案
     */
    public static BusinessException of(ResultCode resultCode, String userMessage) {
        log.warn("[business-error] code={} msg={}", resultCode.getCode(), userMessage);
        return new BusinessException(resultCode.getCode(), userMessage);
    }
}
