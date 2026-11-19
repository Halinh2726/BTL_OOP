package com.ptit.parking.gate.domain;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * Ảnh chụp đính kèm sự cố (value object). Chỉ nhận JPG / PNG / WEBP, tối đa {@link #MAX_BYTES}.
 * <p>Không tin loại ảnh client khai báo: kiểm tra "chữ ký" ở đầu file (magic bytes) có đúng loại đó không —
 * một file .exe đổi tên thành .jpg sẽ bị từ chối.
 */
public final class IncidentPhoto {

    public static final int MAX_BYTES = 2 * 1024 * 1024;

    /** Chữ ký đầu file của từng loại ảnh được nhận. */
    private static final Map<String, byte[]> SIGNATURES = Map.of(
            "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'},
            "image/webp", new byte[] {'R', 'I', 'F', 'F'});

    private final String contentType;
    private final byte[] data;

    public IncidentPhoto(String contentType, byte[] data) {
        byte[] signature = SIGNATURES.get(contentType);
        if (signature == null || data == null || data.length == 0 || data.length > MAX_BYTES
                || !startsWith(data, signature) || ("image/webp".equals(contentType) && !isWebp(data))) {
            throw new DomainException(ErrorCode.PHOTO_INVALID);
        }
        this.contentType = contentType;
        this.data = data.clone();                         // sao chép: bên ngoài không sửa được ảnh đã kiểm tra
    }

    /** Ảnh gửi lên dạng Base64 (có thể kèm tiền tố "data:image/jpeg;base64,"). */
    public static IncidentPhoto fromBase64(String contentType, String base64) {
        String payload = base64 == null ? "" : base64.substring(base64.indexOf(',') + 1).strip();
        try {
            return new IncidentPhoto(contentType, Base64.getDecoder().decode(payload));
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.PHOTO_INVALID);
        }
    }

    public String contentType() { return contentType; }

    public byte[] data() { return data.clone(); }

    public int size() { return data.length; }

    /** "RIFF" còn là đầu file âm thanh WAV, video AVI: WEBP phải có thêm chữ "WEBP" ở byte 8–11. */
    private static boolean isWebp(byte[] data) {
        byte[] marker = {'W', 'E', 'B', 'P'};
        return data.length >= 12 && Arrays.equals(data, 8, 12, marker, 0, 4);
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }
}
