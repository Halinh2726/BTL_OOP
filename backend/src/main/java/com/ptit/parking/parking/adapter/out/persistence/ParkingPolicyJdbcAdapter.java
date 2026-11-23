package com.ptit.parking.parking.adapter.out.persistence;

import com.ptit.parking.parking.application.port.out.ParkingPolicyRepository;
import com.ptit.parking.parking.domain.ParkingPolicyConfig;
import java.time.LocalTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cài đặt {@link ParkingPolicyRepository} đọc/ghi cột cấu hình giờ khóa & phí qua đêm trong system_config. */
@Component
class ParkingPolicyJdbcAdapter implements ParkingPolicyRepository {

    private final JdbcClient jdbc;

    ParkingPolicyJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ParkingPolicyConfig current() {
        return jdbc.sql("""
                SELECT checkout_curfew_time, overnight_fee_points, curfew_enabled
                FROM system_config
                WHERE id = 1""")
                .query((rs, row) -> new ParkingPolicyConfig(
                        rs.getObject("checkout_curfew_time", LocalTime.class),
                        rs.getLong("overnight_fee_points"),
                        rs.getBoolean("curfew_enabled")))
                .optional()
                .orElse(ParkingPolicyConfig.DEFAULT);
    }

    @Override
    public ParkingPolicyConfig save(ParkingPolicyConfig config) {
        jdbc.sql("""
                UPDATE system_config
                SET checkout_curfew_time = :curfewTime,
                    overnight_fee_points = :overnightFee,
                    curfew_enabled = :curfewEnabled
                WHERE id = 1""")
                .param("curfewTime", config.checkoutCurfewTime())
                .param("overnightFee", config.overnightFeePoints())
                .param("curfewEnabled", config.curfewEnabled())
                .update();
        return current();
    }
}
