package com.ptit.parking.student.application.port.out;

/**
 * Port: xe có đang trong bãi không (có lượt gửi DANG_GUI). Module student hỏi qua interface này thay vì phụ thuộc
 * trực tiếp module parking — parking đã phụ thuộc student (PlateNumber), phụ thuộc ngược lại sẽ thành vòng tròn.
 */
public interface ParkingPresence {

    boolean isParked(long vehicleId);
}
