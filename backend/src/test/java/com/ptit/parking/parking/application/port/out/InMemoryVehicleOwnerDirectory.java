package com.ptit.parking.parking.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ptit.parking.student.domain.PlateNumber;

/**
 * Danh bạ xe GIẢ trong bộ nhớ. "Khóa" không chặn gì nhưng ghi lại biển số đã khóa để test kiểm tra
 * "có khóa dòng xe không"; test đồng thời ghi đè {@link #lockByPlate} để khóa thật.
 */
public class InMemoryVehicleOwnerDirectory implements VehicleOwnerDirectory {

    private final Map<PlateNumber, VehicleOwner> byPlate = new ConcurrentHashMap<>();
    private final List<PlateNumber> locked = new CopyOnWriteArrayList<>();

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

    public List<PlateNumber> locked() {
        return List.copyOf(locked);
    }
}
