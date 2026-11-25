import type { FeeRule, VehicleTypeFeeSchedule, VietQrOrder } from './api';
import type { Idempotent } from '../api/ApiClient';

export interface GuestEntry {
  plateNumber?: string;
  vehicleTypeCode: string;
  guestName?: string;
  guestPhone?: string;
  note?: string;
}

export interface GuestEntryPreview {
  allowed: boolean;
  rules: FeeRule[];
  cashPerPoint: number;
}

export interface GuestTicket {
  sessionId: number;
  ticketCode: string;
  ticketToken: string;
  qrPayload: string;
  plateDisplay: string | null;
  vehicleTypeCode: string;
  enteredAt: string;
}

export interface GuestLookup {
  ticketToken?: string;
  ticketCode?: string;
  plateNumber?: string;
}

export interface GuestCheckoutPreview extends ParkedGuest {
  quote: {
    feePoints: number;
    amount: number;
    minutesParked: number;
    overnight: boolean;
    checkoutLocked: boolean;
    checkoutCurfewTime: string | null;
  };
  lostTicketFeeVnd: number;
  lostTicketApproved: boolean;
}

export interface GuestCheckout {
  sessionId: number;
  ticket?: GuestLookup;
  observedPlate?: string;
  documentLast4?: string;
  expectedAmount: number;
  quoteId?: string;
}

export type GuestMethod = 'TIEN_MAT' | 'VIETQR';
export type GuestPaymentRoute = 'cash' | 'vietqr' | 'lost-ticket';

export interface GuestReceipt {
  ticketCode: string;
  method: GuestMethod;
  amount: number;
  lostTicketFee: number;
  overnight: boolean;
  lostTicket: boolean;
  paidAt: string;
}

export interface ParkedGuest {
  sessionId: number;
  plateNumber: string | null;
  plateDisplay: string | null;
  vehicleTypeCode: string;
  enteredAt: string;
}

export interface GuestConfig {
  enabled: boolean;
  lostTicketFeeVnd: number;
}

export interface GuestRevenue {
  payments: number;
  cash: number;
  vietqr: number;
}

export interface GuestSessionItem extends Omit<ParkedGuest, 'sessionId'> {
  id: number;
  ticketCode: string;
  exitedAt: string | null;
  guestName: string | null;
  guestPhone: string | null;
  note: string | null;
  lostTicket: boolean;
  lostTicketApproved: boolean;
  paymentMethod: GuestMethod | null;
  amount: number | null;
}

export interface GuestSearch {
  from: string;
  to: string;
  status: string;
  plate: string;
}

export interface PublicGuestTicket {
  enteredAt: string;
  exitedAt: string | null;
  minutesParked: number;
  amount: number;
  overnight: boolean;
  checkoutLocked: boolean;
  checkoutCurfewTime: string | null;
}

export interface GuestApi {
  previewEntry(body: GuestEntry): Promise<GuestEntryPreview>;
  checkIn(body: GuestEntry): Promise<GuestTicket>;
  previewCheckout(body: GuestLookup): Promise<GuestCheckoutPreview>;
  vietQr(sessionId: number, body: Pick<GuestCheckout, 'ticket' | 'observedPlate'>): Promise<{
    quoteId: string;
    expiresAt: string;
    order: VietQrOrder;
  }>;
  checkout(route: GuestPaymentRoute, body: GuestCheckout, key: string): Promise<Idempotent<GuestReceipt>>;
  parked(plate: string, offset: number): Promise<{ items: ParkedGuest[]; total: number; overdue: number }>;
  publicTicket(token: string): Promise<PublicGuestTicket>;
  settings(): Promise<GuestConfig>;
  saveSettings(body: GuestConfig): Promise<GuestConfig>;
  search(filters: GuestSearch, offset: number): Promise<{ items: GuestSessionItem[]; total: number; revenue: GuestRevenue }>;
  exportSessions(filters: GuestSearch): Promise<Blob>;
  approveLostTicket(id: number): Promise<void>;
  revenue(from: string, to: string): Promise<GuestRevenue>;
  feeCatalog(): Promise<{ schedules: VehicleTypeFeeSchedule[] }>;
  updateFeeRules(vehicleTypeCode: string, rules: FeeRule[]): Promise<{ schedules: VehicleTypeFeeSchedule[] }>;
}
