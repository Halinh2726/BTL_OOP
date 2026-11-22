package com.ptit.parking.support.application.event;

import com.ptit.parking.shared.application.event.NotifiableEvent;
import com.ptit.parking.support.domain.CameraReviewStatus;
import java.util.List;

/** Thông báo cho sinh viên khi quản lý cập nhật trạng thái/kết quả trích xuất camera của khiếu nại. */
public record CameraReviewUpdated(
        long complaintId,
        long studentId,
        CameraReviewStatus status,
        String note,
        boolean hasPhoto) implements NotifiableEvent {

    @Override
    public List<Notice> notices() {
        String id = "#" + complaintId;
        String suffix = (note == null || note.isBlank()) ? "" : " Ghi chú: " + note.strip();
        String title;
        String body;
        switch (status) {
            case DANG_TRICH_XUAT -> {
                title = "Đang trích xuất camera cho yêu cầu " + id;
                body = "Quản lý đang trích xuất dữ liệu camera theo yêu cầu của bạn." + suffix;
            }
            case DA_TRICH_XUAT -> {
                title = "Đã có kết quả trích xuất camera " + id;
                body = (hasPhoto ? "Quản lý đã đính kèm ảnh trích xuất camera." : "Quản lý đã hoàn tất kiểm tra camera.")
                        + suffix;
            }
            case KHONG_CO_DU_LIEU -> {
                title = "Không có dữ liệu camera cho yêu cầu " + id;
                body = "Quản lý phản hồi không tìm thấy dữ liệu camera phù hợp." + suffix;
            }
            default -> {
                title = "Cập nhật trích xuất camera " + id;
                body = "Trạng thái trích xuất camera đã được cập nhật." + suffix;
            }
        }
        return List.of(Notice.toUser(studentId, "CAMERA", title, body));
    }
}
