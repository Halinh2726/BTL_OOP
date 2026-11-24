package com.ptit.parking.report.presentation.web;

import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.report.application.port.out.LedgerQuery.Entry;
import com.ptit.parking.report.application.port.out.LedgerQuery.Party;
import com.ptit.parking.shared.presentation.web.TabularReport;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Báo cáo sổ giao dịch dạng bảng — một cài đặt của {@link TabularReport} để xuất CSV. */
record LedgerCsvReport(LocalDate from, LocalDate to, List<Entry> entries, ZoneId zone) implements TabularReport {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Override
    public String fileName() {
        return "so-giao-dich_" + from + "_" + to;
    }

    @Override
    public List<String> headers() {
        return List.of("Mã GD", "Thời gian", "Loại", "Số điểm", "Tiền mặt (đ)", "Bên chuyển", "MSSV bên chuyển",
                "Bên nhận", "MSSV bên nhận", "Người thực hiện", "Biển số", "Lý do", "GD gốc");
    }

    @Override
    public List<List<Object>> rows() {
        List<List<Object>> rows = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            rows.add(Arrays.asList(e.id(), TIME.format(e.createdAt().atZone(zone)), label(e.type()), e.points(),
                    e.cashAmount(), name(e.from()), code(e.from()), name(e.to()), code(e.to()), e.performerName(),
                    e.plateNumber(), e.reason(), e.originalTransactionId()));
        }
        return rows;
    }

    /** switch trên enum: thêm loại giao dịch mới mà quên nhãn ở đây thì trình biên dịch báo lỗi. */
    static String label(TransactionType type) {
        return switch (type) {
            case DOI_DIEM -> "Đổi điểm";
            case THANH_TOAN -> "Thanh toán phí gửi xe";
            case RUT_DIEM -> "Rút điểm";
            case DIEU_CHINH -> "Điều chỉnh";
            case MUA_VE -> "Mua vé tháng";
        };
    }

    private static String name(Party party) {
        return party == null ? null : party.fullName();
    }

    private static String code(Party party) {
        return party == null ? null : party.studentCode();
    }
}
