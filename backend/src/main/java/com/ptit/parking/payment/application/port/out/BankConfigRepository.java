package com.ptit.parking.payment.application.port.out;

import com.ptit.parking.payment.domain.BankAccountConfig;
import java.time.Instant;
import java.util.Optional;

/** Port lưu trữ cấu hình tài khoản ngân hàng nhận tiền (tùy chỉnh bởi Quản lý). */
public interface BankConfigRepository {

    record SavedBankConfig(BankAccountConfig config, Instant updatedAt) {}

    Optional<SavedBankConfig> findSaved();

    SavedBankConfig save(BankAccountConfig config, long updatedByUserId);

    void deleteSaved();
}
