package com.ptit.parking.parking.domain;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;

/** Lượt gửi chỉ đổi trạng thái qua open/close — không bao giờ "nửa vào nửa ra". */
class ParkingSessionTest {

    private static final Instant ENTER = Instant.parse("2026-10-27T00:55:00.123456789Z");
    private static final Instant EXIT = Instant.parse("2026-10-27T11:00:00Z");
    private static final long VEHICLE = 100L;
    private static final long GUARD_IN = 4L;
    private static final long GUARD_OUT = 5L;

    @Test
    void open_dangGui_chuaCoGioRa_gioVaoCatToiMicroGiay() {
        ParkingSession session = ParkingSession.open(VEHICLE, GUARD_IN, ENTER);

        assertThat(session.id()).isNull();
        assertThat(session.isOpen()).isTrue();
        assertThat(session.status()).isEqualTo(SessionStatus.DANG_GUI);
        assertThat(session.vehicleId()).isEqualTo(VEHICLE);
        assertThat(session.entryGuardId()).isEqualTo(GUARD_IN);
        assertThat(session.enteredAt()).isEqualTo(Instant.parse("2026-10-27T00:55:00.123456Z"));   // khớp DATETIME(6)
        assertThat(session.exitedAt()).isNull();
        assertThat(session.exitGuardId()).isNull();
    }

    @Test
    void close_daRa_ghiGioRaVaBaoVeChoRa_cungLuc() {
        ParkingSession session = ParkingSession.open(VEHICLE, GUARD_IN, ENTER);

        session.close(GUARD_OUT, EXIT);

        assertThat(session.isOpen()).isFalse();
        assertThat(session.status()).isEqualTo(SessionStatus.DA_RA);
        assertThat(session.exitedAt()).isEqualTo(EXIT);
        assertThat(session.exitGuardId()).isEqualTo(GUARD_OUT);
    }

    @Test
    void closeLanHai_baoKhongCoLuotDangMo_giuNguyenLanRaDau() {
        ParkingSession session = ParkingSession.open(VEHICLE, GUARD_IN, ENTER);
        session.close(GUARD_OUT, EXIT);

        assertThatThrownBy(() -> session.close(6L, EXIT.plusSeconds(3600)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NO_OPEN_SESSION));
        assertThat(session.exitGuardId()).isEqualTo(GUARD_OUT);
        assertThat(session.exitedAt()).isEqualTo(EXIT);
    }

    @Test
    void gioRaTruocGioVao_khongDongDuoc_vanDangGui() {
        ParkingSession session = ParkingSession.open(VEHICLE, GUARD_IN, ENTER);

        assertThatThrownBy(() -> session.close(GUARD_OUT, Instant.parse("2026-10-27T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(session.isOpen()).isTrue();
        assertThat(session.exitedAt()).isNull();
    }

    @Test
    void withId_banSaoCoId_giuNguyenNoiDung() {
        ParkingSession session = ParkingSession.open(VEHICLE, GUARD_IN, ENTER).withId(501L);

        assertThat(session.id()).isEqualTo(501L);
        assertThat(session.isOpen()).isTrue();
        assertThat(session.vehicleId()).isEqualTo(VEHICLE);
        assertThat(session.entryGuardId()).isEqualTo(GUARD_IN);
    }
}
