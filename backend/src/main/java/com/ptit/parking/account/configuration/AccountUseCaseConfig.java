package com.ptit.parking.account.configuration;

import com.ptit.parking.account.application.port.out.AccountProfileRepository;
import com.ptit.parking.account.application.port.out.AuditLogQuery;
import com.ptit.parking.account.application.port.out.CredentialRepository;
import com.ptit.parking.account.application.port.out.EmailSender;
import com.ptit.parking.account.application.port.out.PasswordResetCodeRepository;
import com.ptit.parking.account.application.port.out.SecretGenerator;
import com.ptit.parking.account.application.usecase.ApplyDemoPasswordUseCase;
import com.ptit.parking.account.application.usecase.ChangePasswordUseCase;
import com.ptit.parking.account.application.usecase.ConfirmPasswordResetUseCase;
import com.ptit.parking.account.application.usecase.IssueTemporaryPasswordUseCase;
import com.ptit.parking.account.application.usecase.ListAuditLogUseCase;
import com.ptit.parking.account.application.usecase.ManageMyProfileUseCase;
import com.ptit.parking.account.application.usecase.RequestPasswordResetUseCase;
import com.ptit.parking.account.application.usecase.UpdateGuardProfileUseCase;
import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.shared.application.port.out.AuditTrail;
import com.ptit.parking.shared.application.port.out.EventPublisher;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition Root của module account. */
@Configuration
public class AccountUseCaseConfig {

    @Bean
    ManageMyProfileUseCase manageMyProfileUseCase(AccountProfileRepository profiles, AuditTrail audit,
                                                  TransactionRunner transactions, Clock clock) {
        return new ManageMyProfileUseCase(profiles, audit, transactions, clock);
    }

    @Bean
    ChangePasswordUseCase changePasswordUseCase(CredentialRepository credentials, PasswordHasher hasher,
                                                AuditTrail audit, TransactionRunner transactions,
                                                EventPublisher events, Clock clock) {
        return new ChangePasswordUseCase(credentials, hasher, audit, transactions, events, clock);
    }

    @Bean
    RequestPasswordResetUseCase requestPasswordResetUseCase(CredentialRepository credentials,
                                                            PasswordResetCodeRepository codes, PasswordHasher hasher,
                                                            SecretGenerator secrets, EmailSender emails,
                                                            AuditTrail audit, TransactionRunner transactions,
                                                            Clock clock) {
        return new RequestPasswordResetUseCase(credentials, codes, hasher, secrets, emails, audit, transactions, clock);
    }

    @Bean
    ConfirmPasswordResetUseCase confirmPasswordResetUseCase(CredentialRepository credentials,
                                                            PasswordResetCodeRepository codes, PasswordHasher hasher,
                                                            AuditTrail audit, TransactionRunner transactions,
                                                            EventPublisher events, Clock clock) {
        return new ConfirmPasswordResetUseCase(credentials, codes, hasher, audit, transactions, events, clock);
    }

    @Bean
    IssueTemporaryPasswordUseCase issueTemporaryPasswordUseCase(CredentialRepository credentials,
                                                                PasswordHasher hasher, SecretGenerator secrets,
                                                                AuditTrail audit, TransactionRunner transactions,
                                                                EventPublisher events, Clock clock) {
        return new IssueTemporaryPasswordUseCase(credentials, hasher, secrets, audit, transactions, events, clock);
    }

    @Bean
    UpdateGuardProfileUseCase updateGuardProfileUseCase(AccountProfileRepository profiles, AuditTrail audit,
                                                        TransactionRunner transactions, Clock clock) {
        return new UpdateGuardProfileUseCase(profiles, audit, transactions, clock);
    }

    @Bean
    ListAuditLogUseCase listAuditLogUseCase(AuditLogQuery logs) {
        return new ListAuditLogUseCase(logs);
    }

    @Bean
    ApplyDemoPasswordUseCase applyDemoPasswordUseCase(CredentialRepository credentials, PasswordHasher hasher,
                                                      TransactionRunner transactions, Clock clock) {
        return new ApplyDemoPasswordUseCase(credentials, hasher, transactions, clock);
    }
}
