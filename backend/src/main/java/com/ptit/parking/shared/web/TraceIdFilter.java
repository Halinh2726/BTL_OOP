package com.ptit.parking.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gắn mã truy vết (traceId) cho MỌI request (Đặc tả API mục 11):
 * lấy từ header {@code X-Request-Id} nếu hợp lệ, không thì tự sinh; trả lại trong header
 * và đưa vào MDC để mọi dòng log của request đó đều có traceId.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "traceId";

    // Chỉ nhận ký tự an toàn: chặn chèn ký tự xuống dòng giả mạo log
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);              // luồng xử lý được tái sử dụng: phải dọn
        }
    }

    /** traceId của request đang xử lý (dùng khi trả lỗi). */
    public static String currentTraceId() {
        String traceId = MDC.get(MDC_KEY);
        return traceId != null ? traceId : newTraceId();
    }

    static String resolve(String incoming) {
        return incoming != null && SAFE_TRACE_ID.matcher(incoming).matches() ? incoming : newTraceId();
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}