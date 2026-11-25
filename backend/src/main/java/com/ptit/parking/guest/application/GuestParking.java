package com.ptit.parking.guest.application;

import com.ptit.parking.catalog.application.port.out.ExchangeRateRepository;
import com.ptit.parking.catalog.application.port.out.FeeRuleRepository;
import com.ptit.parking.catalog.domain.CustomerType;
import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.gate.application.usecase.ReportIncidentUseCase;
import com.ptit.parking.gate.domain.IncidentType;
import com.ptit.parking.guest.application.GuestParkingStore.Entry;
import com.ptit.parking.guest.application.GuestParkingStore.Page;
import com.ptit.parking.guest.application.GuestParkingStore.Revenue;
import com.ptit.parking.guest.application.GuestParkingStore.Search;
import com.ptit.parking.guest.application.GuestParkingStore.Settings;
import com.ptit.parking.guest.application.GuestParkingStore.VietQrQuote;
import com.ptit.parking.guest.domain.GuestPayment;
import com.ptit.parking.guest.domain.GuestPayment.Method;
import com.ptit.parking.guest.domain.GuestSession;
import com.ptit.parking.parking.application.port.out.OpenShiftLookup;
import com.ptit.parking.parking.application.port.out.ParkingPolicyRepository;
import com.ptit.parking.parking.application.port.out.VietQrGenerator;
import com.ptit.parking.parking.domain.DurationFeePolicy;
import com.ptit.parking.parking.domain.ParkingPolicyConfig;
import com.ptit.parking.parking.domain.VietQrOrder;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.application.port.out.VehicleRepository;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class GuestParking {
    private static final Duration VIETQR_QUOTE_DURATION = Duration.ofMinutes(15);
    public record EntryPreview(boolean allowed, List<FeeRule> rules, int cashPerPoint) {}

    public record Lookup(String ticketToken, String ticketCode, String plateNumber) {}

    public record Quote(long feePoints, long amount, long lostTicketFee, long minutesParked,
                        boolean overnight, boolean checkoutLocked, LocalTime checkoutCurfewTime) {}

    public record CheckoutPreview(GuestSession session, Quote quote, int lostTicketFeeVnd) {}

    public record Checkout(long sessionId, Lookup ticket, String observedPlate, String documentLast4,
                           long expectedAmount, UUID quoteId) {}

    public record VietQrPayment(UUID quoteId, Instant expiresAt, VietQrOrder order) {}

    public record Receipt(String ticketCode, GuestPayment payment, boolean lostTicket, boolean replayed) {}

    public record PublicTicket(Instant enteredAt, Instant exitedAt, long minutesParked, long amount,
                               boolean overnight, boolean checkoutLocked, LocalTime checkoutCurfewTime) {}

    private final GuestParkingStore store;
    private final VehicleRepository vehicles;
    private final FeeRuleRepository tariffs;
    private final ExchangeRateRepository rates;
    private final ParkingPolicyRepository policies;
    private final OpenShiftLookup shifts;
    private final VietQrGenerator vietQr;
    private final ReportIncidentUseCase incidents;
    private final TransactionRunner transactions;
    private final Clock clock;

    public GuestParking(GuestParkingStore store, VehicleRepository vehicles, FeeRuleRepository tariffs,
                        ExchangeRateRepository rates, ParkingPolicyRepository policies, OpenShiftLookup shifts,
                        VietQrGenerator vietQr, ReportIncidentUseCase incidents, TransactionRunner transactions,
                        Clock clock) {
        this.store = store;
        this.vehicles = vehicles;
        this.tariffs = tariffs;
        this.rates = rates;
        this.policies = policies;
        this.shifts = shifts;
        this.vietQr = vietQr;
        this.incidents = incidents;
        this.transactions = transactions;
        this.clock = clock;
    }

    public EntryPreview previewEntry(Entry entry) {
        return previewEntry(entry, false);
    }

    private EntryPreview previewEntry(Entry entry, boolean lockPlate) {
        if (!store.settings().enabled()) {
            throw new DomainException(ErrorCode.GUEST_PARKING_DISABLED);
        }
        List<FeeRule> rules = rules(entry.vehicleTypeCode());
        if (entry.plate() == null && !"XE_DAP".equals(entry.vehicleTypeCode())) {
            throw new DomainException(ErrorCode.PLATE_REQUIRED, "Chỉ xe đạp được gửi không có biển số.");
        }
        if (entry.plate() != null) {
            boolean studentPlate = lockPlate ? vehicles.plateExistsForUpdate(entry.plate()) : vehicles.plateExists(entry.plate());
            if (studentPlate) {
                throw new DomainException(ErrorCode.GUEST_PLATE_BELONGS_TO_STUDENT);
            }
            if (store.findOpenByPlate(entry.plate()).isPresent()) {
                throw new DomainException(ErrorCode.GUEST_SESSION_ALREADY_OPEN);
            }
        }
        return new EntryPreview(true, rules, rates.currentExchangeRate().cashPerPoint());
    }

    public GuestSession checkIn(long guardId, Entry entry) {
        return transactions.inTransaction(() -> {
            previewEntry(entry, true);
            Instant now = now();
            LocalDate date = now.atZone(clock.getZone()).toLocalDate();
            int number = store.nextTicketNumber(date);
            if (number > 9_999) {
                throw new DomainException(ErrorCode.GUEST_TICKET_LIMIT);
            }
            return store.enter(entry, GuestSession.ticketCode(date, number), GuestSession.newToken(), guardId, now);
        });
    }

    public CheckoutPreview previewCheckout(Lookup lookup) {
        GuestSession session = open(find(lookup));
        return new CheckoutPreview(session, quote(session, now(), 0), store.settings().lostTicketFeeVnd());
    }

    public VietQrPayment vietQr(long sessionId, Lookup ticket, String observedPlate) {
        return transactions.inTransaction(() -> {
            GuestSession session = open(store.lockById(sessionId).orElseThrow(GuestParking::missing));
            verifyVehicle(session, observedPlate);
            requireTicket(session, ticket);
            Instant now = now();
            requireCheckoutOpen(now);
            VietQrQuote price = store.vietQrQuote(sessionId).filter(quote -> quote.expiresAt().isAfter(now))
                    .orElseGet(() -> {
                        Quote quote = quote(session, now, 0);
                        VietQrQuote created = new VietQrQuote(UUID.randomUUID(), quote.feePoints(), quote.amount(),
                                quote.overnight(), now.plus(VIETQR_QUOTE_DURATION));
                        store.saveVietQrQuote(sessionId, created);
                        return created;
                    });
            VietQrOrder order = vietQr.generateGuest(session.id(), session.plate() == null ? null : session.plate().display(),
                    session.ticketCode(), price.amount());
            return new VietQrPayment(price.id(), price.expiresAt(), order);
        });
    }

    public Receipt checkout(long guardId, UUID key, Method method, boolean lost, Checkout request) {
        return transactions.inTransaction(() -> {
            GuestSession session = store.lockById(request.sessionId()).orElseThrow(GuestParking::missing);
            Optional<GuestPayment> existing = store.paymentByKey(key);
            if (existing.isPresent()) {
                GuestPayment payment = existing.get();
                if (payment.guardId() != guardId || payment.sessionId() != session.id()
                        || payment.method() != method || session.lostTicket() != lost
                        || payment.amount() != request.expectedAmount()) {
                    throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
                }
                return new Receipt(session.ticketCode(), payment, lost, true);
            }
            open(session);
            verifyVehicle(session, request.observedPlate());
            verifyProof(session, lost, request);

            Instant now = now();
            requireCheckoutOpen(now);
            long lostFee = lost ? store.settings().lostTicketFeeVnd() : 0;
            long feePoints;
            long amount;
            boolean overnight;
            if (method == Method.VIETQR) {
                VietQrQuote quote = store.vietQrQuote(session.id()).filter(item -> item.id().equals(request.quoteId()))
                        .orElseThrow(() -> new DomainException(ErrorCode.GUEST_AMOUNT_CHANGED));
                feePoints = quote.feePoints();
                amount = quote.amount();
                overnight = quote.overnight();
            } else {
                Quote quote = quote(session, now, lostFee);
                feePoints = quote.feePoints();
                amount = quote.amount();
                overnight = quote.overnight();
            }
            if (amount != request.expectedAmount()) {
                throw new DomainException(ErrorCode.GUEST_AMOUNT_CHANGED);
            }
            Long shift = method == Method.TIEN_MAT ? shifts.findOpenShiftId(guardId).orElse(null) : null;
            GuestPayment payment = new GuestPayment(session.id(), guardId, shift, method, feePoints,
                    amount, overnight, lostFee, key, now);
            store.close(session.id(), guardId, now, lost ? request.documentLast4() : null);
            store.insertPayment(payment);
            if (lost) {
                incidents.report(new ReportIncidentUseCase.Command(guardId, IncidentType.MAT_VE,
                        session.plate() == null ? null : session.plate().value(),
                        "Khách mất vé " + session.ticketCode() + ". Đã đối chiếu giấy tờ tại chốt.", null, null));
            }
            return new Receipt(session.ticketCode(), payment, lost, false);
        });
    }

    public void approveLostTicket(long sessionId, long managerId) {
        transactions.inTransaction(() -> {
            GuestSession session = open(store.lockById(sessionId).orElseThrow(GuestParking::missing));
            if (session.plate() != null) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR, "Chỉ duyệt mất vé xe đạp không biển.");
            }
            if (!session.lostTicketApproved()) {
                store.approveLostTicket(session.id(), managerId);
            }
            return null;
        });
    }

    public PublicTicket publicTicket(String token) {
        return transactions.inTransaction(() -> {
            GuestSession session = tokenLookup(token).orElseThrow(GuestParking::missing);
            if (session.isOpen()) {
                Quote quote = quote(session, now(), 0);
                return new PublicTicket(session.enteredAt(), null, quote.minutesParked(), quote.amount(),
                        quote.overnight(), quote.checkoutLocked(), quote.checkoutCurfewTime());
            }
            GuestPayment payment = store.paymentForSession(session.id()).orElseThrow(GuestParking::missing);
            return new PublicTicket(session.enteredAt(), session.exitedAt(),
                    elapsed(session, session.exitedAt()).toMinutes(), payment.amount(), payment.overnight(), false, null);
        });
    }

    public Settings settings() {
        return store.settings();
    }

    public Settings updateSettings(Settings settings) {
        store.saveSettings(settings);
        return settings;
    }

    public Page search(LocalDate from, LocalDate to, String status, String plate, int limit, int offset) {
        if (from != null || to != null) {
            requireDateRange(from, to);
        }
        if (!status.isEmpty() && !List.of("DANG_GUI", "DA_RA").contains(status)) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Trạng thái lượt gửi không hợp lệ.");
        }
        return store.search(new Search(from == null ? null : startOf(from), to == null ? null : startOf(to.plusDays(1)),
                status, plateFragment(plate), limit, offset, null));
    }

    public Page parked(String plate, int limit, int offset) {
        return store.search(new Search(null, null, "DANG_GUI", plateFragment(plate), limit, offset,
                now().minus(Duration.ofDays(1))));
    }

    public Revenue revenue(LocalDate from, LocalDate to) {
        requireDateRange(from, to);
        return store.revenue(startOf(from), startOf(to.plusDays(1)));
    }

    public static PlateNumber parsePlate(String raw) {
        return raw == null || raw.isBlank() ? null : PlateNumber.tryParse(raw)
                .orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_ERROR, "Biển số không hợp lệ."));
    }

    private static String plateFragment(String plate) {
        String fragment = PlateNumber.normalize(plate);
        if (!fragment.matches("[A-Z0-9Đ]{0,20}")) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Biển số tìm kiếm không hợp lệ.");
        }
        return fragment;
    }

    private GuestSession find(Lookup lookup) {
        return (lookup.ticketToken() != null ? tokenLookup(lookup.ticketToken())
                : lookup.ticketCode() != null ? store.findByCode(lookup.ticketCode().strip().toUpperCase(Locale.ROOT))
                : Optional.ofNullable(parsePlate(lookup.plateNumber())).flatMap(store::findOpenByPlate))
                .orElseThrow(GuestParking::missing);
    }

    private Optional<GuestSession> tokenLookup(String token) {
        return token.matches("[a-f0-9]{32}") ? store.findByToken(token) : Optional.empty();
    }

    private void verifyProof(GuestSession session, boolean lost, Checkout request) {
        if (!lost) {
            requireTicket(session, request.ticket());
            return;
        }
        if (session.plate() == null && !session.lostTicketApproved()) {
            throw new DomainException(ErrorCode.GUEST_LOST_TICKET_REQUIRES_MANAGER);
        }
        if (request.documentLast4() == null || !request.documentLast4().matches("[0-9]{4}")) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Cần nhập đúng 4 số cuối giấy tờ đã đối chiếu.");
        }
    }

    private void requireTicket(GuestSession session, Lookup ticket) {
        if (ticket == null || (ticket.ticketToken() == null && ticket.ticketCode() == null)
                || find(ticket).id() != session.id()) {
            throw missing();
        }
    }

    private static void verifyVehicle(GuestSession session, String actualPlate) {
        if (!java.util.Objects.equals(session.plate(), parsePlate(actualPlate))) {
            throw new DomainException(ErrorCode.GUEST_TICKET_PLATE_MISMATCH);
        }
    }

    private Quote quote(GuestSession session, Instant now, long lostFee) {
        ZonedDateTime localNow = now.atZone(clock.getZone());
        ParkingPolicyConfig policy = policies.current();
        boolean overnight = policy.isOvernight(session.enteredAt(), localNow, clock.getZone());
        Duration duration = elapsed(session, now);
        long points = overnight ? policy.overnightFeePoints()
                : new DurationFeePolicy(rules(session.vehicleTypeCode())).feeFor(duration);
        long amount = Math.addExact(Math.multiplyExact(points, rates.currentExchangeRate().cashPerPoint()), lostFee);
        return new Quote(points, amount, lostFee, duration.toMinutes(), overnight,
                policy.isCurfewActive(localNow, clock.getZone()), policy.curfewEnabled() ? policy.checkoutCurfewTime() : null);
    }

    private List<FeeRule> rules(String vehicleType) {
        List<FeeRule> rules = tariffs.rulesForVehicleType(CustomerType.KHACH, vehicleType);
        if (rules.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Loại xe chưa có bảng giá khách.");
        }
        return rules;
    }

    private static Duration elapsed(GuestSession session, Instant now) {
        return Duration.between(session.enteredAt(), now.isBefore(session.enteredAt()) ? session.enteredAt() : now);
    }

    private static GuestSession open(GuestSession session) {
        if (!session.isOpen()) {
            throw new DomainException(ErrorCode.SESSION_ALREADY_PAID);
        }
        return session;
    }

    private void requireCheckoutOpen(Instant now) {
        if (policies.current().isCurfewActive(now.atZone(clock.getZone()), clock.getZone())) {
            throw new DomainException(ErrorCode.CHECKOUT_CURFEW);
        }
    }

    private static DomainException missing() {
        return new DomainException(ErrorCode.GUEST_TICKET_NOT_FOUND);
    }

    private static void requireDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= 93) {
            throw new DomainException(ErrorCode.DATE_RANGE_INVALID);
        }
    }

    private Instant startOf(LocalDate date) {
        return date.atStartOfDay(clock.getZone()).toInstant();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
