package com.ptit.parking.payment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.payment.application.port.out.BankConfigRepository;
import com.ptit.parking.payment.application.usecase.GetBankConfigUseCase.Source;
import com.ptit.parking.payment.domain.BankAccountConfig;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BankConfigUseCaseTest {

    private static final BankAccountConfig ENV_DEFAULT =
            new BankAccountConfig("MB", "0987654321", "BAI XE HOC VIEN PTIT", "compact2");

    private final InMemoryBankConfigRepository repo = new InMemoryBankConfigRepository();
    private final GetBankConfigUseCase getUseCase = new GetBankConfigUseCase(repo, ENV_DEFAULT);
    private final UpdateBankConfigUseCase updateUseCase = new UpdateBankConfigUseCase(repo, getUseCase);

    @Test
    void chuaLuuTrongDb_traVeMacDinhEnv() {
        var active = getUseCase.current();

        assertThat(active.source()).isEqualTo(Source.ENV);
        assertThat(active.config()).isEqualTo(ENV_DEFAULT);
        assertThat(active.updatedAt()).isNull();
    }

    @Test
    void quanLyCapNhat_luuChuanHoaVaTraVeTuDatabase() {
        var updated = updateUseCase.update(
                new UpdateBankConfigUseCase.Command(" vcb ", "0123-456 789", "  nguyen   van a ", "compact"), 1L);

        assertThat(updated.source()).isEqualTo(Source.DATABASE);
        assertThat(updated.config().bankId()).isEqualTo("VCB");
        assertThat(updated.config().accountNo()).isEqualTo("0123456789");
        assertThat(updated.config().accountName()).isEqualTo("NGUYEN VAN A");
        assertThat(updated.config().template()).isEqualTo("compact");
        assertThat(getUseCase.currentConfig().accountNo()).isEqualTo("0123456789");
    }

    @Test
    void khoiPhucMacDinh_xoaBanGhiDbVaQuayLaiEnv() {
        updateUseCase.update(new UpdateBankConfigUseCase.Command("ACB", "11223344", "CHU BAI XE", "qr_only"), 1L);
        var reset = updateUseCase.resetToDefault();

        assertThat(reset.source()).isEqualTo(Source.ENV);
        assertThat(reset.config()).isEqualTo(ENV_DEFAULT);
    }

    @Test
    void duLieuSaiDinhDang_nemLoi() {
        assertThatThrownBy(() -> new BankAccountConfig("M", "123456", "TEN", "compact2"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BankAccountConfig("MB", "12", "TEN", "compact2"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BankAccountConfig("MB", "123456", "TEN", "invalid_tpl"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static class InMemoryBankConfigRepository implements BankConfigRepository {
        private SavedBankConfig saved;

        @Override
        public Optional<SavedBankConfig> findSaved() {
            return Optional.ofNullable(saved);
        }

        @Override
        public SavedBankConfig save(BankAccountConfig config, long updatedByUserId) {
            saved = new SavedBankConfig(config, Instant.parse("2026-11-22T15:00:00Z"));
            return saved;
        }

        @Override
        public void deleteSaved() {
            saved = null;
        }
    }
}
