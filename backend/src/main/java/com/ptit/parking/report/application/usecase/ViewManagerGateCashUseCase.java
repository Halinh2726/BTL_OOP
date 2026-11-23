package com.ptit.parking.report.application.usecase;

import com.ptit.parking.parking.application.port.out.GateCashRepository;
import com.ptit.parking.parking.application.port.out.GateCashRepository.CashEntry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mục 3.6 PDF — Quản lý xem bảng tổng hợp tiền mặt bảo vệ đã thu tại chốt theo ngày,
 * tách biệt hoàn toàn với nạp điểm điện tử ({@code DOI_DIEM}).
 */
public class ViewManagerGateCashUseCase {

    public record GuardTotal(long guardId, String guardName, int count, long totalAmount) {}

    public record Report(LocalDate date, long totalAmount, int totalCount,
                         List<GuardTotal> byGuard, List<CashEntry> entries) {}

    private final GateCashRepository gateCash;
    private final ZoneId zone;

    public ViewManagerGateCashUseCase(GateCashRepository gateCash, ZoneId zone) {
        this.gateCash = gateCash;
        this.zone = zone;
    }

    public Report onDate(LocalDate date) {
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<CashEntry> entries = gateCash.findAllBetween(start, end);

        Map<Long, List<CashEntry>> grouped = new LinkedHashMap<>();
        for (CashEntry entry : entries) {
            grouped.computeIfAbsent(entry.guardId(), k -> new ArrayList<>()).add(entry);
        }

        List<GuardTotal> byGuard = grouped.values().stream()
                .map(list -> new GuardTotal(
                        list.get(0).guardId(),
                        list.get(0).guardName(),
                        list.size(),
                        list.stream().mapToLong(CashEntry::amount).sum()))
                .toList();
        long totalAmount = entries.stream().mapToLong(CashEntry::amount).sum();
        return new Report(date, totalAmount, entries.size(), byGuard, entries);
    }
}
