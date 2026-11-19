package com.ptit.parking.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/** Đợt 4a — xuất CSV: BOM cho Excel, thoát ký tự đặc biệt, chống chèn công thức. */
class CsvExportTest {

    private final TabularReport report = new TabularReport() {
        @Override
        public String fileName() { return "thu-nghiem"; }

        @Override
        public List<String> headers() { return List.of("Mã", "Tên", "Số"); }

        @Override
        public List<List<Object>> rows() {
            return List.of(
                    Arrays.asList(1, "Nguyễn Văn A, K65", -3000),
                    Arrays.asList(2, "Nói \"xin chào\"", null),
                    Arrays.asList(3, "=HYPERLINK(\"http://x\")", 5));
        }
    };

    @Test
    void coBom_xuongDongCrlf_thoatDauPhayVaNgoacKep_chanCongThuc() {
        String csv = CsvExport.toCsv(report);

        assertThat(csv).startsWith("﻿Mã,Tên,Số\r\n");
        assertThat(csv).contains("1,\"Nguyễn Văn A, K65\",-3000\r\n");          // số âm KHÔNG bị thêm '
        assertThat(csv).contains("2,\"Nói \"\"xin chào\"\"\",\r\n");             // null = ô trống
        assertThat(csv).contains("3,\"'=HYPERLINK(\"\"http://x\"\")\",5\r\n");   // công thức bị vô hiệu
    }

    @Test
    void response_laFileTaiVe_textCsvUtf8() {
        ResponseEntity<byte[]> response = CsvExport.response(report);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains("thu-nghiem.csv");
        assertThat(response.getHeaders().getContentType()).hasToString("text/csv;charset=UTF-8");
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).startsWith("﻿");
    }
}
