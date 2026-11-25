package com.ptit.parking.gate.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.gate.application.event.IncidentChanged;
import com.ptit.parking.gate.domain.Incident;
import com.ptit.parking.gate.domain.IncidentStatus;
import com.ptit.parking.gate.domain.IncidentType;
import com.ptit.parking.shared.application.port.out.EventPublisher;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Đợt 4b — quản lý xử lý sự cố bảo vệ báo (dùng lại kho giả FakeIncidents của GateUseCasesTest). */
class HandleIncidentUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final GateUseCasesTest.FakeIncidents incidents = new GateUseCasesTest.FakeIncidents();
    private final TransactionRunner directly = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };
    private final List<Object> published = new ArrayList<>();
    private final EventPublisher capture = published::add;
    private final HandleIncidentUseCase useCase = new HandleIncidentUseCase(incidents, directly, capture, CLOCK);

    private long reported() {
        return incidents.save(Incident.report(4L, IncidentType.XE_HU_HONG, null, "Xe đổ", false, CLOCK.instant()), null).id();
    }

    @Test
    void danhSachMacDinh_chiSuCoChuaDong_kemTenNguoiBao() {
        long open = reported();
        long done = reported();
        useCase.start(1L, done);
        useCase.resolve(1L, done, "Đã báo chủ xe");

        assertThat(useCase.list(null)).extracting(l -> l.incident().id()).containsExactly(open);
        assertThat(useCase.list(IncidentStatus.DA_XU_LY)).singleElement().satisfies(l -> {
            assertThat(l.reporterName()).isEqualTo("Người dùng 4");
            assertThat(l.handlerName()).isEqualTo("Người dùng 1");
            assertThat(l.incident().resolution()).isEqualTo("Đã báo chủ xe");
        });
    }

    @Test
    void nhanXuLy_roiTuChoi_khongThaoTacLaiDuocSuCoDaDong() {
        long id = reported();
        assertThat(useCase.start(1L, id).incident().status()).isEqualTo(IncidentStatus.DANG_XU_LY);
        assertThat(useCase.reject(1L, id, "Báo nhầm biển số").incident().status()).isEqualTo(IncidentStatus.TU_CHOI);
        assertThatThrownBy(() -> useCase.resolve(1L, id, "x"))
                .extracting(e -> ((DomainException) e).errorCode()).isEqualTo(ErrorCode.INCIDENT_STATUS_CONFLICT);
        assertThatThrownBy(() -> useCase.start(1L, 999L))
                .extracting(e -> ((DomainException) e).errorCode()).isEqualTo(ErrorCode.INCIDENT_NOT_FOUND);
        assertThat(published).hasSize(2).allSatisfy(e -> assertThat(e).isInstanceOf(IncidentChanged.class));
    }
}
