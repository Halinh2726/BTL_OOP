package com.ptit.parking.gate.configuration;

import com.ptit.parking.shared.application.port.out.EventPublisher;
import com.ptit.parking.gate.application.port.out.GateActivityQuery;
import com.ptit.parking.gate.application.port.out.GuardRoster;
import com.ptit.parking.gate.application.port.out.IncidentRepository;
import com.ptit.parking.gate.application.port.out.ShiftAssignmentRepository;
import com.ptit.parking.gate.application.port.out.ShiftRepository;
import com.ptit.parking.gate.application.usecase.HandleIncidentUseCase;
import com.ptit.parking.gate.application.usecase.ShiftPlanUseCase;
import com.ptit.parking.gate.application.usecase.ReportIncidentUseCase;
import com.ptit.parking.gate.application.usecase.ShiftAttendanceUseCase;
import com.ptit.parking.gate.application.usecase.ShiftHandoverUseCase;
import com.ptit.parking.gate.application.usecase.ViewGuardCashSheetUseCase;
import com.ptit.parking.gate.application.usecase.ViewMyActivityUseCase;
import com.ptit.parking.gate.application.usecase.ViewParkingLotUseCase;
import com.ptit.parking.gate.domain.GuardShift;
import com.ptit.parking.parking.application.port.out.GateCashRepository;
import com.ptit.parking.parking.application.port.out.OpenShiftLookup;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition Root của module gate. */
@Configuration
public class GateUseCaseConfig {

    @Bean
    ViewParkingLotUseCase viewParkingLotUseCase(GateActivityQuery activity, Clock clock) {
        return new ViewParkingLotUseCase(activity, clock);
    }

    @Bean
    ViewMyActivityUseCase viewMyActivityUseCase(GateActivityQuery activity, ShiftRepository shifts, Clock clock) {
        return new ViewMyActivityUseCase(activity, shifts, clock);
    }

    @Bean
    ShiftHandoverUseCase shiftHandoverUseCase(ShiftRepository shifts, GateActivityQuery activity,
                                              IncidentRepository incidents, TransactionRunner transactions, Clock clock) {
        return new ShiftHandoverUseCase(shifts, activity, incidents, transactions, clock);
    }

    @Bean
    ShiftAttendanceUseCase shiftAttendanceUseCase(ShiftRepository shifts, GateActivityQuery activity, Clock clock) {
        return new ShiftAttendanceUseCase(shifts, activity, clock);
    }

    @Bean
    ReportIncidentUseCase reportIncidentUseCase(IncidentRepository incidents, TransactionRunner transactions,
                                                Clock clock) {
        return new ReportIncidentUseCase(incidents, transactions, clock);
    }

    /** Đợt 4b — quản lý xử lý sự cố bảo vệ báo. */
    @Bean
    HandleIncidentUseCase handleIncidentUseCase(IncidentRepository incidents, TransactionRunner transactions,
                                                Clock clock) {
        return new HandleIncidentUseCase(incidents, transactions, clock);
    }

    /** Đợt 4b — phân ca và đối chiếu chuyên cần. */
    @Bean
    ShiftPlanUseCase shiftPlanUseCase(ShiftAssignmentRepository assignments, ShiftRepository shifts, GuardRoster roster,
                                      TransactionRunner transactions, EventPublisher events, Clock clock) {
        return new ShiftPlanUseCase(assignments, shifts, roster, transactions, events, clock);
    }

    @Bean
    OpenShiftLookup openShiftLookup(ShiftRepository shifts) {
        return guardId -> shifts.findOpenByGuard(guardId).map(GuardShift::id);
    }

    @Bean
    ViewGuardCashSheetUseCase viewGuardCashSheetUseCase(ShiftRepository shifts, GateCashRepository gateCash,
                                                        Clock clock) {
        return new ViewGuardCashSheetUseCase(shifts, gateCash, clock);
    }
}
