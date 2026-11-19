package com.ptit.parking.shared.application.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.parking.gate.application.event.ShiftScheduleChanged;
import com.ptit.parking.gate.domain.ShiftSlot;
import com.ptit.parking.owner.application.event.WithdrawalChanged;
import com.ptit.parking.owner.domain.WithdrawalStatus;
import com.ptit.parking.parking.application.event.LowBalanceWarning;
import com.ptit.parking.shared.application.event.NotifiableEvent.Notice;
import com.ptit.parking.shared.application.event.NotifiableEvent.Role;
import com.ptit.parking.shared.application.event.NotifiableEvent.User;
import com.ptit.parking.support.application.event.ComplaintChanged;
import com.ptit.parking.support.domain.ComplaintStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Đợt 5 — mẫu Observer: MỖI sự kiện tự biết báo cho ai, nội dung gì (đa hình, bộ nghe không có if/else). */
class NotifiableEventsTest {

    private static List<Object> recipients(NotifiableEvent event) {
        return event.notices().stream().map(Notice::to).map(Object.class::cast).toList();
    }

    @Test
    void khieuNai_moi_baoMoiQuanLy_ketLuan_baoSinhVien() {
        assertThat(recipients(new ComplaintChanged(9, 3, ComplaintStatus.MOI, null, null, null, null)))
                .containsExactly(new Role("QUAN_LY"));
        Notice done = new ComplaintChanged(9, 3, ComplaintStatus.DA_XU_LY, "Đã hoàn phí", null, null, null)
                .notices().getFirst();
        assertThat(done.to()).isEqualTo(new User(3));
        assertThat(done.title()).contains("#9");
        assertThat(done.body()).contains("Đã hoàn phí");
        assertThat(new ComplaintChanged(9, 3, ComplaintStatus.DA_HUY, null, null, null, null).notices()).isEmpty();
    }

    @Test
    void deXuatBoiThuong_baoSinhVienVaChuBai_duyet_baoSinhVienVaNguoiDeXuat() {
        assertThat(recipients(new ComplaintChanged(9, 3, ComplaintStatus.CHO_DUYET_BOI_THUONG, "Camera", 50_000L, 1L, null)))
                .containsExactly(new User(3), new Role("CHU_TAI_KHOAN"));
        List<Notice> approved = new ComplaintChanged(9, 3, ComplaintStatus.DA_BOI_THUONG, "Camera", 50_000L, 1L, null)
                .notices();
        assertThat(approved).extracting(Notice::to).containsExactly(new User(3), new User(1));
        assertThat(approved.getFirst().title()).contains("50.000");
    }

    @Test
    void rutDiem_moiTrangThaiBaoDungBen() {
        assertThat(recipients(new WithdrawalChanged(5, 2, WithdrawalStatus.CHO_XU_LY, 3000, 3000, null)))
                .containsExactly(new Role("QUAN_LY"));
        assertThat(recipients(new WithdrawalChanged(5, 2, WithdrawalStatus.DA_TRA, 3000, 3000, null)))
                .containsExactly(new User(2));
        assertThat(recipients(new WithdrawalChanged(5, 2, WithdrawalStatus.KHIEU_NAI, 3000, 3000, "Thiếu")))
                .containsExactly(new Role("QUAN_LY"));
        // Chủ bãi tự xác nhận: không báo ai; quản lý giải quyết khiếu nại (có ghi chú): báo chủ bãi
        assertThat(new WithdrawalChanged(5, 2, WithdrawalStatus.DA_XAC_NHAN, 3000, 3000, null).notices()).isEmpty();
        assertThat(recipients(new WithdrawalChanged(5, 2, WithdrawalStatus.DA_XAC_NHAN, 3000, 3000, "Đã trả bù")))
                .containsExactly(new User(2));
    }

    @Test
    void phanCa_soDuThap_baoDungNguoi_tieuDeCatBotKhiQuaDai() {
        Notice shift = new ShiftScheduleChanged(4, LocalDate.of(2026, 11, 21), ShiftSlot.DEM, true).notices().getFirst();
        assertThat(shift.to()).isEqualTo(new User(4));
        assertThat(shift.title()).contains("đêm").contains("21/11/2026");
        Notice low = new LowBalanceWarning(3, 1000, 3000).notices().getFirst();
        assertThat(low.body()).contains("1.000").contains("3.000");

        Notice longOne = Notice.toUser(1, "X", "a".repeat(400), "b".repeat(900));
        assertThat(longOne.title()).hasSize(Notice.MAX_TITLE).endsWith("…");
        assertThat(longOne.body()).hasSize(Notice.MAX_BODY);
    }
}
