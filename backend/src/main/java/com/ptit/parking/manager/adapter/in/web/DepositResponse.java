package com.ptit.parking.manager.adapter.in.web;

import com.ptit.parking.payment.domain.CashDeposit;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Response 201 của POST /manager/deposits (Đặc tả API #17). */
record DepositResponse(long transactionId, String studentCode, long cashAmount, int cashPerPoint, long points,
                       long balanceBefore, long balanceAfter, String createdAt) {

    static DepositResponse from(CashDeposit deposit, String studentCode, ZoneId zone) {
        long wallet = deposit.targetWalletId();
        return new DepositResponse(deposit.id(), studentCode, deposit.cashAmount(), deposit.rate().cashPerPoint(),
                deposit.points(), deposit.balanceBeforeFor(wallet), deposit.balanceAfterFor(wallet),
                OffsetDateTime.ofInstant(deposit.createdAt(), zone).truncatedTo(ChronoUnit.SECONDS)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
}