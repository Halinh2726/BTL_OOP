package com.ptit.parking.manager.adapter.in.web;

import com.ptit.parking.payment.domain.CashWithdrawal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Response 201 của POST /manager/withdrawals (Đặc tả API #19). cashAmount là số tiền quản lý trả cho chủ tài khoản. */
record WithdrawalResponse(long transactionId, long points, int cashPerPoint, long cashAmount,
                          long balanceBefore, long balanceAfter, String createdAt) {

    static WithdrawalResponse from(CashWithdrawal withdrawal, ZoneId zone) {
        long wallet = withdrawal.sourceWalletId();
        return new WithdrawalResponse(withdrawal.id(), withdrawal.points(), withdrawal.rate().cashPerPoint(),
                withdrawal.cashAmount(), withdrawal.balanceBeforeFor(wallet), withdrawal.balanceAfterFor(wallet),
                OffsetDateTime.ofInstant(withdrawal.createdAt(), zone).truncatedTo(ChronoUnit.SECONDS)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
}
