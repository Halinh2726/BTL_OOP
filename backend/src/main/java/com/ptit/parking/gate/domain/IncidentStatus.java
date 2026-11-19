package com.ptit.parking.gate.domain;

/**
 * Trạng thái xử lý sự cố (khớp ck_incidents_status). MOI và DANG_XU_LY là "chưa xong": được chuyển sang biên bản
 * bàn giao ca. Mỗi hằng tự biết mình đã kết thúc hay chưa — nơi dùng không cần if/else theo từng trạng thái.
 */
public enum IncidentStatus {
    MOI(false),
    DANG_XU_LY(false),
    DA_XU_LY(true),
    TU_CHOI(true);

    private final boolean closed;

    IncidentStatus(boolean closed) {
        this.closed = closed;
    }

    public boolean isClosed() {
        return closed;
    }
}
