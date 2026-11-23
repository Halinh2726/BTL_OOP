package com.ptit.parking.payment.config;

import com.ptit.parking.auth.application.port.out.UserRepository;
import com.ptit.parking.manager.application.usecase.DepositCashUseCase;
import com.ptit.parking.parking.application.usecase.PayParkingFeeByVietQrUseCase;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.port.out.BankConfigRepository;
import com.ptit.parking.payment.application.port.out.PointTransactionRepository;
import com.ptit.parking.payment.application.port.out.ReceivingWalletProvider;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.application.usecase.GetBankConfigUseCase;
import com.ptit.parking.payment.application.usecase.GetMyTransactionsUseCase;
import com.ptit.parking.payment.application.usecase.GetMyWalletUseCase;
import com.ptit.parking.payment.application.usecase.PayParkingFeeUseCase;
import com.ptit.parking.payment.application.usecase.ProcessBankWebhookUseCase;
import com.ptit.parking.payment.application.usecase.UpdateBankConfigUseCase;
import com.ptit.parking.payment.domain.BankAccountConfig;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition Root của module payment: các thành phần dùng chung cho mọi nghiệp vụ cộng/trừ điểm. */
@Configuration
public class PaymentConfig {

    @Bean
    GetBankConfigUseCase getBankConfigUseCase(
            BankConfigRepository repository,
            @Value("${parking.vietqr.bank-id:MB}") String bankId,
            @Value("${parking.vietqr.account-no:0987654321}") String accountNo,
            @Value("${parking.vietqr.account-name:BAI XE HOC VIEN PTIT}") String accountName,
            @Value("${parking.vietqr.template:compact2}") String template) {
        return new GetBankConfigUseCase(repository, new BankAccountConfig(bankId, accountNo, accountName, template));
    }

    @Bean
    UpdateBankConfigUseCase updateBankConfigUseCase(BankConfigRepository repository,
                                                    GetBankConfigUseCase getBankConfig) {
        return new UpdateBankConfigUseCase(repository, getBankConfig);
    }

    @Bean
    IdempotencyGuard idempotencyGuard(PointTransactionRepository transactions, TransactionRunner transactionRunner) {
        return new IdempotencyGuard(transactions, transactionRunner);
    }

    @Bean
    GetMyWalletUseCase getMyWalletUseCase(WalletRepository wallets) {
        return new GetMyWalletUseCase(wallets);
    }

    @Bean
    GetMyTransactionsUseCase getMyTransactionsUseCase(WalletRepository wallets, PointTransactionRepository transactions) {
        return new GetMyTransactionsUseCase(wallets, transactions);
    }

    @Bean
    PayParkingFeeUseCase payParkingFeeUseCase(WalletRepository wallets, ReceivingWalletProvider receivingWallet) {
        return new PayParkingFeeUseCase(wallets, receivingWallet);
    }

    @Bean
    ProcessBankWebhookUseCase processBankWebhookUseCase(DepositCashUseCase depositCash,
                                                        PayParkingFeeByVietQrUseCase payParkingFeeByVietQr,
                                                        UserRepository users) {
        return new ProcessBankWebhookUseCase(depositCash, payParkingFeeByVietQr, users);
    }
}
