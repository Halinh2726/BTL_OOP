package com.ptit.parking.gate.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Đợt 4b — phân ca: giờ ca (ca đêm sang hôm sau), xếp / gỡ, đối chiếu đúng giờ / đi muộn / vắng. */
class ShiftAssignmentTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    /** Giờ Việt Nam trong ngày DAY. */
    private static Instant at(int hour, int minute) {
        return DAY.atTime(hour, minute).atZone(VN).toInstant();
    }

    private static ShiftAssignment morning() {
        return ShiftAssignment.restore(1L, DAY, ShiftSlot.SANG, 4L, 1L, at(0, 0));
    }

    @Test
    void gioCa_caDemKetThucSangHomSau() {
        assertThat(ShiftSlot.SANG.startsAt(DAY, VN)).isEqualTo(at(6, 0));
        assertThat(ShiftSlot.CHIEU.endsAt(DAY, VN)).isEqualTo(at(22, 0));
        assertThat(ShiftSlot.DEM.startsAt(DAY, VN)).isEqualTo(at(22, 0));
        assertThat(ShiftSlot.DEM.endsAt(DAY, VN)).isEqualTo(DAY.plusDays(1).atTime(6, 0).atZone(VN).toInstant());
    }

    @Test
    void khongXepCaDaKetThuc_khongGoCaDaBatDau() {
        assertThatThrownBy(() -> ShiftAssignment.plan(DAY, ShiftSlot.SANG, 4L, 1L, at(14, 0), VN))
                .extracting(e -> ((DomainException) e).errorCode()).isEqualTo(ErrorCode.SHIFT_SLOT_PASSED);
        ShiftAssignment running = ShiftAssignment.plan(DAY, ShiftSlot.SANG, 4L, 1L, at(10, 0), VN);   // ca đang diễn ra: vẫn xếp bổ sung được
        assertThat(running.guardId()).isEqualTo(4L);
        assertThatThrownBy(() -> running.requireRemovable(at(10, 0), VN))
                .extracting(e -> ((DomainException) e).errorCode()).isEqualTo(ErrorCode.SHIFT_SLOT_PASSED);
        ShiftAssignment.plan(DAY, ShiftSlot.CHIEU, 4L, 1L, at(10, 0), VN).requireRemovable(at(10, 0), VN);   // chưa bắt đầu: gỡ được
    }

    @Test
    void doiChieu_dungGio_diMuon_sapToi_chuaNhanCa_vang() {
        ShiftAssignment a = morning();
        assertThat(a.attendance(List.of(at(5, 30)), at(12, 0), VN).status()).isEqualTo(AttendanceStatus.DUNG_GIO);   // sớm 30 phút
        assertThat(a.attendance(List.of(at(6, 15)), at(12, 0), VN).status()).isEqualTo(AttendanceStatus.DUNG_GIO);   // muộn đúng 15 phút
        ShiftAssignment.Attendance late = a.attendance(List.of(at(6, 40)), at(12, 0), VN);
        assertThat(late.status()).isEqualTo(AttendanceStatus.DI_MUON);
        assertThat(late.lateMinutes()).isEqualTo(40);
        assertThat(late.actualStart()).isEqualTo(at(6, 40));

        assertThat(a.attendance(List.of(), at(5, 0), VN).status()).isEqualTo(AttendanceStatus.SAP_TOI);
        assertThat(a.attendance(List.of(), at(9, 0), VN).status()).isEqualTo(AttendanceStatus.CHUA_NHAN_CA);
        assertThat(a.attendance(List.of(), at(14, 0), VN).status()).isEqualTo(AttendanceStatus.VANG);
    }

    @Test
    void doiChieu_chiTinhLanNhanCaTrongKhungCuaCaDo() {
        ShiftAssignment a = morning();
        // Nhận ca lúc 4:00 (sớm hơn 60 phút) và 14:30 (ca chiều) đều không tính cho ca sáng
        assertThat(a.attendance(List.of(at(4, 0), at(14, 30)), at(15, 0), VN).status()).isEqualTo(AttendanceStatus.VANG);
        // Có nhiều lần nhận ca trong khung: lấy lần sớm nhất
        assertThat(a.attendance(List.of(at(8, 0), at(6, 5)), at(15, 0), VN).actualStart()).isEqualTo(at(6, 5));
    }
}
