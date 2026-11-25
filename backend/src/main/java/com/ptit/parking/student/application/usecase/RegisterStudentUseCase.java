package com.ptit.parking.student.application.usecase;

import com.ptit.parking.account.domain.EmailAddress;
import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.auth.domain.Student;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.application.port.out.StudentAccountRepository;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * SV01 — sinh viên tự đăng ký tài khoản (Đặc tả API #4).
 * <p>Tạo User (vai trò SINH_VIEN) + Student + ví 0 điểm trong MỘT transaction: không bao giờ có
 * sinh viên thiếu ví, hay ví không có chủ.
 */
public class RegisterStudentUseCase {

    /** Dữ liệu đầu vào. */
    public record Command(String studentCode, String fullName, String username, String password, String email) {

        public Command(String studentCode, String fullName, String username, String password) {
            this(studentCode, fullName, username, password, null);
        }
    }

    private final StudentAccountRepository students;
    private final WalletRepository wallets;
    private final PasswordHasher hasher;
    private final TransactionRunner transactions;

    public RegisterStudentUseCase(StudentAccountRepository students, WalletRepository wallets,
                                  PasswordHasher hasher, TransactionRunner transactions) {
        this.students = students;
        this.wallets = wallets;
        this.hasher = hasher;
        this.transactions = transactions;
    }

    public Student register(Command command) {
        return register(command, student -> {});
    }

    /** Thực hiện thêm thao tác lưu trữ trong cùng giao dịch tạo sinh viên và ví. */
    public Student register(Command command, Consumer<Student> onCreated) {
        Optional<EmailAddress> email = EmailAddress.ofNullable(command.email());
        // Băm BCrypt (chậm, cố ý) TRƯỚC khi mở transaction để không giữ kết nối CSDL lâu
        Student student = Student.register(command.username(), hasher.hash(command.password()),
                command.fullName(), command.studentCode());

        return transactions.inTransaction(() -> {
            if (students.studentCodeExists(student.studentCode())) {
                throw new DomainException(ErrorCode.STUDENT_CODE_EXISTS);
            }
            if (students.usernameExists(student.username())) {
                throw new DomainException(ErrorCode.USERNAME_EXISTS);
            }
            if (email.isPresent() && students.emailExists(email.get().value())) {
                throw new DomainException(ErrorCode.EMAIL_EXISTS);
            }
            // Hai người đăng ký cùng lúc vẫn có thể cùng qua bước kiểm tra trên; khi đó ràng buộc UNIQUE
            // của CSDL chặn người thứ hai và ConstraintErrorTranslator đổi thành đúng mã 409.
            Student saved = email.isPresent()
                    ? students.save(student, email.get().value())
                    : students.save(student);
            wallets.insert(Wallet.openFor(saved.id()));
            onCreated.accept(saved);
            return saved;
        });
    }
}
