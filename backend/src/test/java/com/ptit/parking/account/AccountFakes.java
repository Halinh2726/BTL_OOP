package com.ptit.parking.account;

import com.ptit.parking.account.application.port.out.CredentialRepository;
import com.ptit.parking.account.application.port.out.EmailSender;
import com.ptit.parking.account.application.port.out.PasswordResetCodeRepository;
import com.ptit.parking.account.application.port.out.SecretGenerator;
import com.ptit.parking.account.domain.AccountCredential;
import com.ptit.parking.account.domain.EmailAddress;
import com.ptit.parking.account.domain.PasswordResetCode;
import com.ptit.parking.auth.domain.AccountStatus;
import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.shared.application.port.out.AuditTrail;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Các lớp giả trong bộ nhớ dùng chung cho test module account — không cần CSDL, Spring hay BCrypt thật. */
public final class AccountFakes {

    private AccountFakes() {
    }

    /** "Băm" giả: thêm tiền tố — đủ để kiểm tra use case gọi hash/matches đúng chỗ. */
    public static final PasswordHasher HASHER = new PasswordHasher() {
        @Override
        public String hash(String rawPassword) {
            return "h:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return passwordHash.equals("h:" + rawPassword);
        }
    };

    public static final TransactionRunner DIRECTLY = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    public static AccountCredential credential(long userId, Role role, String rawPassword) {
        return new AccountCredential(userId, "user" + userId, "Người dùng " + userId, role, AccountStatus.HOAT_DONG,
                HASHER.hash(rawPassword), false, null);
    }

    public static final class Credentials implements CredentialRepository {
        public final Map<Long, AccountCredential> byId = new HashMap<>();
        public final Map<String, Long> idByEmail = new HashMap<>();
        public final Map<String, Long> idByStudentCode = new HashMap<>();

        public Credentials add(AccountCredential credential, String email) {
            byId.put(credential.userId(), credential);
            if (email != null) {
                idByEmail.put(email, credential.userId());
            }
            return this;
        }

        @Override
        public Optional<AccountCredential> findByUserId(long userId) {
            return Optional.ofNullable(byId.get(userId));
        }

        @Override
        public Optional<AccountCredential> findByEmail(EmailAddress email) {
            return Optional.ofNullable(idByEmail.get(email.value())).map(byId::get);
        }

        @Override
        public Optional<AccountCredential> findByStudentCode(String studentCode) {
            return Optional.ofNullable(idByStudentCode.get(studentCode)).map(byId::get);
        }

        @Override
        public void save(AccountCredential credential) {
            byId.put(credential.userId(), credential);
        }

        public String demoHash;

        @Override
        public int resetAllPasswords(String passwordHash, java.time.Instant changedAt) {
            demoHash = passwordHash;
            return byId.size();
        }
    }

    public static final class Codes implements PasswordResetCodeRepository {
        public final List<PasswordResetCode> all = new ArrayList<>();
        public int saves;

        @Override
        public void save(PasswordResetCode code) {
            saves++;
            if (code.id() == null) {
                all.add(PasswordResetCode.restore(all.size() + 1, code.userId(), code.codeHash(), code.createdAt(),
                        code.expiresAt(), code.failedAttempts(), code.usedAt()));
            }
        }

        @Override
        public Optional<PasswordResetCode> findLatest(long userId) {
            return all.stream().filter(c -> c.userId() == userId).reduce((first, second) -> second);
        }

        @Override
        public Optional<PasswordResetCode> lockLatest(long userId) {
            return findLatest(userId);
        }
    }

    public static final class Outbox implements EmailSender {
        public final List<EmailMessage> sent = new ArrayList<>();

        @Override
        public void send(EmailMessage message) {
            sent.add(message);
        }
    }

    public static final class FixedSecrets implements SecretGenerator {
        @Override
        public String oneTimeCode() {
            return "123456";
        }

        @Override
        public String temporaryPassword() {
            return "TamThoi2345";
        }
    }

    public static final class AuditLog implements AuditTrail {
        public final List<AuditEntry> entries = new ArrayList<>();

        @Override
        public void record(AuditEntry entry) {
            entries.add(entry);
        }
    }
}
