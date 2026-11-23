package com.ptit.parking.payment.application.usecase;

import com.ptit.parking.payment.application.port.out.BankConfigRepository;
import com.ptit.parking.payment.application.usecase.GetBankConfigUseCase.ActiveBankConfig;
import com.ptit.parking.payment.application.usecase.GetBankConfigUseCase.Source;
import com.ptit.parking.payment.domain.BankAccountConfig;
import java.util.Objects;

/**
 * Cho phép Quản lý cập nhật tài khoản ngân hàng nhận tiền VietQR trực tiếp trên giao diện,
 * hoặc khôi phục về cấu hình mặc định trong .env.
 */
public class UpdateBankConfigUseCase {

    public record Command(String bankId, String accountNo, String accountName, String template) {}

    private final BankConfigRepository repository;
    private final GetBankConfigUseCase getBankConfig;

    public UpdateBankConfigUseCase(BankConfigRepository repository, GetBankConfigUseCase getBankConfig) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.getBankConfig = Objects.requireNonNull(getBankConfig, "getBankConfig");
    }

    public ActiveBankConfig update(Command command, long managerUserId) {
        BankAccountConfig config = new BankAccountConfig(
                command.bankId(),
                command.accountNo(),
                command.accountName(),
                command.template());
        BankConfigRepository.SavedBankConfig saved = repository.save(config, managerUserId);
        return new ActiveBankConfig(saved.config(), Source.DATABASE, saved.updatedAt(), getBankConfig.envDefault());
    }

    public ActiveBankConfig resetToDefault() {
        repository.deleteSaved();
        return getBankConfig.current();
    }
}
