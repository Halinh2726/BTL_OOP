/**
 * Âm thanh và rung khi có kết quả — bảo vệ không cần nhìn màn hình vẫn biết:
 * - "allow": hai tiếng cao ngắn + rung ngắn  → được qua
 * - "deny" : một tiếng trầm dài + rung ba nhịp → bị từ chối
 * - "done" : một tiếng cao rất ngắn            → đã xác nhận xong
 * Chỉ là phản hồi phụ trợ: thiết bị không hỗ trợ (không có Web Audio / rung) thì im lặng, không báo lỗi.
 */
export type FeedbackKind = 'allow' | 'deny' | 'done';

interface Tone {
  frequency: number;
  durationMs: number;
  delayMs: number;
}

const TONES: Record<FeedbackKind, Tone[]> = {
  allow: [
    { frequency: 880, durationMs: 120, delayMs: 0 },
    { frequency: 1320, durationMs: 160, delayMs: 150 },
  ],
  deny: [{ frequency: 220, durationMs: 600, delayMs: 0 }],
  done: [{ frequency: 1046, durationMs: 90, delayMs: 0 }],
};

const VIBRATION: Record<FeedbackKind, number[]> = {
  allow: [120],
  deny: [200, 100, 200, 100, 200],
  done: [60],
};

let audioContext: AudioContext | null = null;

function context(): AudioContext | null {
  try {
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return null;
    audioContext = audioContext ?? new Ctor();
    if (audioContext.state === 'suspended') void audioContext.resume();
    return audioContext;
  } catch {
    return null;
  }
}

export function playFeedback(kind: FeedbackKind, options: { sound: boolean; vibrate: boolean }): void {
  if (options.vibrate && typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
    navigator.vibrate(VIBRATION[kind]);
  }
  if (!options.sound) return;
  const ctx = context();
  if (!ctx) return;
  for (const tone of TONES[kind]) {
    const start = ctx.currentTime + tone.delayMs / 1000;
    const oscillator = ctx.createOscillator();
    const gain = ctx.createGain();
    oscillator.type = kind === 'deny' ? 'square' : 'sine';
    oscillator.frequency.value = tone.frequency;
    gain.gain.setValueAtTime(0.25, start);
    gain.gain.exponentialRampToValueAtTime(0.001, start + tone.durationMs / 1000);
    oscillator.connect(gain).connect(ctx.destination);
    oscillator.start(start);
    oscillator.stop(start + tone.durationMs / 1000);
  }
}
