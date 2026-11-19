package com.ptit.parking.gate.domain;

/**
 * Số liệu của một ca trực, "chụp" lúc bàn giao ca (value object). Chỉ ĐẾM số lượt — không có điểm, không có tiền (R06).
 *
 * @param checkIns       số xe bảo vệ này cho vào trong ca
 * @param checkOuts      số xe bảo vệ này cho ra trong ca
 * @param vehiclesInLot  số xe còn trong bãi lúc bàn giao (của cả bãi)
 * @param openIncidents  số sự cố chưa xử lý xong lúc bàn giao
 */
public record ShiftSummary(int checkIns, int checkOuts, int vehiclesInLot, int openIncidents) {

    public ShiftSummary {
        if (checkIns < 0 || checkOuts < 0 || vehiclesInLot < 0 || openIncidents < 0) {
            throw new IllegalArgumentException("Số liệu ca trực không được âm");
        }
    }
}
