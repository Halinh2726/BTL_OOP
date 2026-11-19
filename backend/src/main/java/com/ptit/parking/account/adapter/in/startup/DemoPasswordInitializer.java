package com.ptit.parking.account.adapter.in.startup;

import com.ptit.parking.account.application.usecase.ApplyDemoPasswordUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter "đầu vào" lúc khởi động (cùng vai trò với controller, chỉ khác nguồn kích hoạt): khi cấu hình
 * {@code parking.demo.password} (profile {@code demo}, xem application-demo.yml), đặt mật khẩu đó cho mọi tài khoản.
 * <p>Không có cấu hình này thì bean KHÔNG được tạo — chạy bình thường hay chạy test đều không bị ảnh hưởng.
 */
@Component
@ConditionalOnProperty(name = "parking.demo.password")
class DemoPasswordInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoPasswordInitializer.class);

    private final ApplyDemoPasswordUseCase applyDemoPassword;
    private final String demoPassword;

    DemoPasswordInitializer(ApplyDemoPasswordUseCase applyDemoPassword,
                            @Value("${parking.demo.password}") String demoPassword) {
        this.applyDemoPassword = applyDemoPassword;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        int accounts = applyDemoPassword.apply(demoPassword);
        log.warn("[DEMO] Đã đặt mật khẩu demo cho {} tài khoản. KHÔNG bật profile demo khi chạy thật.", accounts);
    }
}
