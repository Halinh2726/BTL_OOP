package com.ptit.parking.report.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.report.application.port.out.CashPositionQuery;
import com.ptit.parking.report.application.port.out.LedgerQuery;
import com.ptit.parking.report.application.port.out.LedgerQuery.Entry;
import com.ptit.parking.report.application.port.out.LedgerQuery.Filter;
import com.ptit.parking.report.application.port.out.ReconciliationRepository;
import com.ptit.parking.report.application.port.out.StudentProfileQuery;
import com.ptit.parking.report.application.port.out.TrafficQuery;
import com.ptit.parking.report.application.usecase.LedgerUseCase.Criteria;
import com.ptit.parking.report.domain.CashPosition;
import com.ptit.parking.report.domain.DailyReconciliation;
import com.ptit.parking.report.domain.ReconciliationOutcome;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Đợt 4a — use case giám sát & đối soát, chạy với các kho giả trong bộ nhớ (không CSDL, không Spring). */
class ReportUseCasesTest {

    /** 10:00 sáng 20/11/2026 giờ Việt Nam. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    private static final TransactionRunner DIRECTLY = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    private static ErrorCode codeOf(ThrowingCallable call) {
        DomainException[] caught = new DomainException[1];
        assertThatThrownBy(() -> {
            try {
                call.call();
            } catch (DomainException e) {
                caught[0] = e;
                throw e;
            }
        }).isInstanceOf(DomainException.class);
        return caught[0].errorCode();
    }

    private static Entry entry(long id) {
        return new Entry(id, TransactionType.THANH_TOAN, 3000, null, null, null, "Bảo vệ", "BAO_VE",
                Instant.parse("2026-11-20T01:00:00Z"), "29A12345", null, null);
    }

    @Nested
    class SoGiaoDich {

        private final List<Filter> seen = new ArrayList<>();
        /** Sổ giả có 45 giao dịch id 1..45; trả về theo before / limit như CSDL. */
        private final LedgerQuery ledger = filter -> {
            seen.add(filter);
            return LongStream.iterate(45, id -> id - 1).limit(45)
                    .filter(id -> filter.before() == null || id < filter.before())
                    .limit(filter.limit()).mapToObj(ReportUseCasesTest::entry).toList();
        };
        private final LedgerUseCase useCase = new LedgerUseCase(ledger, CLOCK);

        @Test
        void phanTrangTheoKhoa_layThuaMotDong_deBietConTrangSau() {
            LedgerUseCase.Page first = useCase.page(new Criteria(null, null, null, null), null, 30);
            assertThat(first.items()).hasSize(30);
            assertThat(first.hasMore()).isTrue();
            assertThat(first.nextBefore()).isEqualTo(16L);
            assertThat(seen.getLast().limit()).isEqualTo(31);

            LedgerUseCase.Page second = useCase.page(new Criteria(null, null, null, null), first.nextBefore(), 30);
            assertThat(second.items()).hasSize(15);
            assertThat(second.hasMore()).isFalse();
            assertThat(second.nextBefore()).isNull();
        }

        @Test
        void ngayTheoGioVietNam_mssvVietHoa() {
            useCase.page(new Criteria(TODAY, TODAY, TransactionType.DOI_DIEM, " b21dccn001 "), null, 500);

            Filter filter = seen.getLast();
            assertThat(filter.from()).isEqualTo(Instant.parse("2026-11-19T17:00:00Z"));      // 00:00 ngày 20 giờ VN
            assertThat(filter.to()).isEqualTo(Instant.parse("2026-11-20T17:00:00Z"));        // 00:00 ngày 21 giờ VN
            assertThat(filter.studentCode()).isEqualTo("B21DCCN001");
            assertThat(filter.type()).isEqualTo(TransactionType.DOI_DIEM);
            assertThat(filter.limit()).isEqualTo(LedgerUseCase.MAX_LIMIT + 1);               // limit bị giới hạn 100
        }

        @Test
        void khoangNgayNguoc_hoacXuatQua93Ngay_biChan() {
            assertThat(codeOf(() -> useCase.page(new Criteria(TODAY, TODAY.minusDays(1), null, null), null, 30)))
                    .isEqualTo(ErrorCode.DATE_RANGE_INVALID);
            assertThat(codeOf(() -> useCase.export(new Criteria(TODAY.minusDays(93), TODAY, null, null))))
                    .isEqualTo(ErrorCode.DATE_RANGE_INVALID);
            assertThat(codeOf(() -> useCase.export(new Criteria(null, TODAY, null, null))))
                    .isEqualTo(ErrorCode.DATE_RANGE_INVALID);
            assertThat(useCase.export(new Criteria(TODAY.minusDays(92), TODAY, null, null))).hasSize(45);
        }
    }

    @Nested
    class DoiSoat {

        private final List<DailyReconciliation> saved = new ArrayList<>();
        private final CashPositionQuery positions = (date, zone) ->
                new CashPosition(date, 100_000, 50_000, 20_000, 3, 1, 12, 36_000, true);
        private final ReconciliationRepository repository = new ReconciliationRepository() {
            @Override
            public DailyReconciliation save(DailyReconciliation r) {
                DailyReconciliation withId = r.withId(saved.size() + 1L);
                saved.add(withId);
                return withId;
            }

            @Override
            public Optional<Closed> findByDate(LocalDate date) {
                return saved.stream().filter(r -> r.businessDate().equals(date)).findFirst()
                        .map(r -> new Closed(r, "Phạm Văn Quản"));
            }

            @Override
            public List<Closed> recent(int limit) {
                return saved.stream().map(r -> new Closed(r, "Phạm Văn Quản")).toList();
            }
        };
        private final ReconciliationUseCase useCase = new ReconciliationUseCase(positions, repository, DIRECTLY, CLOCK);

        @Test
        void xemNgay_chuaChot_roiChot_khongChotLanHai() {
            assertThat(useCase.day(TODAY).closed()).isEmpty();
            assertThat(useCase.day(TODAY).position().expectedCash()).isEqualTo(130_000);

            ReconciliationRepository.Closed closed = useCase.close(
                    new ReconciliationUseCase.Command(1L, TODAY, 130_000, null));
            assertThat(closed.reconciliation().outcome()).isEqualTo(ReconciliationOutcome.KHOP);
            assertThat(closed.closedByName()).isEqualTo("Phạm Văn Quản");
            assertThat(closed.reconciliation().closedAt()).isEqualTo(CLOCK.instant());
            assertThat(useCase.day(TODAY).closed()).isPresent();
            assertThat(useCase.history()).hasSize(1);

            assertThat(codeOf(() -> useCase.close(new ReconciliationUseCase.Command(1L, TODAY, 130_000, null))))
                    .isEqualTo(ErrorCode.ALREADY_RECONCILED);
            assertThat(saved).hasSize(1);
        }

        @Test
        void ngayTuongLai_khongXemKhongChot() {
            assertThat(codeOf(() -> useCase.day(TODAY.plusDays(1)))).isEqualTo(ErrorCode.RECONCILIATION_DATE_INVALID);
            assertThat(codeOf(() -> useCase.close(new ReconciliationUseCase.Command(1L, TODAY.plusDays(1), 0, null))))
                    .isEqualTo(ErrorCode.RECONCILIATION_DATE_INVALID);
        }

        @Test
        void lechKhongGiaiTrinh_khongLuuGi() {
            assertThat(codeOf(() -> useCase.close(new ReconciliationUseCase.Command(1L, TODAY, 120_000, ""))))
                    .isEqualTo(ErrorCode.RECONCILIATION_NOTE_REQUIRED);
            assertThat(saved).isEmpty();
        }
    }

    @Nested
    class HoSoSinhVien {

        private final List<Filter> ledgerCalls = new ArrayList<>();
        private final StudentProfileQuery students = new StudentProfileQuery() {
            @Override
            public List<StudentRow> search(String fragment, int limit) {
                return List.of(new StudentRow(3L, "B21DCCN001", "Nguyễn Văn A", "HOAT_DONG", 44_000, 1, false));
            }

            @Override
            public Optional<StudentDetail> findByCode(String code) {
                return "B21DCCN001".equals(code)
                        ? Optional.of(new StudentDetail(3L, code, "Nguyễn Văn A", null, null, "HOAT_DONG",
                        Instant.parse("2026-10-01T00:00:00Z"), 44_000))
                        : Optional.empty();
            }

            @Override
            public List<VehicleRow> vehiclesOf(long userId) {
                return List.of();
            }

            @Override
            public List<SessionRow> recentSessions(long userId, int limit) {
                return List.of();
            }
        };
        private final StudentProfileUseCase useCase = new StudentProfileUseCase(students, filter -> {
            ledgerCalls.add(filter);
            return List.of(entry(4));
        });

        @Test
        void timKiem_itNhat2KyTu() {
            assertThat(codeOf(() -> useCase.search(" a "))).isEqualTo(ErrorCode.VALIDATION_ERROR);
            assertThat(useCase.search("Văn")).hasSize(1);
        }

        @Test
        void hoSo_mssvKhongPhanBietHoaThuong_kemGiaoDichGanDayCuaChinhSinhVienDo() {
            StudentProfileUseCase.Profile profile = useCase.profile(" b21dccn001 ");

            assertThat(profile.student().fullName()).isEqualTo("Nguyễn Văn A");
            assertThat(profile.recentTransactions()).hasSize(1);
            assertThat(ledgerCalls.getLast().studentCode()).isEqualTo("B21DCCN001");
            assertThat(ledgerCalls.getLast().limit()).isEqualTo(StudentProfileUseCase.RECENT_LIMIT);
            assertThat(codeOf(() -> useCase.profile("KHONGCO"))).isEqualTo(ErrorCode.STUDENT_NOT_FOUND);
        }
    }

    @Nested
    class ThongKeLuotXe {

        private final List<Instant[]> ranges = new ArrayList<>();
        private final TrafficQuery traffic = new TrafficQuery() {
            @Override
            public Map<Integer, Long> entriesByHour(Instant from, Instant to, ZoneId zone) {
                ranges.add(new Instant[] {from, to});
                return Map.of(7, 70L, 17, 35L);
            }

            @Override
            public Map<Integer, Long> exitsByHour(Instant from, Instant to, ZoneId zone) {
                return Map.of(17, 60L);
            }

            @Override
            public long parkedNow() {
                return 12;
            }
        };
        private final TrafficStatsUseCase useCase = new TrafficStatsUseCase(traffic, CLOCK);

        @Test
        void bayNgay_tinhTuNgay14DenHetHomNay_gioCaoDiem() {
            TrafficStatsUseCase.Report report = useCase.report(7);

            assertThat(report.from()).isEqualTo(LocalDate.of(2026, 11, 14));
            assertThat(report.to()).isEqualTo(TODAY);
            assertThat(ranges.getFirst()[0]).isEqualTo(Instant.parse("2026-11-13T17:00:00Z"));
            assertThat(ranges.getFirst()[1]).isEqualTo(Instant.parse("2026-11-20T17:00:00Z"));
            assertThat(ranges.get(1)[0]).isEqualTo(Instant.parse("2026-11-19T17:00:00Z"));    // riêng hôm nay
            assertThat(report.profile().averageEntriesAt(7)).isEqualTo(10.0);
            assertThat(report.peakHours()).containsExactly(7, 17);
            assertThat(report.parkedNow()).isEqualTo(12);
        }

        @Test
        void soNgayNgoai7_30_90_biChan() {
            assertThat(codeOf(() -> useCase.report(14))).isEqualTo(ErrorCode.VALIDATION_ERROR);
        }
    }
}
