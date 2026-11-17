/**
 * Phát âm thanh phản hồi bằng Web Audio API (không cần tải file âm thanh ngoài).
 * Tạo trải nghiệm tương tự máy quẹt thẻ xe bus / tàu điện khi vào và ra bãi xe.
 */

function getAudioContext(): AudioContext | null {
  try {
    const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    return AudioContextClass ? new AudioContextClass() : null;
  } catch {
    return null;
  }
}

/** Âm "Bíp" cao trong trẻo báo quét thành công / trừ điểm hợp lệ. */
export function playSuccessSound(): void {
  const ctx = getAudioContext();
  if (!ctx) return;
  try {
    const now = ctx.currentTime;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = 'sine';
    // Chime 2 nốt: A5 (880Hz) -> E6 (1318.5Hz)
    osc.frequency.setValueAtTime(880, now);
    osc.frequency.exponentialRampToValueAtTime(1318.5, now + 0.08);

    gain.gain.setValueAtTime(0.25, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.16);

    osc.connect(gain);
    gain.connect(ctx.destination);

    osc.start(now);
    osc.stop(now + 0.16);
  } catch {
    // Trình duyệt có thể chặn khi chưa có tương tác người dùng, bỏ qua an toàn
  }
}

/** Âm báo trầm cảnh báo: lỗi, không đủ điểm hoặc biển số không hợp lệ. */
export function playErrorSound(): void {
  const ctx = getAudioContext();
  if (!ctx) return;
  try {
    const now = ctx.currentTime;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = 'sawtooth';
    // 2 nhịp trầm: 350Hz -> 220Hz
    osc.frequency.setValueAtTime(350, now);
    osc.frequency.setValueAtTime(220, now + 0.12);

    gain.gain.setValueAtTime(0.3, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.28);

    osc.connect(gain);
    gain.connect(ctx.destination);

    osc.start(now);
    osc.stop(now + 0.28);
  } catch {
    // Bỏ qua nếu audio context bị hạn chế
  }
}

