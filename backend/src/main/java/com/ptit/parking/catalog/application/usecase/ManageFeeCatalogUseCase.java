package com.ptit.parking.catalog.application.usecase;

import com.ptit.parking.catalog.application.port.out.FeeRuleRepository;
import com.ptit.parking.catalog.application.port.out.VehicleTypeRepository;
import com.ptit.parking.catalog.application.usecase.GetCatalogUseCase.VehicleTypeFeeSchedule;
import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.catalog.domain.CustomerType;
import com.ptit.parking.catalog.domain.VehicleType;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Quản lý sửa bảng giá gửi xe theo lượt cho từng loại xe (chỉ Quản lý). */
public class ManageFeeCatalogUseCase {

    private final VehicleTypeRepository vehicleTypes;
    private final FeeRuleRepository feeRules;
    private final TransactionRunner transactions;

    public ManageFeeCatalogUseCase(VehicleTypeRepository vehicleTypes, FeeRuleRepository feeRules,
                                   TransactionRunner transactions) {
        this.vehicleTypes = vehicleTypes;
        this.feeRules = feeRules;
        this.transactions = transactions;
    }

    public List<VehicleTypeFeeSchedule> view() {
        return view(CustomerType.SINH_VIEN);
    }

    public List<VehicleTypeFeeSchedule> view(CustomerType customer) {
        Map<String, List<FeeRule>> byType = feeRules.allByVehicleType(CustomerType.SINH_VIEN);
        List<FeeRule> fallback = feeRules.allFeeRules();
        return vehicleTypes.allVehicleTypes().stream()
                .map(type -> {
                    List<FeeRule> raw = byType.getOrDefault(type.code(), List.of());
                    List<FeeRule> sorted = (raw.isEmpty() ? fallback : raw).stream().sorted().toList();
                    return new VehicleTypeFeeSchedule(type.code(), type.name(), sorted);
                })
                .toList();
    }

    public List<VehicleTypeFeeSchedule> update(String rawVehicleTypeCode, List<FeeRule> rawRules) {
        return update(CustomerType.SINH_VIEN, rawVehicleTypeCode, rawRules);
    }

    public List<VehicleTypeFeeSchedule> update(CustomerType customer, String rawVehicleTypeCode, List<FeeRule> rawRules) {
        String code = rawVehicleTypeCode == null ? "" : rawVehicleTypeCode.trim().toUpperCase(Locale.ROOT);
        VehicleType target = vehicleTypes.allVehicleTypes().stream()
                .filter(type -> type.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_ERROR, "Loại xe không tồn tại"));

        List<FeeRule> validated;
        try {
            validated = FeeRule.validateSchedule(rawRules);
        } catch (IllegalArgumentException ex) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }

        transactions.inTransaction(() -> {
            feeRules.replaceRulesForVehicleType(CustomerType.SINH_VIEN, target.code(), validated);
            return null;
        });
        return view(CustomerType.SINH_VIEN);
    }
}
