package com.ptit.parking.support.application.usecase;

import com.ptit.parking.support.application.port.out.ComplaintDirectory;
import com.ptit.parking.support.application.port.out.ComplaintDirectory.SessionInfo;
import com.ptit.parking.support.application.port.out.ComplaintDirectory.StudentInfo;
import com.ptit.parking.support.domain.Complaint;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Khiếu nại kèm thông tin hiển thị (sinh viên, lượt gửi, tên người xử lý / đề xuất / duyệt). */
public record ComplaintDetails(Complaint complaint, StudentInfo student, SessionInfo session, String handlerName,
                               String proposerName, String deciderName) {

    /** Ghép thông tin cho cả danh sách bằng 3 câu truy vấn (không truy vấn theo từng dòng). */
    static List<ComplaintDetails> of(List<Complaint> complaints, ComplaintDirectory directory) {
        Set<Long> students = new HashSet<>();
        Set<Long> sessions = new HashSet<>();
        Set<Long> people = new HashSet<>();
        for (Complaint c : complaints) {
            students.add(c.studentId());
            if (c.sessionId() != null) {
                sessions.add(c.sessionId());
            }
            for (Long id : new Long[] {c.handledBy(), c.proposedBy(), c.decidedBy()}) {
                if (id != null) {
                    people.add(id);
                }
            }
        }
        Map<Long, StudentInfo> studentInfo = directory.studentsOf(students);
        Map<Long, SessionInfo> sessionInfo = directory.sessionsOf(sessions);
        Map<Long, String> names = directory.namesOf(people);
        List<ComplaintDetails> result = new ArrayList<>(complaints.size());
        for (Complaint c : complaints) {
            result.add(new ComplaintDetails(c, studentInfo.get(c.studentId()),
                    c.sessionId() == null ? null : sessionInfo.get(c.sessionId()),
                    name(names, c.handledBy()), name(names, c.proposedBy()), name(names, c.decidedBy())));
        }
        return result;
    }

    static ComplaintDetails of(Complaint complaint, ComplaintDirectory directory) {
        return of(List.of(complaint), directory).getFirst();
    }

    private static String name(Map<Long, String> names, Long id) {
        return id == null ? null : names.get(id);
    }

    public ComplaintDetails {
        Objects.requireNonNull(complaint, "complaint");
    }
}
