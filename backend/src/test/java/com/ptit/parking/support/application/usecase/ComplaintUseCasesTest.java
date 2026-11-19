package com.ptit.parking.support.application.usecase;

import com.ptit.parking.shared.application.port.out.EventPublisher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ptit.parking.gate.domain.IncidentPhoto;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.support.application.PointPayout;
import com.ptit.parking.support.application.port.out.ComplaintDirectory;
import com.ptit.parking.support.application.port.out.ComplaintDirectory.SessionInfo;
import com.ptit.parking.support.application.port.out.ComplaintRepository;
import com.ptit.parking.support.domain.Complaint;
import com.ptit.parking.support.domain.ComplaintStatus;
import com.ptit.parking.support.domain.ComplaintType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Đợt 4b — quy tắc của use case khiếu nại (không CSDL): giới hạn khiếu nại mở, lượt gửi của mình, điều kiện hoàn phí. */
class ComplaintUseCasesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final long STUDENT = 3L;
    private static final TransactionRunner DIRECTLY = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    /** Kho giả: đối tượng trong danh sách được sửa trực tiếp nên update không cần làm gì. */
    static final class FakeComplaints implements ComplaintRepository {
        final List<Complaint> all = new ArrayList<>();

        @Override
        public Complaint insert(Complaint complaint, IncidentPhoto photo) {
            Complaint saved = complaint.withId(all.size() + 1L);
            all.add(saved);
            return saved;
        }

        @Override
        public void update(Complaint complaint) {
        }

        @Override
        public Optional<Complaint> findById(long id) {
            return all.stream().filter(c -> c.id() == id).findFirst();
        }

        @Override
        public Optional<Complaint> lockById(long id) {
            return findById(id);
        }

        @Override
        public List<Complaint> findByStudent(long studentId, int limit) {
            return all.stream().filter(c -> c.submittedBy(studentId)).limit(limit).toList();
        }

        @Override
        public List<Complaint> findByStatuses(Collection<ComplaintStatus> statuses, int limit) {
            return all.stream().filter(c -> statuses.isEmpty() || statuses.contains(c.status())).limit(limit).toList();
        }

        @Override
        public Optional<IncidentPhoto> findPhoto(long complaintId) {
            return Optional.empty();
        }
    }

    private final FakeComplaints complaints = new FakeComplaints();
    private final ComplaintDirectory directory = mock(ComplaintDirectory.class);
    private final PointPayout payout = mock(PointPayout.class);
    private final StudentComplaintUseCase students = new StudentComplaintUseCase(complaints, directory, DIRECTLY,
            EventPublisher.NONE, CLOCK);
    private final HandleComplaintUseCase handling = new HandleComplaintUseCase(complaints, directory, payout, DIRECTLY,
            EventPublisher.NONE, CLOCK);

    @BeforeEach
    void directoryReturnsNothingSpecial() {
        when(directory.studentsOf(anyCollection())).thenReturn(Map.of());
        when(directory.sessionsOf(anyCollection())).thenReturn(Map.of());
        when(directory.namesOf(anyCollection())).thenReturn(Map.of());
        when(directory.sessionOfStudent(anyLong(), anyLong())).thenReturn(Optional.empty());
    }

    private static ErrorCode codeOf(Runnable call) {
        try {
            call.run();
        } catch (DomainException e) {
            return e.errorCode();
        }
        throw new AssertionError("Không có lỗi nghiệp vụ");
    }

    private long submit(ComplaintType type) {
        return students.submit(new StudentComplaintUseCase.Command(STUDENT, type, null, "Mô tả", null, null))
                .complaint().id();
    }

    @Test
    void toiDa5KhieuNaiDangMo_huyBotMotCaiThiGuiTiepDuoc() {
        for (int i = 0; i < StudentComplaintUseCase.MAX_OPEN; i++) {
            submit(ComplaintType.KHAC);
        }
        assertThat(codeOf(() -> submit(ComplaintType.KHAC))).isEqualTo(ErrorCode.TOO_MANY_OPEN_COMPLAINTS);
        students.cancel(STUDENT, 1L);
        assertThat(submit(ComplaintType.KHAC)).isEqualTo(6L);
    }

    @Test
    void luotGuiKhongPhaiCuaMinh_biChan() {
        assertThat(codeOf(() -> students.submit(new StudentComplaintUseCase.Command(STUDENT, ComplaintType.THU_PHI_SAI,
                42L, "Lượt của người khác", null, null)))).isEqualTo(ErrorCode.PARKING_SESSION_NOT_FOUND);
        assertThat(complaints.all).isEmpty();
    }

    @Test
    void hoanPhi_chiKhieuNaiThuPhiSaiCoLuotDaThanhToan_khongChuyenDiemKhiSaiDieuKien() {
        long other = submit(ComplaintType.KHAC);
        handling.start(1L, other);
        assertThat(codeOf(() -> handling.resolve(1L, other, "Hoàn", 1000))).isEqualTo(ErrorCode.REFUND_NOT_ALLOWED);

        long noSession = submit(ComplaintType.THU_PHI_SAI);
        handling.start(1L, noSession);
        assertThat(codeOf(() -> handling.resolve(1L, noSession, "Hoàn", 1000))).isEqualTo(ErrorCode.REFUND_NOT_ALLOWED);
        verifyNoInteractions(payout);

        // Không hoàn điểm thì vẫn kết luận được bình thường
        assertThat(handling.resolve(1L, noSession, "Đã giải thích bảng giá", 0).complaint().status())
                .isEqualTo(ComplaintStatus.DA_XU_LY);
    }

    @Test
    void hoanPhi_trangThaiSai_baoXungDotTruocKhiChuyenDiem() {
        long id = students.submit(new StudentComplaintUseCase.Command(STUDENT, ComplaintType.THU_PHI_SAI, null,
                "Trừ nhầm", null, null)).complaint().id();
        Complaint c = complaints.findById(id).orElseThrow();
        when(directory.sessionsOf(anyCollection())).thenReturn(Map.of(7L, new SessionInfo(7L, STUDENT, "29A12345",
                CLOCK.instant(), CLOCK.instant(), 3000L, 99L)));
        // Khiếu nại gắn lượt 7 nhưng CHƯA tiếp nhận (MOI): không được hoàn
        complaints.all.set(0, Complaint.restore().id(c.id()).studentId(STUDENT).type(ComplaintType.THU_PHI_SAI)
                .sessionId(7L).description("Trừ nhầm").createdAt(c.createdAt()).status(ComplaintStatus.MOI).build());
        assertThat(codeOf(() -> handling.resolve(1L, id, "Hoàn", 3000))).isEqualTo(ErrorCode.COMPLAINT_STATUS_CONFLICT);
        verifyNoInteractions(payout);
        assertThatThrownBy(() -> handling.start(1L, 999L)).isInstanceOf(DomainException.class);
    }
}
