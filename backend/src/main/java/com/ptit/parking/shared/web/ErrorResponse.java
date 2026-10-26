package com.ptit.parking.shared.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ptit.parking.shared.domain.ErrorCode;

/**
 * Cấu trúc lỗi THỐNG NHẤT cho mọi API (Đặc tả API mục 5):
 * {@code {code, message, status, traceId, fields}}. {@code fields} chỉ có với VALIDATION_ERROR.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, int status, String traceId, List<FieldViolation> fields) {

    public static ErrorResponse of(ErrorCode errorCode, String traceId) {
        return new ErrorResponse(errorCode.name(), errorCode.defaultMessage(), errorCode.httpStatus(), traceId, null);
    }

    public static ErrorResponse validation(String traceId, List<FieldViolation> fields) {
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return new ErrorResponse(code.name(), code.defaultMessage(), code.httpStatus(), traceId, fields);
    }

    /** Lỗi của một trường: code là REQUIRED, PATTERN, LENGTH, RANGE hoặc NOT_FOUND. */
    public record FieldViolation(String field, String code, String message) {}
}