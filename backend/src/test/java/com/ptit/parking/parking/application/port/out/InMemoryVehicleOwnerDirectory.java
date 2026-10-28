package com.ptit.parking.parking.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Comparator;
import java.util.Set;
import com.ptit.parking.student.domain.PlateNumber;

/**
 * Danh bạ xe GIẢ trong bộ nhớ. "Khóa" không chặn gì nhưng ghi lại biển số đã khóa để test kiểm tra
 * "có khóa dòng xe không"; test đồng thời ghi đè {@link #lockByPlate} để khóa thật.
 */
public class InMemoryVehicleOwnerDirectory implements VehicleOwnerDirectory {

    private final Map<PlateNumber, VehicleOwner> byPlate = new ConcurrentHashMap<>();
    private final List<PlateNumber> locked = new CopyOnWriteArrayList<>();
    private final Set<PlateNumber> parked = ConcurrentHashMap.newKeySet();
    public void add(VehicleOwner vehicle) {
        byPlate.put(vehicle.plate(), vehicle);
    }

    @Override
    public Optional<VehicleOwner> findByPlate(PlateNumber plate) {
        return Optional.ofNullable(byPlate.get(plate));
    }

    @Override
    public Optional<VehicleOwner> lockByPlate(PlateNumber plate) {
        locked.add(plate);
        return findByPlate(plate);
    }

        @Override
    public Optional<VehicleOwner> findByVehicleId(long vehicleId) {
        return byPlate.values().stream().filter(v -> v.vehicleId() == vehicleId).findFirst();
    }

        /** Chuẩn bị dữ liệu test cho gợi ý biển số: xe này đang có lượt gửi chưa ra. */
    public void markParked(PlateNumber plate) {
        parked.add(plate);
    }

    /** Cùng quy tắc với câu SQL: chứa đoạn ký tự, biển kết thúc bằng đoạn đó xếp trước, rồi theo biển số. */
    @Override
    public List<PlateSuggestion> suggestPlates(String fragment, boolean parkedOnly, int limit) {
        return byPlate.values().stream()
                .filter(v -> v.plate().value().contains(fragment))
                .filter(v -> !parkedOnly || parked.contains(v.plate()))
                .sorted(Comparator.comparing((VehicleOwner v) -> !v.plate().value().endsWith(fragment))
                        .thenComparing(v -> v.plate().value()))
                .limit(limit)
                .map(v -> new PlateSuggestion(v.plate(), v.vehicleTypeCode(), parked.contains(v.plate())))
                .toList();
    }

    public List<PlateNumber> locked() {
        return List.copyOf(locked);
    }
}
