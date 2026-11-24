package com.ptit.parking.shared.presentation.web;

import java.util.List;

/**
 * Một báo cáo dạng bảng: tên file, dòng tiêu đề, các dòng dữ liệu. {@link CsvExport} biết cách xuất MỌI báo cáo
 * cài interface này — muốn thêm báo cáo mới (chấm công, doanh thu...) chỉ cần viết một lớp cài đặt, không sửa CsvExport
 * (nguyên lý đóng/mở). Muốn thêm định dạng mới (Excel) thì viết thêm một lớp xuất, các báo cáo giữ nguyên.
 */
public interface TabularReport {

    /** Tên file không kèm đuôi, chỉ gồm chữ không dấu, số, '-', '_'. */
    String fileName();

    List<String> headers();

    /** Mỗi ô là String, Number hoặc null (ô trống). */
    List<List<Object>> rows();
}
