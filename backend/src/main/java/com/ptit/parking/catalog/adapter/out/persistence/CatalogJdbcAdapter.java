package com.ptit.parking.catalog.adapter.out.persistence;

import java.time.LocalTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.ptit.parking.catalog.application.port.out.ExchangeRateRepository;
import com.ptit.parking.catalog.application.port.out.FeeRuleRepository;
import com.ptit.parking.catalog.application.port.out.VehicleTypeRepository;
import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.catalog.domain.VehicleType;
import com.ptit.parking.payment.domain.ExchangeRate;

/**
 * Adapter cài đặt 3 port danh mục bằng SQL thuần (JdbcClient). Danh mục chỉ ĐỌC, không có vòng đời
 * phức tạp nên không cần entity JPA — tầng application không biết và không cần biết điều này.
 */
@Component
class CatalogJdbcAdapter implements VehicleTypeRepository, FeeRuleRepository, ExchangeRateRepository {

    private final JdbcClient jdbc;

    CatalogJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<VehicleType> allVehicleTypes() {
        return jdbc.sql("SELECT code, name FROM vehicle_types ORDER BY name")
                .query((rs, row) -> new VehicleType(rs.getString("code"), rs.getString("name")))
                .list();
    }

    @Override
    public List<FeeRule> allFeeRules() {
        return jdbc.sql("SELECT start_time, fee_points FROM fee_rules")
                .query((rs, row) -> new FeeRule(rs.getObject("start_time", LocalTime.class), rs.getInt("fee_points")))
                .list();
    }

    /** Chỉ đọc cột cash_per_point; cột receiving_wallet_id không bao giờ rời khỏi CSDL qua đây (R06). */
    @Override
    public ExchangeRate currentExchangeRate() {
        int cashPerPoint = jdbc.sql("SELECT cash_per_point FROM system_config WHERE id = 1")
                .query(Integer.class)
                .single();
        return new ExchangeRate(cashPerPoint);
    }
}