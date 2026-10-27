package com.ptit.parking.catalog.application.usecase;

import com.ptit.parking.catalog.application.port.out.ExchangeRateRepository;
import com.ptit.parking.catalog.application.port.out.FeeRuleRepository;
import com.ptit.parking.catalog.application.port.out.VehicleTypeRepository;
import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.catalog.domain.VehicleType;
import com.ptit.parking.payment.domain.ExchangeRate;
import java.util.List;

/**
 * Đặc tả API #9, #10, #11 — danh mục chỉ đọc cho mọi người đã đăng nhập:
 * sinh viên chọn loại xe khi đăng ký (SV02), xem bảng giá (SV03, BV01); quản lý xem tỷ lệ quy đổi (QL01, QL02).
 */
public class GetCatalogUseCase {

    private final VehicleTypeRepository vehicleTypes;
    private final FeeRuleRepository feeRules;
    private final ExchangeRateRepository exchangeRates;

    public GetCatalogUseCase(VehicleTypeRepository vehicleTypes, FeeRuleRepository feeRules,
                             ExchangeRateRepository exchangeRates) {
        this.vehicleTypes = vehicleTypes;
        this.feeRules = feeRules;
        this.exchangeRates = exchangeRates;
    }

    public List<VehicleType> vehicleTypes() {
        return vehicleTypes.allVehicleTypes();
    }

    /** API #10 bước 1: sắp xếp theo startTime tăng dần — dùng thứ tự tự nhiên của {@link FeeRule}. */
    public List<FeeRule> feeRules() {
        return feeRules.allFeeRules().stream().sorted().toList();
    }

    public ExchangeRate exchangeRate() {
        return exchangeRates.currentExchangeRate();
    }
}