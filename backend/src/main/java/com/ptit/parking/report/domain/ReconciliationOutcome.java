package com.ptit.parking.report.domain;

/** Kết luận của biên bản đối soát: tiền đếm được so với sổ sách. */
public enum ReconciliationOutcome {
    /** Đếm được đúng bằng sổ sách. */
    KHOP,
    /** Đếm được nhiều hơn sổ sách (có thể quên ghi một giao dịch rút). */
    THUA,
    /** Đếm được ít hơn sổ sách (có thể trả nhầm, quên ghi một giao dịch đổi điểm...). */
    THIEU;

    public static ReconciliationOutcome of(long discrepancy) {
        if (discrepancy == 0) {
            return KHOP;
        }
        return discrepancy > 0 ? THUA : THIEU;
    }
}
