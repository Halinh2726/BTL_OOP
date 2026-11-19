package com.ptit.parking.report.application.port.out;

import com.ptit.parking.report.domain.CashPosition;
import java.time.LocalDate;
import java.time.ZoneId;

/** Port CHỈ ĐỌC: tình hình tiền mặt / điểm của một ngày tính từ sổ giao dịch (R12, R13). */
public interface CashPositionQuery {

    /** Ngày {@code date} theo múi giờ {@code zone} (giờ Việt Nam). */
    CashPosition positionOn(LocalDate date, ZoneId zone);
}
