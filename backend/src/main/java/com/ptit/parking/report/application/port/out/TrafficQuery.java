package com.ptit.parking.report.application.port.out;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

/** Port CHỈ ĐỌC: đếm lượt xe vào / ra theo giờ trong ngày (giờ địa phương 0–23). */
public interface TrafficQuery {

    /** Số lượt xe VÀO trong [from, to), nhóm theo giờ (giờ không có xe thì không có trong map). */
    Map<Integer, Long> entriesByHour(Instant from, Instant to, ZoneId zone);

    /** Số lượt xe RA trong [from, to), nhóm theo giờ. */
    Map<Integer, Long> exitsByHour(Instant from, Instant to, ZoneId zone);

    /** Số xe đang trong bãi lúc này. */
    long parkedNow();
}
