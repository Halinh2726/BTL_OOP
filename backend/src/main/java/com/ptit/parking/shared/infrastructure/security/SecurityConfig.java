package com.ptit.parking.shared.infrastructure.security;

import java.time.Clock;

import com.ptit.parking.auth.presentation.web.SocialLoginClients;
import com.ptit.parking.auth.presentation.web.SocialLoginSecurity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
    public static final String SOCIAL_REGISTER_URL = SocialLoginClients.BASE + "/register";
    public static final String HEALTH_URL = "/api/v1/health";
    public static final String WEBHOOK_URL = "/api/v1/webhooks/**";
    /** Quên mật khẩu (yêu cầu mã + đặt lại): chưa đăng nhập nên không có phiên, không có CSRF token. */
    public static final String PASSWORD_RESET_URL = "/api/v1/auth/password-reset";
    public static final String PASSWORD_RESET_CONFIRM_URL = "/api/v1/auth/password-reset/confirm";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonSecurityErrorHandler errors,
                                            AccountStatusLookup accounts, CredentialStateLookup credentials,
                                            Clock clock, ObjectProvider<SocialLoginSecurity> socialLogin) throws Exception {
        http
                .csrf(csrf -> csrf.spa().ignoringRequestMatchers(LOGIN_URL, REGISTER_URL, WEBHOOK_URL,
                        PASSWORD_RESET_URL, PASSWORD_RESET_CONFIRM_URL))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(LOGIN_URL, REGISTER_URL, HEALTH_URL, WEBHOOK_URL,
                            PASSWORD_RESET_URL, PASSWORD_RESET_CONFIRM_URL).permitAll();
                    auth.requestMatchers(HttpMethod.GET, SocialLoginClients.BASE + "/status",
                            SocialLoginClients.START + "/*", SocialLoginClients.CALLBACK + "/*").permitAll();
                    auth.requestMatchers(HttpMethod.POST, SOCIAL_REGISTER_URL).permitAll();
                    auth.requestMatchers(SocialLoginClients.MANAGER_CONFIG, SocialLoginClients.MANAGER_CONFIG + "/*")
                            .hasRole("QUAN_LY");
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
                // Đổi mật khẩu -> phiên cũ hết hiệu lực; mật khẩu tạm -> phải đổi trước khi làm việc khác
                .addFilterBefore(new CredentialFreshnessFilter(credentials, errors), AuthorizationFilter.class)
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
        SocialLoginSecurity social = socialLogin.getIfAvailable();
        if (social != null) {
            social.configure(http);
        }
        return http.build();
    }

    /** Nơi lưu "ai đang đăng nhập": trong HttpSession phía máy chủ. */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
