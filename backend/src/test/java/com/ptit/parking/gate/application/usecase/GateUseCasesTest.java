package com.ptit.parking.gate.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.gate.application.port.out.GateActivityQuery;
import com.ptit.parking.gate.application.port.out.IncidentRepository;
import com.ptit.parking.gate.application.port.out.ShiftRepository;
import com.ptit.parking.gate.domain.CashOnHand;
import com.ptit.parking.gate.domain.GuardShift;
import com.ptit.parking.gate.domain.Incident;
import com.ptit.parking.gate.domain.IncidentPhoto;
import com.ptit.parking.gate.domain.IncidentType;
import com.ptit.parking.gate.domain.ParkedVehicle;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Đợt 2 — use case của bảo vệ với kho dữ liệu giả trong bộ nhớ. */
class GateUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final TransactionRunner DIRECTLY = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    private final FakeShifts shifts = new FakeShifts();
    private final FakeActivity activity = new FakeActivity();
    private final FakeIncidents incidents = new FakeIncidents();
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"));
    private final ShiftHandoverUseCase handover = new ShiftHandoverUseCase(shifts, activity, incidents, DIRECTLY, clock);

    @Test
    void ketThucCa_soLieuDoHeThongTuDem_chiDemKhongCoDiem() {
        handover.start(4L, Set.of(), new CashOnHand(200_000));
        activity.checkIns = 12;
        activity.checkOuts = 9;
        activity.parked = 30;
        incidents.save(Incident.report(4L, IncidentType.KHAC, null, "Mất điện cổng sau", false, NOW), null);

        GuardShift ended = handover.end(4L, new CashOnHand(350_000), "Đèn cổng sau hỏng");

        assertThat(ended.summary().checkIns()).isEqualTo(12);
        assertThat(ended.summary().checkOuts()).isEqualTo(9);
        assertThat(ended.summary().vehiclesInLot()).isEqualTo(30);
        assertThat(ended.summary().openIncidents()).isEqualTo(1);
    }

    @Test
    void coBienBanChuaNhan_phaiXacNhanDungBienBanMoiNhanCaDuoc() {
        handover.start(4L, Set.of(), CashOnHand.ZERO);
        long handoverId = handover.end(4L, new CashOnHand(350_000), null).id();

        assertThatThrownBy(() -> handover.start(6L, Set.of(), CashOnHand.ZERO))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.HANDOVER_PENDING));

        GuardShift mine = handover.start(6L, Set.of(handoverId), new CashOnHand(300_000));
        assertThat(mine.expectedOpeningCash()).isEqualTo(new CashOnHand(350_000));
        assertThat(mine.openingDiscrepancy()).contains(-50_000L);
        assertThat(mine.isOpen()).isTrue();
        assertThat(shifts.byId(handoverId).receivedBy()).isEqualTo(6L);
        assertThat(handover.overview(7L).pendingHandovers()).isEmpty();
    }

    @Test
    void bamNhanCaHaiLan_khongTaoHaiCa_ketThucKhiChuaNhanCa_baoLoi() {
        GuardShift first = handover.start(4L, Set.of(), CashOnHand.ZERO);
        assertThat(handover.start(4L, Set.of(), CashOnHand.ZERO)).isSameAs(first);

        assertThatThrownBy(() -> handover.end(6L, CashOnHand.ZERO, null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NO_OPEN_SHIFT));
    }

    @Test
    void danhSachXeTrongBai_locTheoBienSo_demXeQuaLau() {
        activity.vehicles.add(parked(1, "29A12345", NOW.minus(Duration.ofHours(30))));
        activity.vehicles.add(parked(2, "30B67890", NOW.minus(Duration.ofHours(2))));

        ViewParkingLotUseCase.LotView view = new ViewParkingLotUseCase(activity, clock).view("123.45");

        assertThat(view.total()).isEqualTo(2);
        assertThat(view.overdue()).isEqualTo(1);
        assertThat(view.vehicles()).extracting(v -> v.plate().value()).containsExactly("29A12345");
    }

    @Test
    void baoSuCo_bienSoSai_biTuChoi_anhChiNguoiBaoXemDuoc() {
        ReportIncidentUseCase report = new ReportIncidentUseCase(incidents, DIRECTLY, clock);
        assertThatThrownBy(() -> report.report(new ReportIncidentUseCase.Command(4L, IncidentType.XE_HU_HONG,
                "29-A", "Vỡ gương", null, null)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        String jpeg = java.util.Base64.getEncoder().encodeToString(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 9});
        Incident saved = report.report(new ReportIncidentUseCase.Command(4L, IncidentType.XE_HU_HONG,
                "29a-123.45", "Vỡ gương", "image/jpeg", jpeg));

        assertThat(saved.hasPhoto()).isTrue();
        assertThat(report.photo(4L, saved.id()).contentType()).isEqualTo("image/jpeg");
        assertThatThrownBy(() -> report.photo(6L, saved.id()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INCIDENT_NOT_FOUND));
    }

    @Test
    void chamCong_tongGioTruc_soCaLechTien_theoTungBaoVe() {
        handover.start(4L, Set.of(), CashOnHand.ZERO);
        long first = handover.end(4L, new CashOnHand(350_000), null).id();
        handover.start(6L, Set.of(first), new CashOnHand(300_000));     // khai thiếu 50.000 đ

        ShiftAttendanceUseCase attendance = new ShiftAttendanceUseCase(shifts, activity, clock);
        ShiftAttendanceUseCase.Timesheet sheet = attendance.timesheet(
                java.time.LocalDate.of(2026, 11, 20), java.time.LocalDate.of(2026, 11, 20), null);

        assertThat(sheet.shifts()).hasSize(2);
        assertThat(sheet.totals()).extracting(ShiftAttendanceUseCase.GuardTotals::guardId).containsExactlyInAnyOrder(4L, 6L);
        assertThat(sheet.totals()).filteredOn(t -> t.guardId() == 6L).first()
                .extracting(ShiftAttendanceUseCase.GuardTotals::cashMismatches).isEqualTo(1);
        assertThat(attendance.myHistory(4L).shifts()).hasSize(1);
        assertThatThrownBy(() -> attendance.timesheet(java.time.LocalDate.of(2026, 11, 20),
                java.time.LocalDate.of(2026, 11, 1), null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private static ParkedVehicle parked(long id, String plate, Instant enteredAt) {
        return new ParkedVehicle(id, PlateNumber.of(plate), "XE_MAY", "B21DCCN00" + id, "Sinh viên " + id,
                enteredAt, "Bảo vệ");
    }

    // ===== Kho giả =====

    static final class FakeShifts implements ShiftRepository {
        final List<GuardShift> all = new ArrayList<>();

        GuardShift byId(long id) {
            return all.stream().filter(s -> s.id() == id).findFirst().orElseThrow();
        }

        @Override
        public Optional<GuardShift> findOpenByGuard(long guardId) {
            return all.stream().filter(s -> s.guardId() == guardId && s.isOpen()).findFirst();
        }

        @Override
        public Optional<GuardShift> lockOpenByGuard(long guardId) {
            return findOpenByGuard(guardId);
        }

        @Override
        public List<GuardShift> findPendingHandovers(long excludingGuardId, boolean lock) {
            return all.stream().filter(s -> s.isAwaitingReceipt() && s.guardId() != excludingGuardId).toList();
        }

        @Override
        public List<GuardShift> findByGuard(long guardId, int limit) {
            return all.stream().filter(s -> s.guardId() == guardId).sorted((a, b) -> Long.compare(b.id(), a.id()))
                    .limit(limit).toList();
        }

        @Override
        public List<GuardShift> findStartedBetween(Instant from, Instant to, Long guardId) {
            return all.stream()
                    .filter(s -> !s.startedAt().isBefore(from) && s.startedAt().isBefore(to))
                    .filter(s -> guardId == null || s.guardId() == guardId)
                    .toList();
        }

        @Override
        public GuardShift save(GuardShift shift) {
            if (shift.id() != null) {
                return shift;
            }
            GuardShift saved = shift.withId(all.size() + 1);
            all.add(saved);
            return saved;
        }
    }

    static final class FakeActivity implements GateActivityQuery {
        final List<ParkedVehicle> vehicles = new ArrayList<>();
        long checkIns;
        long checkOuts;
        long parked;

        @Override
        public List<ParkedVehicle> parkedVehicles() {
            return vehicles;
        }

        @Override
        public long countParked() {
            return parked;
        }

        @Override
        public long countCheckIns(long guardId, Instant from, Instant to) {
            return checkIns;
        }

        @Override
        public long countCheckOuts(long guardId, Instant from, Instant to) {
            return checkOuts;
        }

        @Override
        public List<ActivityEntry> activityOf(long guardId, Instant from, int limit) {
            return List.of();
        }

        @Override
        public Map<Long, String> namesOf(Collection<Long> userIds) {
            Map<Long, String> names = new HashMap<>();
            userIds.forEach(id -> names.put(id, "Người " + id));
            return names;
        }
    }

    static final class FakeIncidents implements IncidentRepository {
        final List<Incident> all = new ArrayList<>();
        final Map<Long, IncidentPhoto> photos = new HashMap<>();

        @Override
        public Incident save(Incident incident, IncidentPhoto photo) {
            Incident saved = incident.withId(all.size() + 1);
            all.add(saved);
            if (photo != null) {
                photos.put(saved.id(), photo);
            }
            return saved;
        }

        @Override
        public Optional<Incident> findById(long incidentId) {
            return all.stream().filter(i -> i.id() == incidentId).findFirst();
        }

        @Override
        public List<Incident> findByReporter(long reporterId, int limit) {
            return all.stream().filter(i -> i.reportedBy(reporterId)).limit(limit).toList();
        }

        @Override
        public List<Incident> findOpen(int limit) {
            return all.stream().filter(Incident::isOpen).limit(limit).toList();
        }

        @Override
        public long countOpen() {
            return all.stream().filter(Incident::isOpen).count();
        }

        @Override
        public Optional<IncidentPhoto> findPhoto(long incidentId) {
            return Optional.ofNullable(photos.get(incidentId));
        }

        // Đợt 4b — quản lý xử lý sự cố (đối tượng trong danh sách được sửa trực tiếp nên update không cần làm gì)

        @Override
        public Optional<Incident> lockById(long incidentId) {
            return findById(incidentId);
        }

        @Override
        public void updateHandling(Incident incident) {
        }

        @Override
        public List<Incident> findByStatus(com.ptit.parking.gate.domain.IncidentStatus status, int limit) {
            return all.stream().filter(i -> status == null ? i.isOpen() : i.status() == status).limit(limit).toList();
        }

        @Override
        public Map<Long, String> namesOf(java.util.Collection<Long> userIds) {
            Map<Long, String> names = new HashMap<>();
            userIds.forEach(id -> names.put(id, "Người dùng " + id));
            return names;
        }
    }
}
