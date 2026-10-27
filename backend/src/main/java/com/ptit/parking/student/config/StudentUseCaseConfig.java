package com.ptit.parking.student.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.student.application.port.out.StudentAccountRepository;
import com.ptit.parking.student.application.port.out.VehicleRepository;
import com.ptit.parking.student.application.usecase.ListMyVehiclesUseCase;
import com.ptit.parking.student.application.usecase.RegisterStudentUseCase;
import com.ptit.parking.student.application.usecase.RegisterVehicleUseCase;

/** Composition Root của module student: lắp adapter SQL, BCrypt và transaction thật vào use case. */
@Configuration
public class StudentUseCaseConfig {

    @Bean
    RegisterStudentUseCase registerStudentUseCase(StudentAccountRepository students, WalletRepository wallets,
                                                  PasswordHasher hasher, TransactionRunner transactions) {
        return new RegisterStudentUseCase(students, wallets, hasher, transactions);
    }

    @Bean
    RegisterVehicleUseCase registerVehicleUseCase(VehicleRepository vehicles, Clock clock) {
        return new RegisterVehicleUseCase(vehicles, clock);
    }

    @Bean
    ListMyVehiclesUseCase listMyVehiclesUseCase(VehicleRepository vehicles) {
        return new ListMyVehiclesUseCase(vehicles);
    }
}