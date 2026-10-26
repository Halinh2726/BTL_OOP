package com.ptit.parking.auth.config;

import com.ptit.parking.auth.application.port.out.PermissionRepository;
import com.ptit.parking.auth.application.port.out.UserRepository;
import com.ptit.parking.auth.application.usecase.GetCurrentUserUseCase;
import com.ptit.parking.auth.application.usecase.LoginUseCase;
import com.ptit.parking.auth.domain.PasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition Root của module auth: nơi DUY NHẤT "lắp" cài đặt thật (JPA, BCrypt) vào use case.
 * Nhờ vậy lớp use case không cần annotation Spring nào — là Java thuần, test bằng {@code new}.
 */
@Configuration
public class AuthUseCaseConfig {

    @Bean
    LoginUseCase loginUseCase(UserRepository users, PermissionRepository permissions, PasswordHasher hasher) {
        return new LoginUseCase(users, permissions, hasher);
    }

    @Bean
    GetCurrentUserUseCase getCurrentUserUseCase(UserRepository users, PermissionRepository permissions) {
        return new GetCurrentUserUseCase(users, permissions);
    }
}
