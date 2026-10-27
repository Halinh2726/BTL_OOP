package com.ptit.parking.student.application.usecase;

import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.auth.domain.Student;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.application.port.out.StudentAccountRepository;

/**
 * SV01 — sinh viên tự đăng ký tài khoản (Đặc tả API #4).
 * <p>Tạo User (vai trò SINH_VIEN) + Student + ví 0 điểm trong MỘT transaction: không bao giờ có
 * sinh viên thiếu ví, hay ví không có chủ.
 */
public class RegisterStudentUseCase {

    /** Dữ liệu đầu vào. Gom 4 chuỗi thành một đối tượng để không thể truyền nhầm thứ tự tham số. */
    public record Command(String studentCode, String fullName, String username, String password) {}

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
            // Hai người đăng ký cùng lúc vẫn có thể cùng qua bước kiểm tra trên; khi đó ràng buộc UNIQUE
            // của CSDL chặn người thứ hai và ConstraintErrorTranslator đổi thành đúng mã 409.
            Student saved = students.save(student);
            wallets.insert(Wallet.openFor(saved.id()));
            return saved;
        });
    }
}