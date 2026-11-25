package com.ptit.parking.catalog.infrastructure.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.ptit.parking.catalog.application.port.out.ExchangeRateRepository;
import com.ptit.parking.catalog.application.port.out.FeeRuleRepository;
import com.ptit.parking.catalog.application.port.out.VehicleTypeRepository;
import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.catalog.domain.VehicleType;
import com.ptit.parking.payment.domain.ExchangeRate;

/**
 * Adapter cài đặt danh mục bằng SQL thuần (JdbcClient).
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
        List<FeeRule> xeMayRules = jdbc.sql("""
                SELECT up_to_minutes, fee_points
                FROM duration_fee_rules
                WHERE vehicle_type_code = 'XE_MAY'
                ORDER BY up_to_minutes""")
                .query((rs, row) -> new FeeRule(rs.getInt("up_to_minutes"), rs.getInt("fee_points")))
                .list();
        if (!xeMayRules.isEmpty()) {
            return xeMayRules;
        }
        return jdbc.sql("""
                SELECT up_to_minutes, fee_points
                FROM duration_fee_rules
                WHERE vehicle_type_code = (SELECT MIN(vehicle_type_code) FROM duration_fee_rules)
                ORDER BY up_to_minutes""")
                .query((rs, row) -> new FeeRule(rs.getInt("up_to_minutes"), rs.getInt("fee_points")))
                .list();
    }

    @Override
    public List<FeeRule> rulesForVehicleType(String vehicleTypeCode) {
        List<FeeRule> rules = jdbc.sql("""
                SELECT up_to_minutes, fee_points
                FROM duration_fee_rules
                WHERE vehicle_type_code = :code
                ORDER BY up_to_minutes""")
                .param("code", vehicleTypeCode)
                .query((rs, row) -> new FeeRule(rs.getInt("up_to_minutes"), rs.getInt("fee_points")))
                .list();
        return rules.isEmpty() ? allFeeRules() : rules;
    }

    @Override
    public Map<String, List<FeeRule>> allByVehicleType() {
        Map<String, List<FeeRule>> byType = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT vehicle_type_code, up_to_minutes, fee_points
                FROM duration_fee_rules
                ORDER BY vehicle_type_code, up_to_minutes""")
                .query(rs -> {
                    String code = rs.getString("vehicle_type_code");
                    FeeRule rule = new FeeRule(rs.getInt("up_to_minutes"), rs.getInt("fee_points"));
                    byType.computeIfAbsent(code, ignored -> new ArrayList<>()).add(rule);
                });
        return byType;
    }

    @Override
    public void replaceRulesForVehicleType(String vehicleTypeCode, List<FeeRule> rules) {
        jdbc.sql("DELETE FROM duration_fee_rules WHERE vehicle_type_code = :code")
                .param("code", vehicleTypeCode)
                .update();
        for (FeeRule rule : rules) {
            jdbc.sql("""
                    INSERT INTO duration_fee_rules (vehicle_type_code, up_to_minutes, fee_points)
                    VALUES (:code, :upToMinutes, :feePoints)""")
                    .param("code", vehicleTypeCode)
                    .param("upToMinutes", rule.upToMinutes())
                    .param("feePoints", rule.feePoints())
                    .update();
        }
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