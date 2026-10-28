package com.ptit.parking.manager.application.usecase;

import com.ptit.parking.manager.application.port.out.StudentDirectory;
import com.ptit.parking.manager.application.port.out.StudentDirectory.StudentAccount;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;

/**
 * QL01 bước 1 (Đặc tả API #16) — quản lý tra MSSV, xem họ tên để đối chiếu với thẻ sinh viên TRƯỚC khi
 * nhận tiền (R08), và xem số điểm hiện có.
 */
public class LookupStudentUseCase {

    public record StudentSummary(String studentCode, String fullName, long balance) {}

    private final StudentDirectory students;
    private final WalletRepository wallets;

    public LookupStudentUseCase(StudentDirectory students, WalletRepository wallets) {
        this.students = students;
        this.wallets = wallets;
    }

    public StudentSummary lookup(String studentCode) {
        StudentAccount student = students.findByStudentCode(StudentDirectory.normalize(studentCode))
                .orElseThrow(() -> new DomainException(ErrorCode.STUDENT_NOT_FOUND));
        long balance = wallets.findByUserId(student.userId()).orElseThrow().balance();
        return new StudentSummary(student.studentCode(), student.fullName(), balance);
    }
}