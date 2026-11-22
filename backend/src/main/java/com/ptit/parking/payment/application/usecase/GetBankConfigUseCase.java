package com.ptit.parking.payment.application.usecase;

import com.ptit.parking.payment.application.port.out.BankConfigRepository;
import com.ptit.parking.payment.domain.BankAccountConfig;
import java.time.Instant;
import java.util.Objects;

/**
 * Lấy cấu hình ngân hàng đang áp dụng cho VietQR:
 * ưu tiên cấu hình Quản lý đã lưu trong CSDL; nếu chưa lưu thì dùng mặc định từ .env / application.yml.
 */
public class GetBankConfigUseCase {

    public enum Source {
        DATABASE,
        ENV
    }

    public record ActiveBankConfig(
            BankAccountConfig config,
            Source source,
            Instant updatedAt,
            BankAccountConfig envDefault
    ) {}

    private final BankConfigRepository repository;
    private final BankAccountConfig envDefault;

    public GetBankConfigUseCase(BankConfigRepository repository, BankAccountConfig envDefault) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.envDefault = Objects.requireNonNull(envDefault, "envDefault");
    }

    public ActiveBankConfig current() {
        return repository.findSaved()
                .map(saved -> new ActiveBankConfig(saved.config(), Source.DATABASE, saved.updatedAt(), envDefault))
                .orElseGet(() -> new ActiveBankConfig(envDefault, Source.ENV, null, envDefault));
    }

    public BankAccountConfig currentConfig() {
        return current().config();
    }

    public BankAccountConfig envDefault() {
        return envDefault;
    }
}
