package com.ptit.parking.shared.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ptit.parking.shared.config.TimeConfig;
import com.ptit.parking.shared.security.AccountStatusLookup.AccountState;
import com.ptit.parking.shared.web.ConstraintErrorTranslator;
import com.ptit.parking.shared.web.GlobalExceptionHandler;
import com.ptit.parking.shared.web.TraceIdFilter;

/**
 * Bộ cấu hình dùng chung cho mọi test tầng web (@WebMvcTest): bảo mật thật, xử lý lỗi thật,
 * đồng hồ thật — chỉ thay việc tra trạng thái tài khoản bằng một lớp giả "luôn hoạt động"
 * để test không cần CSDL. Test nào cần tình huống khác thì ghi đè bằng @MockitoBean.
 */
@TestConfiguration
@Import({SecurityConfig.class, JsonSecurityErrorHandler.class, TimeConfig.class,
        GlobalExceptionHandler.class, ConstraintErrorTranslator.class, TraceIdFilter.class})
public class WebSecurityTestConfig {

    @Bean
    AccountStatusLookup alwaysActiveAccounts() {
        return userId -> AccountState.ACTIVE;
    }
}