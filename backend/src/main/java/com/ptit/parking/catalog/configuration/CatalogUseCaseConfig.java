package com.ptit.parking.catalog.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ptit.parking.catalog.application.port.out.ExchangeRateRepository;
import com.ptit.parking.catalog.application.port.out.FeeRuleRepository;
import com.ptit.parking.catalog.application.port.out.VehicleTypeRepository;
import com.ptit.parking.catalog.application.usecase.GetCatalogUseCase;
import com.ptit.parking.catalog.application.usecase.ManageFeeCatalogUseCase;
import com.ptit.parking.shared.application.port.out.TransactionRunner;

/** Composition Root của module catalog: lắp adapter SQL thật vào use case. */
@Configuration
public class CatalogUseCaseConfig {

    @Bean
    GetCatalogUseCase getCatalogUseCase(VehicleTypeRepository vehicleTypes, FeeRuleRepository feeRules,
                                        ExchangeRateRepository exchangeRates) {
        return new GetCatalogUseCase(vehicleTypes, feeRules, exchangeRates);
    }

    @Bean
    ManageFeeCatalogUseCase manageFeeCatalogUseCase(VehicleTypeRepository vehicleTypes, FeeRuleRepository feeRules,
                                                    TransactionRunner transactions) {
        return new ManageFeeCatalogUseCase(vehicleTypes, feeRules, transactions);
    }
}