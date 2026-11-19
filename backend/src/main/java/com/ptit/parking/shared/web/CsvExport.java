package com.ptit.parking.shared.web;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Xuất một {@link TabularReport} thành file CSV mở được bằng Excel: UTF-8 có BOM (đúng tiếng Việt), xuống dòng CRLF,
 * ô có dấu phẩy / ngoặc kép / xuống dòng được bọc ngoặc kép.
 * <p>Chống "CSV injection": ô CHỮ bắt đầu bằng = + - @ (Excel hiểu là công thức) được thêm dấu ' phía trước.
 */
public final class CsvExport {

    private static final String BOM = "﻿";
    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private CsvExport() {
    }

    public static String toCsv(TabularReport report) {
        StringBuilder csv = new StringBuilder(BOM);
        csv.append(line(List.copyOf(report.headers())));
        for (List<Object> row : report.rows()) {
            csv.append(line(row));
        }
        return csv.toString();
    }

    public static ResponseEntity<byte[]> response(TabularReport report) {
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(report.fileName() + ".csv").build().toString())
                .body(toCsv(report).getBytes(StandardCharsets.UTF_8));
    }

    private static String line(List<?> cells) {
        return cells.stream().map(CsvExport::cell).collect(Collectors.joining(",")) + "\r\n";
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        String text = value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
