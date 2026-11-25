import type { GuestLookup } from '../types/guest';

const TICKET_TOKEN = /^[a-f0-9]{32}$/;

export function guestTokenFromQr(payload: string): string | null {
  const value = payload.trim();
  if (value.startsWith('PTIT-GUEST:')) {
    const token = value.slice('PTIT-GUEST:'.length);
    return TICKET_TOKEN.test(token) ? token : null;
  }
  if (!/^https?:\/\//i.test(value)) return null;
  try {
    const path = new URL(value).pathname;
    return /^\/ve\/([a-f0-9]{32})$/.exec(path)?.[1] ?? null;
  } catch {
    return null;
  }
}

export function guestLookup(raw: string): GuestLookup {
  const token = guestTokenFromQr(raw);
  if (token) return { ticketToken: token };
  const value = raw.trim().toUpperCase();
  return /^K-\d{6}-\d{4}$/.test(value) ? { ticketCode: value } : { plateNumber: value };
}
