package com.ptit.parking.shared.security;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Cấu hình bảo mật (Đặc tả API mục 3, 4):
 * <ul>
 *   <li>Phiên phía máy chủ (cookie SESSION), KHÔNG dùng JWT/localStorage.</li>
 *   <li>CSRF: cookie XSRF-TOKEN đọc được bằng JavaScript, giao diện gửi lại qua header X-XSRF-TOKEN.
 *       Bỏ qua cho login và register vì lúc đó chưa có phiên.</li>
 *   <li>Mỗi API cần đúng mã quyền theo {@link ProtectedEndpoint}; các /api/** còn lại chỉ cần đăng nhập.</li>
 *   <li>Mọi request đã đăng nhập: phiên chưa quá 12 giờ, tài khoản chưa bị khóa — kiểm tra TRƯỚC quyền.</li>
 * </ul>
 * Phân quyền nằm ở backend cho MỌI request, không dựa vào việc giao diện ẩn nút.
 */
@Configuration
public class SecurityConfig {

    public static final String LOGIN_URL = "/api/v1/auth/login";
    public static final String LOGOUT_URL = "/api/v1/auth/logout";
    public static final String REGISTER_URL = "/api/v1/students/register";
    public static final String HEALTH_URL = "/api/v1/health";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonSecurityErrorHandler errors,
                                            AccountStatusLookup accounts, Clock clock) throws Exception {
        http
                .csrf(csrf -> csrf.spa().ignoringRequestMatchers(LOGIN_URL, REGISTER_URL))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(LOGIN_URL, REGISTER_URL, HEALTH_URL).permitAll();
                    for (ProtectedEndpoint endpoint : ProtectedEndpoint.values()) {
                        auth.requestMatchers(endpoint.method(), endpoint.pathPattern())
                                .hasAnyAuthority(endpoint.authorities());
                    }
                    auth.requestMatchers("/api/**").authenticated();
                    auth.anyRequest().permitAll();                     // file giao diện tĩnh
                })
                // Giới hạn tần suất (Đặc tả API mục 11): đăng ký 10 lần/giờ/IP, mỗi phiên 120 request/phút
                .addFilterBefore(new RateLimitFilter(clock, errors), AuthorizationFilter.class)
                // Thứ tự kiểm tra (Đặc tả API mục 4): phiên hợp lệ -> tài khoản không bị khóa -> có quyền
                .addFilterBefore(new SessionLifetimeFilter(clock, errors), AuthorizationFilter.class)
                .addFilterBefore(new ActiveAccountFilter(accounts, errors), AuthorizationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .logout(logout -> logout
                        .logoutUrl(LOGOUT_URL)                         // POST, cần CSRF; gọi lại vẫn 204
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies("SESSION"))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /** Nơi lưu "ai đang đăng nhập": trong HttpSession phía máy chủ. */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}