package com.ptit.parking.manager.presentation.web;

import com.ptit.parking.payment.application.usecase.GetBankConfigUseCase.ActiveBankConfig;
import com.ptit.parking.payment.domain.BankAccountConfig;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Response của GET / PUT / DELETE /api/v1/manager/bank-config. */
record BankConfigResponse(
        String bankId,
        String accountNo,
        String accountName,
        String template,
        String source,
        String updatedAt,
        EnvDefault envDefault
) {

    record EnvDefault(String bankId, String accountNo, String accountName, String template) {
        static EnvDefault from(BankAccountConfig config) {
            return new EnvDefault(config.bankId(), config.accountNo(), config.accountName(), config.template());
        }
    }

    static BankConfigResponse from(ActiveBankConfig active, ZoneId zone) {
        BankAccountConfig c = active.config();
        String formattedTime = active.updatedAt() == null ? null
                : OffsetDateTime.ofInstant(active.updatedAt(), zone).truncatedTo(ChronoUnit.SECONDS)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new BankConfigResponse(
                c.bankId(),
                c.accountNo(),
                c.accountName(),
                c.template(),
                active.source().name(),
                formattedTime,
                EnvDefault.from(active.envDefault()));
    }
}
