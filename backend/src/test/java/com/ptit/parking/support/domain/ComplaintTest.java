package com.ptit.parking.support.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** Đợt 4b — khiếu nại: máy trạng thái, đúng người, đúng loại, người duyệt khác người đề xuất. */
class ComplaintTest {

    private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");
    private static final long STUDENT = 3L;
    private static final long MANAGER = 1L;
    private static final long OWNER = 2L;

    private static Complaint submitted(ComplaintType type) {
        return Complaint.submit(STUDENT, type, 7L, "  Bị trừ 5.000 dù ra lúc 17:00  ", false, NOW).withId(10);
    }

    private static ErrorCode codeOf(ThrowingCallable call) {
        DomainException[] caught = new DomainException[1];
        assertThatThrownBy(() -> {
            try {
                call.call();
            } catch (DomainException e) {
                caught[0] = e;
                throw e;
            }
        }).isInstanceOf(DomainException.class);
        return caught[0].errorCode();
    }

    @Test
    void guiMoi_trangThaiMoi_moTaDaCatKhoangTrang() {
        Complaint c = submitted(ComplaintType.THU_PHI_SAI);
        assertThat(c.status()).isEqualTo(ComplaintStatus.MOI);
        assertThat(c.description()).isEqualTo("Bị trừ 5.000 dù ra lúc 17:00");
        assertThat(c.isOpen()).isTrue();
        assertThat(codeOf(() -> Complaint.submit(STUDENT, ComplaintType.KHAC, null, "  ", false, NOW)))
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void sinhVienChiHuyDuocKhieuNaiCuaMinh_khiChuaTiepNhan() {
        Complaint c = submitted(ComplaintType.KHAC);
        assertThat(codeOf(() -> c.cancel(99L, NOW))).isEqualTo(ErrorCode.COMPLAINT_NOT_FOUND);
        c.cancel(STUDENT, NOW);
        assertThat(c.status()).isEqualTo(ComplaintStatus.DA_HUY);
        assertThat(c.cancelledAt()).isEqualTo(NOW);

        Complaint handled = submitted(ComplaintType.KHAC);
        handled.startHandling(MANAGER, NOW);
        assertThat(codeOf(() -> handled.cancel(STUDENT, NOW))).isEqualTo(ErrorCode.COMPLAINT_STATUS_CONFLICT);
    }

    @Test
    void thuPhiSai_ketLuanKemHoanPhi_loaiKhacKhongDuocHoan() {
        Complaint fee = submitted(ComplaintType.THU_PHI_SAI);
        fee.startHandling(MANAGER, NOW);
        fee.resolve(MANAGER, "Đúng là trừ nhầm giá ca tối", 55L, NOW);
        assertThat(fee.status()).isEqualTo(ComplaintStatus.DA_XU_LY);
        assertThat(fee.refundTransactionId()).isEqualTo(55L);
        assertThat(fee.isOpen()).isFalse();

        Complaint other = submitted(ComplaintType.KHAC);
        other.startHandling(MANAGER, NOW);
        assertThat(codeOf(() -> other.resolve(MANAGER, "x", 56L, NOW))).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void phaiTiepNhanTruocKhiKetLuan_ketLuanBatBuocGhiKetQua() {
        Complaint c = submitted(ComplaintType.KHAC);
        assertThat(codeOf(() -> c.resolve(MANAGER, "Xong", null, NOW))).isEqualTo(ErrorCode.COMPLAINT_STATUS_CONFLICT);
        c.startHandling(MANAGER, NOW);
        assertThat(codeOf(() -> c.resolve(MANAGER, " ", null, NOW))).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void tuChoiDuocCaKhiMoiVaKhiDangXuLy() {
        Complaint fresh = submitted(ComplaintType.KHAC);
        fresh.reject(MANAGER, "Không có căn cứ", NOW);
        assertThat(fresh.status()).isEqualTo(ComplaintStatus.TU_CHOI);
        assertThat(codeOf(() -> fresh.reject(MANAGER, "lần hai", NOW))).isEqualTo(ErrorCode.COMPLAINT_STATUS_CONFLICT);
    }

    @Test
    void boiThuong_chiXeHuHongMatDo_soDiemHopLe_chuBaiDuyet() {
        Complaint fee = submitted(ComplaintType.THU_PHI_SAI);
        fee.startHandling(MANAGER, NOW);
        assertThat(codeOf(() -> fee.proposeCompensation(MANAGER, 50_000, "x", NOW)))
                .isEqualTo(ErrorCode.COMPLAINT_NOT_COMPENSABLE);

        Complaint damage = submitted(ComplaintType.XE_HU_HONG);
        damage.startHandling(MANAGER, NOW);
        assertThat(codeOf(() -> damage.proposeCompensation(MANAGER, 0, "x", NOW))).isEqualTo(ErrorCode.INVALID_AMOUNT);
        damage.proposeCompensation(MANAGER, 150_000, "Camera cho thấy xe bị đổ do va chạm trong bãi", NOW);
        assertThat(damage.status()).isEqualTo(ComplaintStatus.CHO_DUYET_BOI_THUONG);
        assertThat(damage.proposedPoints()).isEqualTo(150_000);

        damage.requireAwaitingDecisionBy(OWNER);
        damage.approveCompensation(OWNER, null, 77L, NOW);
        assertThat(damage.status()).isEqualTo(ComplaintStatus.DA_BOI_THUONG);
        assertThat(damage.compensationTransactionId()).isEqualTo(77L);
        assertThat(damage.decidedBy()).isEqualTo(OWNER);
        assertThat(codeOf(() -> damage.requireAwaitingDecisionBy(OWNER))).isEqualTo(ErrorCode.COMPLAINT_STATUS_CONFLICT);
    }

    @Test
    void nguoiDeXuatKhongTuDuyet_tuChoiPhaiCoLyDo() {
        Complaint lost = submitted(ComplaintType.MAT_DO);
        lost.startHandling(MANAGER, NOW);
        lost.proposeCompensation(MANAGER, 20_000, "Mất mũ bảo hiểm", NOW);

        assertThat(codeOf(() -> lost.requireAwaitingDecisionBy(MANAGER))).isEqualTo(ErrorCode.SELF_PROCESSING_FORBIDDEN);
        assertThat(codeOf(() -> lost.rejectCompensation(MANAGER, "x", NOW))).isEqualTo(ErrorCode.SELF_PROCESSING_FORBIDDEN);
        assertThat(codeOf(() -> lost.rejectCompensation(OWNER, " ", NOW))).isEqualTo(ErrorCode.VALIDATION_ERROR);

        lost.rejectCompensation(OWNER, "Mũ để ngoài khu vực trông giữ", NOW);
        assertThat(lost.status()).isEqualTo(ComplaintStatus.KHONG_BOI_THUONG);
        assertThat(lost.decisionNote()).isEqualTo("Mũ để ngoài khu vực trông giữ");
    }

    @Test
    void mayTrangThai_trangThaiKetThucKhongDiDauDuoc() {
        for (ComplaintStatus closed : new ComplaintStatus[] {ComplaintStatus.DA_XU_LY, ComplaintStatus.TU_CHOI,
                ComplaintStatus.DA_HUY, ComplaintStatus.DA_BOI_THUONG, ComplaintStatus.KHONG_BOI_THUONG}) {
            assertThat(closed.isClosed()).isTrue();
            for (ComplaintStatus next : ComplaintStatus.values()) {
                assertThat(closed.canMoveTo(next)).isFalse();
            }
        }
        assertThat(ComplaintStatus.MOI.canMoveTo(ComplaintStatus.CHO_DUYET_BOI_THUONG)).isFalse();   // phải tiếp nhận trước
        assertThat(ComplaintStatus.CHO_DUYET_BOI_THUONG.canMoveTo(ComplaintStatus.DA_BOI_THUONG)).isTrue();
    }
}
