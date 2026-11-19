package com.ptit.parking.gate.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Đợt 2 — vòng đời ca trực, sự cố, ảnh đính kèm, quy tắc "gửi quá lâu". */
class GateDomainTest {

    private static final Instant NOW = Instant.parse("2026-11-20T01:00:00Z");
    private static final ShiftSummary SUMMARY = new ShiftSummary(12, 9, 30, 1);

    private static void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(expected));
    }

    @Nested
    class CaTruc {

        @Test
        void nhanCa_ketThucCa_coSoLieu_choNguoiKhacNhan() {
            GuardShift shift = GuardShift.start(4L, new CashOnHand(200_000), null, NOW).withId(1L);
            assertThat(shift.isOpen()).isTrue();

            shift.end(SUMMARY, new CashOnHand(350_000), "  Cổng sau hỏng đèn  ", NOW.plusSeconds(8 * 3600));
            assertThat(shift.isAwaitingReceipt()).isTrue();
            assertThat(shift.summary()).isEqualTo(SUMMARY);
            assertThat(shift.handoverNote()).isEqualTo("Cổng sau hỏng đèn");

            shift.receive(6L, NOW.plusSeconds(8 * 3600 + 60));
            assertThat(shift.receivedBy()).isEqualTo(6L);
            assertThat(shift.isAwaitingReceipt()).isFalse();
        }

        @Test
        void khongKetThucHaiLan_khongTuNhanCaCuaMinh_khongNhanLai() {
            GuardShift shift = GuardShift.start(4L, new CashOnHand(200_000), null, NOW).withId(1L);
            assertCode(() -> shift.receive(6L, NOW), ErrorCode.HANDOVER_PENDING);       // chưa kết thúc

            shift.end(SUMMARY, CashOnHand.ZERO, null, NOW.plusSeconds(60));
            assertCode(() -> shift.end(SUMMARY, CashOnHand.ZERO, null, NOW.plusSeconds(120)), ErrorCode.NO_OPEN_SHIFT);
            assertCode(() -> shift.receive(4L, NOW.plusSeconds(120)), ErrorCode.HANDOVER_PENDING);

            shift.receive(6L, NOW.plusSeconds(120));
            assertCode(() -> shift.receive(7L, NOW.plusSeconds(180)), ErrorCode.HANDOVER_PENDING);
        }

        @Test
        void chamCong_thoiGianTruc_chenhLechTienNhanCa() {
            GuardShift open = GuardShift.start(6L, new CashOnHand(300_000), new CashOnHand(350_000), NOW);
            assertThat(open.worked(NOW.plusSeconds(90 * 60))).isEqualTo(Duration.ofMinutes(90));
            assertThat(open.openingDiscrepancy()).contains(-50_000L);       // khai thiếu 50.000 đ so với ca trước

            GuardShift first = GuardShift.start(4L, CashOnHand.ZERO, null, NOW);
            assertThat(first.openingDiscrepancy()).isEmpty();               // không nhận biên bản nào: không so

            first.end(SUMMARY, CashOnHand.ZERO, null, NOW.plusSeconds(8 * 3600));
            assertThat(first.worked(NOW.plusSeconds(99 * 3600))).isEqualTo(Duration.ofHours(8));   // ca đã xong: không tính tiếp
        }

        @Test
        void tienMatAm_hoacQuaLon_biTuChoi() {
            assertCode(() -> new CashOnHand(-1), ErrorCode.INVALID_AMOUNT);
            assertCode(() -> new CashOnHand(CashOnHand.MAX + 1), ErrorCode.INVALID_AMOUNT);
        }

        @Test
        void soLieuAm_biTuChoi() {
            assertThatThrownBy(() -> new ShiftSummary(-1, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class SuCo {

        @Test
        void baoSuCo_trangThaiMoi_quanLyXuLy_ghiKetQua() {
            Incident incident = Incident.report(4L, IncidentType.XE_HU_HONG, PlateNumber.of("29A12345"),
                    "Xe bị vỡ gương", false, NOW);
            assertThat(incident.isOpen()).isTrue();

            incident.startHandling(1L, NOW.plusSeconds(60));
            incident.resolve(1L, "Đã trích camera, báo chủ bãi", NOW.plusSeconds(120));

            assertThat(incident.status()).isEqualTo(IncidentStatus.DA_XU_LY);
            assertThat(incident.isOpen()).isFalse();
            assertCode(() -> incident.reject(1L, "x", NOW), ErrorCode.INCIDENT_STATUS_CONFLICT);
        }

        @Test
        void moTaRong_biTuChoi_tuChoiPhaiCoLyDo() {
            assertCode(() -> Incident.report(4L, IncidentType.KHAC, null, "   ", false, NOW), ErrorCode.VALIDATION_ERROR);
            Incident incident = Incident.report(4L, IncidentType.KHAC, null, "Mất điện", false, NOW);
            assertCode(() -> incident.reject(1L, "", NOW), ErrorCode.VALIDATION_ERROR);
        }
    }

    @Nested
    class AnhDinhKem {

        private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

        @Test
        void anhJpegHopLe_nhanCaTienToDataUrl() {
            IncidentPhoto photo = IncidentPhoto.fromBase64("image/jpeg",
                    "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(JPEG));

            assertThat(photo.size()).isEqualTo(JPEG.length);
        }

        @Test
        void khaiLaJpegNhungNoiDungKhongPhai_quaLon_saiLoai_deuBiTuChoi() {
            assertCode(() -> new IncidentPhoto("image/jpeg", "MZ-file-exe".getBytes()), ErrorCode.PHOTO_INVALID);
            assertCode(() -> new IncidentPhoto("image/jpeg", new byte[IncidentPhoto.MAX_BYTES + 1]), ErrorCode.PHOTO_INVALID);
            assertCode(() -> new IncidentPhoto("image/gif", JPEG), ErrorCode.PHOTO_INVALID);
            assertCode(() -> new IncidentPhoto("image/webp", "RIFF0000WAVE".getBytes()), ErrorCode.PHOTO_INVALID);
            assertCode(() -> IncidentPhoto.fromBase64("image/jpeg", "khong-phai-base64!!"), ErrorCode.PHOTO_INVALID);
        }
    }

    @Test
    void xeGuiQua24Gio_laQuaLau() {
        ParkedVehicle vehicle = new ParkedVehicle(1L, PlateNumber.of("29A12345"), "XE_MAY", "B21DCCN001",
                "Nguyễn Văn A", NOW, "Trần Văn Bảo");

        assertThat(vehicle.isOverdue(NOW.plus(Duration.ofHours(24)))).isFalse();
        assertThat(vehicle.isOverdue(NOW.plus(Duration.ofHours(24)).plusSeconds(1))).isTrue();
        assertThat(vehicle.parkedFor(NOW.minusSeconds(5))).isEqualTo(Duration.ZERO);
    }
}
