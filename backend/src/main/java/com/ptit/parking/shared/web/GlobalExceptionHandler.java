package com.ptit.parking.shared.web;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.web.ErrorResponse.FieldViolation;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import com.ptit.parking.shared.ratelimit.TooManyRequestsException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Nơi DUY NHẤT biến exception thành JSON lỗi chuẩn: controller và use case chỉ việc ném lỗi.
 * <p><b>Kế thừa + override (Template Method):</b> lớp cha {@link ResponseEntityExceptionHandler}
 * của Spring đã xử lý sẵn hơn 15 loại lỗi MVC (sai JSON, thiếu tham số, sai đường dẫn...) và gọi
 * các "móc" {@code handleXxx} / {@code handleExceptionInternal}. Ta chỉ override các móc đó để
 * đổi định dạng trả về sang {@link ErrorResponse}, không phải tự bắt lại từng loại lỗi.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ConstraintErrorTranslator constraintErrors;

    public GlobalExceptionHandler(ConstraintErrorTranslator constraintErrors) {
        this.constraintErrors = constraintErrors;
    }

    // ===== Lỗi nghiệp vụ do domain ném ra =====

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Object> handleDomain(DomainException ex) {
        return respond(ErrorResponse.of(ex.errorCode(), TraceIdFilter.currentTraceId()));
    }

        // ===== Vượt giới hạn tần suất: 429 kèm header Retry-After (Đặc tả API mục 11) =====

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Object> handleTooManyRequests(TooManyRequestsException ex) {
        ErrorResponse body = ErrorResponse.of(ErrorCode.TOO_MANY_REQUESTS, TraceIdFilter.currentTraceId());
        return ResponseEntity.status(body.status())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(body);
    }

    // ===== Vi phạm ràng buộc CSDL (UNIQUE, CHECK, FK) =====

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex) {
        String traceId = TraceIdFilter.currentTraceId();
        return constraintErrors.translate(ex)
                .map(error -> error.field() == null
                        ? ErrorResponse.of(error.code(), traceId)
                        : ErrorResponse.validation(traceId,
                                List.of(new FieldViolation(error.field(), FieldErrorCode.NOT_FOUND.name(),
                                        "Giá trị không tồn tại"))))
                .map(this::respond)
                .orElseGet(() -> handleUnexpected(ex));
    }

    // ===== Lỗi không lường trước: ghi log đầy đủ, KHÔNG trả chi tiết cho client =====

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Lỗi không lường trước", ex);
        return respond(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, TraceIdFilter.currentTraceId()));
    }

    // ===== Override các "móc" của lớp cha =====

    /** Body JSON sai ràng buộc (@NotBlank, @Pattern... trên DTO). */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toViolation)
                .toList();
        return respond(ErrorResponse.validation(TraceIdFilter.currentTraceId(), fields));
    }

    /** Tham số đường dẫn / query sai ràng buộc (@Min, @Max... trên tham số method). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> fields = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(),
                                FieldErrorCode.fromConstraint(constraintName(error)).name(),
                                error.getDefaultMessage())))
                .toList();
        return respond(ErrorResponse.validation(TraceIdFilter.currentTraceId(), fields));
    }

    /** Mọi lỗi MVC còn lại lớp cha đã nhận diện: chỉ đổi định dạng theo mã HTTP. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code;
        if (statusCode.value() == 404 || statusCode.value() == 405) {
            code = ErrorCode.NOT_FOUND;                       // đường dẫn / method không tồn tại
        } else if (statusCode.is4xxClientError()) {
            code = ErrorCode.VALIDATION_ERROR;                // JSON hỏng, thiếu tham số, sai kiểu...
        } else {
            log.error("Lỗi MVC phía máy chủ", ex);
            code = ErrorCode.INTERNAL_ERROR;
        }
        return respond(ErrorResponse.of(code, TraceIdFilter.currentTraceId()));
    }

    // ===== Tiện ích =====

    private ResponseEntity<Object> respond(ErrorResponse body) {
        return ResponseEntity.status(body.status()).body(body);
    }

    private static FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(),
                FieldErrorCode.fromConstraint(error.getCode()).name(), error.getDefaultMessage());
    }

    /** Mã cuối cùng trong danh sách codes của Spring là tên annotation, ví dụ "Max". */
    private static String constraintName(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        return codes == null || codes.length == 0 ? "" : codes[codes.length - 1];
    }
}