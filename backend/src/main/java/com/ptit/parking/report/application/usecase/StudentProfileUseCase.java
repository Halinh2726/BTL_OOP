package com.ptit.parking.report.application.usecase;

import com.ptit.parking.report.application.port.out.LedgerQuery;
import com.ptit.parking.report.application.port.out.StudentProfileQuery;
import com.ptit.parking.report.application.port.out.StudentProfileQuery.SessionRow;
import com.ptit.parking.report.application.port.out.StudentProfileQuery.StudentDetail;
import com.ptit.parking.report.application.port.out.StudentProfileQuery.StudentRow;
import com.ptit.parking.report.application.port.out.StudentProfileQuery.VehicleRow;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.List;
import java.util.Locale;

/**
 * Đợt 4a — quản lý tra cứu hồ sơ sinh viên: tìm theo MSSV / họ tên, xem thông tin, số điểm, xe, lượt gửi và giao
 * dịch gần đây. CHỈ ĐỌC: không sửa được số dư hay giao dịch (R10).
 */
public class StudentProfileUseCase {

    public static final int MIN_QUERY = 2;
    public static final int SEARCH_LIMIT = 20;
    public static final int RECENT_LIMIT = 10;

    public record Profile(StudentDetail student, List<VehicleRow> vehicles, List<SessionRow> recentSessions,
                          List<LedgerQuery.Entry> recentTransactions) {}

    private final StudentProfileQuery students;
    private final LedgerQuery ledger;

    public StudentProfileUseCase(StudentProfileQuery students, LedgerQuery ledger) {
        this.students = students;
        this.ledger = ledger;
    }

    /** Ít nhất 2 ký tự để tránh trả về cả trường. */
    public List<StudentRow> search(String query) {
        String fragment = query == null ? "" : query.strip();
        if (fragment.length() < MIN_QUERY) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        return students.search(fragment, SEARCH_LIMIT);
    }

    public Profile profile(String studentCode) {
        String code = studentCode == null ? "" : studentCode.strip().toUpperCase(Locale.ROOT);
        StudentDetail student = students.findByCode(code)
                .orElseThrow(() -> new DomainException(ErrorCode.STUDENT_NOT_FOUND));
        List<LedgerQuery.Entry> transactions = ledger.find(
                new LedgerQuery.Filter(null, null, null, student.studentCode(), null, RECENT_LIMIT));
        return new Profile(student, students.vehiclesOf(student.userId()),
                students.recentSessions(student.userId(), RECENT_LIMIT), transactions);
    }
}
