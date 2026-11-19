/** Ảnh sau khi thu nhỏ, sẵn sàng gửi lên backend. */
export interface PreparedPhoto {
  contentType: 'image/jpeg';
  /** data URL — vừa để xem trước, vừa gửi lên (backend tự bỏ tiền tố "data:...;base64,"). */
  dataUrl: string;
}

const MAX_SIDE = 1280;
const QUALITY = 0.8;

/**
 * Ảnh chụp từ máy tính bảng thường 3–8 MB; thu nhỏ cạnh dài về 1280 px, nén JPEG còn khoảng 150–300 KB
 * trước khi gửi (backend nhận tối đa 2 MB). Đọc ảnh bằng canvas nên mọi định dạng trình duyệt mở được đều dùng được.
 */
export async function resizeImage(file: File): Promise<PreparedPhoto> {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  const context = canvas.getContext('2d');
  if (!context) throw new Error('Trình duyệt không hỗ trợ xử lý ảnh');
  context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  return { contentType: 'image/jpeg', dataUrl: canvas.toDataURL('image/jpeg', QUALITY) };
}
