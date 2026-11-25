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
            "XE_MAY", List.of(new FeeRule(120, 3000), new FeeRule(360, 5000), new FeeRule(1440, 7000)),
            "XE_DAP", List.of(new FeeRule(120, 1500), new FeeRule(360, 2500), new FeeRule(1440, 4000))));

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
    void tinhPhiTheoLoaiXe_vaCapNhatNgayKhiSuaBangGia() {
        ZonedDateTime exit = ZonedDateTime.parse("2026-11-25T10:00:00+07:00");
        Instant entered1hAgo = exit.minusMinutes(60).toInstant();

        assertThat(policy.quote(1L, entered1hAgo, exit).points()).isEqualTo(3000);
        assertThat(policy.quote(2L, entered1hAgo, exit).points()).isEqualTo(1500);
        assertThat(policy.minimumFeeFor(2L, exit)).isEqualTo(1500);

        useCase.update("xe_dap", List.of(
                new FeeRule(1440, 5000),
                new FeeRule(120, 2000)));

        assertThat(policy.quote(2L, entered1hAgo, exit).points()).isEqualTo(2000);
        assertThat(policy.minimumFeeFor(2L, exit)).isEqualTo(2000);
        assertThat(useCase.view().stream()
                .filter(s -> s.vehicleTypeCode().equals("XE_DAP"))
                .findFirst().orElseThrow().rules())
                .containsExactly(new FeeRule(120, 2000), new FeeRule(1440, 5000));
    }

    @Test
    void tuChoiLoaiXeLaHoacBangGiaKhongHopLe() {
        assertThatThrownBy(() -> useCase.update("XE_TAI", List.of(new FeeRule(1440, 5000))))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);

        assertThatThrownBy(() -> useCase.update("XE_DAP", List.of(new FeeRule(120, 2000))))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
