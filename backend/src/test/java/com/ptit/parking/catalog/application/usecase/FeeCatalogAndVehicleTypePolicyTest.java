package com.ptit.parking.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.catalog.application.port.out.FeeRuleRepository;
import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.catalog.domain.VehicleType;
import com.ptit.parking.parking.application.VehicleTypeDurationFeePolicy;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class FeeCatalogAndVehicleTypePolicyTest {

    private final Map<String, List<FeeRule>> store = new HashMap<>(Map.of(
            "XE_MAY", List.of(new FeeRule(1080, 3000), new FeeRule(1320, 5000), new FeeRule(1440, 15000)),
            "XE_DAP", List.of(new FeeRule(1080, 3000), new FeeRule(1320, 5000), new FeeRule(1440, 15000))));

    private final FeeRuleRepository feeRuleRepo = new FeeRuleRepository() {
        @Override
        public List<FeeRule> allFeeRules() {
            return store.get("XE_MAY");
        }

        @Override
        public List<FeeRule> rulesForVehicleType(String vehicleTypeCode) {
            return store.getOrDefault(vehicleTypeCode, allFeeRules());
        }

        @Override
        public Map<String, List<FeeRule>> allByVehicleType() {
            return Map.copyOf(store);
        }

        @Override
        public void replaceRulesForVehicleType(String vehicleTypeCode, List<FeeRule> rules) {
            store.put(vehicleTypeCode, List.copyOf(rules));
        }
    };

    private final List<VehicleType> types = List.of(
            new VehicleType("XE_DAP", "Xe đạp"),
            new VehicleType("XE_MAY", "Xe máy"));

    private final TransactionRunner tx = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    private final ManageFeeCatalogUseCase useCase = new ManageFeeCatalogUseCase(() -> types, feeRuleRepo, tx);

    private final VehicleOwnerDirectory owners = new VehicleOwnerDirectory() {
        @Override
        public Optional<VehicleOwner> findByPlate(PlateNumber plate) { return Optional.empty(); }

        @Override
        public Optional<VehicleOwner> lockByPlate(PlateNumber plate) { return Optional.empty(); }

        @Override
        public Optional<VehicleOwner> findByVehicleId(long vehicleId) {
            return switch ((int) vehicleId) {
                case 1 -> Optional.of(new VehicleOwner(1L, new PlateNumber("29A12345"), "XE_MAY", 10L, "SV1", "A"));
                case 2 -> Optional.of(new VehicleOwner(2L, new PlateNumber("XD00001"), "XE_DAP", 11L, "SV2", "B"));
                default -> Optional.empty();
            };
        }

        @Override
        public List<PlateSuggestion> suggestPlates(String fragment, boolean parkedOnly, int limit) {
            return List.of();
        }
    };

    private final VehicleTypeDurationFeePolicy policy = new VehicleTypeDurationFeePolicy(feeRuleRepo, owners);

    @Test
    void tinhPhiTheoKhungGio_congDonQuaDem_vaCapNhatNgayKhiSuaBangGia() {
        // Thứ 2 lúc 08:00 giờ VN
        Instant monMorning = ZonedDateTime.parse("2026-11-23T08:00:00+07:00").toInstant();

        // Trước 18:00 cùng ngày -> 3.000
        assertThat(policy.quoteSession(1L, monMorning, ZonedDateTime.parse("2026-11-23T17:59:00+07:00")).points())
                .isEqualTo(3000);
        // Từ 18:00 đến trước 22:00 cùng ngày -> 5.000
        assertThat(policy.quoteSession(1L, monMorning, ZonedDateTime.parse("2026-11-23T19:00:00+07:00")).points())
                .isEqualTo(5000);
        // Qua đêm cộng dồn: gửi Thứ 2, lấy Thứ 4 lúc 19:00 -> 15k + 15k + 5k = 35k
        var wedEvening = policy.quoteSession(1L, monMorning, ZonedDateTime.parse("2026-11-25T19:00:00+07:00"));
        assertThat(wedEvening.points()).isEqualTo(35000);
        assertThat(wedEvening.overnight()).isTrue();
        // Gửi Thứ 2, lấy Thứ 3 lúc 10:00 -> 15k + 3k = 18k
        assertThat(policy.quoteSession(1L, monMorning, ZonedDateTime.parse("2026-11-24T10:00:00+07:00")).points())
                .isEqualTo(18000);

        // Chỉnh giá riêng cho xe đạp: 2k / 4k / 10k
        useCase.update("xe_dap", List.of(
                new FeeRule(1440, 10000),
                new FeeRule(1080, 2000),
                new FeeRule(1320, 4000)));

        assertThat(policy.quoteSession(2L, monMorning, ZonedDateTime.parse("2026-11-23T15:00:00+07:00")).points())
                .isEqualTo(2000);
        assertThat(policy.quoteSession(2L, monMorning, ZonedDateTime.parse("2026-11-25T19:00:00+07:00")).points())
                .isEqualTo(24000); // 10k + 10k + 4k
        assertThat(policy.minimumFeeFor(2L, ZonedDateTime.parse("2026-11-23T15:00:00+07:00"))).isEqualTo(2000);
        assertThat(useCase.view().stream()
                .filter(s -> s.vehicleTypeCode().equals("XE_DAP"))
                .findFirst().orElseThrow().rules())
                .containsExactly(new FeeRule(1080, 2000), new FeeRule(1320, 4000), new FeeRule(1440, 10000));
    }

    @Test
    void tuChoiLoaiXeLaHoacBangGiaKhongHopLe() {
        assertThatThrownBy(() -> useCase.update("XE_TAI", List.of(
                new FeeRule(1080, 3000), new FeeRule(1320, 5000), new FeeRule(1440, 15000))))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);

        assertThatThrownBy(() -> useCase.update("XE_DAP", List.of(new FeeRule(120, 2000), new FeeRule(1440, 5000))))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
