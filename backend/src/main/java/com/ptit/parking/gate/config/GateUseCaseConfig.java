package com.ptit.parking.gate.config;

import com.ptit.parking.gate.application.port.out.GateActivityQuery;
import com.ptit.parking.gate.application.port.out.IncidentRepository;
import com.ptit.parking.gate.application.port.out.ShiftRepository;
import com.ptit.parking.gate.application.usecase.ReportIncidentUseCase;
import com.ptit.parking.gate.application.usecase.ShiftAttendanceUseCase;
import com.ptit.parking.gate.application.usecase.ShiftHandoverUseCase;
import com.ptit.parking.gate.application.usecase.ViewMyActivityUseCase;
import com.ptit.parking.gate.application.usecase.ViewParkingLotUseCase;
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
}
