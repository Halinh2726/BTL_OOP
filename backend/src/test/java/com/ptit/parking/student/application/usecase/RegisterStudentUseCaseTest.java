package com.ptit.parking.student.application.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.ptit.parking.auth.domain.AccountStatus;
import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.auth.domain.Student;
import com.ptit.parking.payment.application.port.out.InMemoryWalletRepository;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.application.port.out.StudentAccountRepository;
import com.ptit.parking.student.application.usecase.RegisterStudentUseCase.Command;

/** SV01 — kiểm tra bằng lớp giả của các port: không cần CSDL, không cần Spring. */
class RegisterStudentUseCaseTest {

    private final FakeTransactions transactions = new FakeTransactions();
    private final FakeStudents students = new FakeStudents(transactions);
    private final FakeWallets wallets = new FakeWallets(transactions);
    private final PasswordHasher hasher = new PasswordHasher() {
        @Override
        public String hash(String rawPassword) { return "hashed:" + rawPassword; }

        @Override
        public boolean matches(String rawPassword, String passwordHash) { return passwordHash.equals(hash(rawPassword)); }
    };

    private final RegisterStudentUseCase register = new RegisterStudentUseCase(students, wallets, hasher, transactions);

    @Test
    void dangKyThanhCong_taoSinhVienDangHoatDong_kemVi0Diem() {
        Student saved = register.register(new Command("b21dccn001", "Nguyễn Văn A", "sv01", "MatKhau@123"));

        assertThat(saved.id()).isEqualTo(1L);
        assertThat(saved.role()).isEqualTo(Role.SINH_VIEN);
        assertThat(saved.status()).isEqualTo(AccountStatus.HOAT_DONG);
        assertThat(saved.studentCode()).isEqualTo("B21DCCN001");             // MSSV chuẩn hóa chữ hoa
        assertThat(saved.passwordHash()).isEqualTo("hashed:MatKhau@123");    // không lưu mật khẩu rõ

        assertThat(wallets.created).singleElement().satisfies(wallet -> {
            assertThat(wallet.userId()).isEqualTo(1L);
            assertThat(wallet.balance()).isZero();
        });
    }

    @Test
    void trungMssv_baoStudentCodeExists_khongTaoGi() {
        students.save(Student.register("sv99", "x", "Người cũ", "B21DCCN001"));

        assertError(() -> register.register(new Command("B21DCCN001", "Nguyễn Văn A", "sv01", "MatKhau@123")),
                ErrorCode.STUDENT_CODE_EXISTS);
        assertThat(students.saved).hasSize(1);
        assertThat(wallets.created).isEmpty();
    }

    @Test
    void trungUsername_baoUsernameExists_khongTaoGi() {
        students.save(Student.register("sv01", "x", "Người cũ", "B21DCCN999"));

        assertError(() -> register.register(new Command("B21DCCN001", "Nguyễn Văn A", "sv01", "MatKhau@123")),
                ErrorCode.USERNAME_EXISTS);
        assertThat(wallets.created).isEmpty();
    }

    @Test
    void luuSinhVienVaTaoVi_namTrongCungMotTransaction() {
        register.register(new Command("B21DCCN001", "Nguyễn Văn A", "sv01", "MatKhau@123"));

        assertThat(transactions.count).isEqualTo(1);
        assertThat(students.savedInsideTransaction).isTrue();
        assertThat(wallets.createdInsideTransaction).isTrue();
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(expected));
    }

    // ----- Lớp giả cài đặt interface của port -----

    /** Ghi nhận đang ở trong transaction hay không, để test kiểm tra được ranh giới transaction. */
    static final class FakeTransactions implements TransactionRunner {
        int count;
        boolean active;

        @Override
        public <T> T inTransaction(Supplier<T> work) {
            count++;
            active = true;
            try {
                return work.get();
            } finally {
                active = false;
            }
        }
    }

    static final class FakeStudents implements StudentAccountRepository {
        final List<Student> saved = new ArrayList<>();
        boolean savedInsideTransaction;
        private final FakeTransactions transactions;

        FakeStudents(FakeTransactions transactions) { this.transactions = transactions; }

        @Override
        public boolean studentCodeExists(String studentCode) {
            return saved.stream().anyMatch(s -> s.studentCode().equals(studentCode));
        }

        @Override
        public boolean usernameExists(String username) {
            return saved.stream().anyMatch(s -> s.username().equals(username));
        }

        @Override
        public Student save(Student student) {
            savedInsideTransaction = transactions.active;
            Student withId = new Student((long) saved.size() + 1, student.username(), student.passwordHash(),
                    student.fullName(), student.status(), student.studentCode());
            saved.add(withId);
            return withId;
        }
    }

        /** Dùng lại kho ví giả chung; chỉ GHI ĐÈ insert để ghi nhận ví được tạo trong transaction hay không. */
    static final class FakeWallets extends InMemoryWalletRepository {
        final List<Wallet> created = new ArrayList<>();
        boolean createdInsideTransaction;
        private final FakeTransactions transactions;

        FakeWallets(FakeTransactions transactions) { this.transactions = transactions; }

        @Override
        public long insert(Wallet wallet) {
            createdInsideTransaction = transactions.active;
            created.add(wallet);
            return super.insert(wallet);
        }
    }

}