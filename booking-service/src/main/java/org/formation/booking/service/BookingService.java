package org.formation.booking.service;

import org.formation.booking.client.ClassServiceClient;
import org.formation.booking.client.NotificationServiceClient;
import org.formation.booking.client.PaymentServiceClient;
import org.formation.booking.dto.BookingRequest;
import org.formation.booking.dto.ConfirmPaymentRequest;
import org.formation.booking.dto.FitnessClassDto;
import org.formation.booking.dto.NotificationRequest;
import org.formation.booking.dto.NotificationType;
import org.formation.booking.dto.PaymentRequest;
import org.formation.booking.dto.PaymentResponse;
import org.formation.booking.entity.Booking;
import org.formation.booking.entity.BookingStatus;
import org.formation.booking.exception.ConflictException;
import org.formation.booking.exception.NoSpotsAvailableException;
import org.formation.booking.exception.PaymentFailedException;
import org.formation.booking.exception.ResourceNotFoundException;
import org.formation.booking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrateur du pattern Saga : reservation -> paiement -> confirmation / annulation.
 * Les appels distants sont faits AVANT la mise a jour locale, pour qu'une erreur
 * laisse la reservation dans son etat precedent et que l'operation puisse etre rejouee.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm");
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<BookingStatus> FINAL_STATUSES =
            EnumSet.of(BookingStatus.CANCELLED, BookingStatus.COMPLETED, BookingStatus.NO_SHOW);

    private final BookingRepository repository;
    private final ClassServiceClient classClient;
    private final PaymentServiceClient paymentClient;
    private final NotificationServiceClient notificationClient;
    private final long paymentDeadlineMinutes;
    private final long cancellationDeadlineHours;

    public BookingService(BookingRepository repository,
                          ClassServiceClient classClient,
                          PaymentServiceClient paymentClient,
                          NotificationServiceClient notificationClient,
                          @Value("${booking.payment-deadline-minutes:60}") long paymentDeadlineMinutes,
                          @Value("${booking.cancellation-deadline-hours:24}") long cancellationDeadlineHours) {
        this.repository = repository;
        this.classClient = classClient;
        this.paymentClient = paymentClient;
        this.notificationClient = notificationClient;
        this.paymentDeadlineMinutes = paymentDeadlineMinutes;
        this.cancellationDeadlineHours = cancellationDeadlineHours;
    }

    // ------------------------------------------------------------------ Lecture

    public List<Booking> findAll(BookingStatus status) {
        return status == null ? repository.findAll() : repository.findByStatusOrderByBookingDateDesc(status);
    }

    public Booking findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation introuvable : id=" + id));
    }

    public List<Booking> findByUser(Long userId) {
        return repository.findByUserIdOrderByBookingDateDesc(userId);
    }

    public List<Booking> findExpired() {
        return repository.findByStatusAndPaymentDeadlineBefore(BookingStatus.PENDING_PAYMENT, LocalDateTime.now());
    }

    // ------------------------------------------------------------------ Cas 1 / Cas 2 : reservation

    public Booking createBooking(BookingRequest request) {
        LocalDateTime now = LocalDateTime.now();

        // Etape 1 : verification du cours + snapshot
        FitnessClassDto fitnessClass = classClient.getClassById(request.classId());
        if (!"SCHEDULED".equals(fitnessClass.status())) {
            throw new ConflictException("Le cours n'est pas ouvert a la reservation (statut : "
                    + fitnessClass.status() + ")");
        }
        if (!fitnessClass.dateTime().isAfter(now)) {
            throw new ConflictException("Le cours est deja passe");
        }
        if (fitnessClass.availableSpots() < request.numberOfSpots()) {
            throw new NoSpotsAvailableException();
        }

        // Etape 2 : reservation des places (verrouillage optimiste cote class-service, 409 si conflit)
        classClient.incrementParticipants(fitnessClass.id(), request.numberOfSpots());

        // Etape 3 : creation de la reservation en attente de paiement
        Booking booking = new Booking();
        booking.setBookingReference(generateReference());
        booking.setUserId(request.userId());
        booking.setUserEmail(request.userEmail());
        booking.setUserName(request.userName());
        booking.setClassId(fitnessClass.id());
        booking.setClassName(fitnessClass.name());
        booking.setClassDate(fitnessClass.dateTime());
        booking.setInstructor(fitnessClass.instructor());
        booking.setPrice(fitnessClass.price());
        booking.setNumberOfSpots(request.numberOfSpots());
        booking.setTotalAmount(fitnessClass.price().multiply(BigDecimal.valueOf(request.numberOfSpots())));
        booking.setBookingDate(now);
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setPaymentDeadline(now.plusMinutes(paymentDeadlineMinutes));
        booking.setCancellationDeadline(fitnessClass.dateTime().minusHours(cancellationDeadlineHours));

        try {
            booking = repository.save(booking);
        } catch (RuntimeException e) {
            // Compensation : on rend les places reservees a l'etape 2
            log.error("Echec de persistance de la reservation, liberation de {} place(s) du cours {}",
                    request.numberOfSpots(), fitnessClass.id());
            classClient.decrementParticipants(fitnessClass.id(), request.numberOfSpots());
            throw e;
        }

        // Etape 4 : notification (non bloquante)
        notify(booking, NotificationType.BOOKING_CONFIRMATION,
                "Reservation " + booking.getBookingReference() + " en attente de paiement",
                "Votre réservation est en attente de paiement. Payez avant "
                        + DISPLAY.format(booking.getPaymentDeadline()));

        log.info("Reservation {} creee (cours {}, {} place(s), {} EUR)", booking.getBookingReference(),
                booking.getClassId(), booking.getNumberOfSpots(), booking.getTotalAmount());
        return booking;
    }

    // ------------------------------------------------------------------ Cas 3 : paiement

    public Booking confirmBooking(Long id, ConfirmPaymentRequest request) {
        Booking booking = findById(id);

        // Etape 1 : verification
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("La reservation " + booking.getBookingReference()
                    + " n'est pas en attente de paiement (statut : " + booking.getStatus() + ")");
        }
        if (LocalDateTime.now().isAfter(booking.getPaymentDeadline())) {
            throw new ConflictException("Paiement expire : le delai de paiement etait fixe au "
                    + DISPLAY.format(booking.getPaymentDeadline()));
        }

        // Etape 2 : traitement du paiement
        PaymentResponse payment = paymentClient.processPayment(new PaymentRequest(
                booking.getId(), booking.getBookingReference(), booking.getUserId(), booking.getTotalAmount(),
                request.paymentMethod(), request.cardLastFour(), request.transactionId()));

        if (!payment.isSuccess()) {
            // La reservation reste PENDING_PAYMENT : l'utilisateur peut retenter jusqu'a la deadline,
            // ensuite le scheduler l'annule et libere les places.
            log.info("Paiement {} refuse pour la reservation {}", payment.paymentReference(),
                    booking.getBookingReference());
            throw new PaymentFailedException("Paiement refuse (" + payment.paymentReference()
                    + "). La reservation reste en attente : vous pouvez reessayer avant le "
                    + DISPLAY.format(booking.getPaymentDeadline()));
        }

        // Etape 3 : mise a jour
        booking.setStatus(BookingStatus.CONFIRMED);
        booking = repository.save(booking);

        // Etape 4 : notification
        notify(booking, NotificationType.PAYMENT_CONFIRMATION,
                "Paiement recu - reservation " + booking.getBookingReference() + " confirmee",
                "Votre paiement de " + booking.getTotalAmount() + " EUR (" + payment.paymentReference()
                        + ") a été accepté. Rendez-vous le " + DISPLAY.format(booking.getClassDate())
                        + " pour le cours \"" + booking.getClassName() + "\".");
        return booking;
    }

    // ------------------------------------------------------------------ Cas 4 : annulation

    public Booking cancelBooking(Long id) {
        Booking booking = findById(id);

        // Etape 1 : verification
        if (FINAL_STATUSES.contains(booking.getStatus())) {
            throw new ConflictException("La reservation " + booking.getBookingReference()
                    + " ne peut plus etre annulee (statut : " + booking.getStatus() + ")");
        }
        if (LocalDateTime.now().isAfter(booking.getCancellationDeadline())) {
            throw new ConflictException("Annulation non autorisee : l'annulation gratuite n'est possible que "
                    + "jusqu'a " + cancellationDeadlineHours + "h avant le cours (limite : "
                    + DISPLAY.format(booking.getCancellationDeadline()) + ")");
        }

        // Etape 2 : remboursement (si paye) puis liberation des places
        boolean paid = booking.getStatus() == BookingStatus.CONFIRMED;
        if (paid) {
            refundPayment(booking);
        }
        classClient.decrementParticipants(booking.getClassId(), booking.getNumberOfSpots());

        // Etape 3 : annulation
        booking.setStatus(BookingStatus.CANCELLED);
        booking = repository.save(booking);

        // Etape 4 : notification
        notify(booking, NotificationType.BOOKING_CANCELLED,
                "Reservation " + booking.getBookingReference() + " annulee",
                "Votre réservation pour le cours \"" + booking.getClassName() + "\" du "
                        + DISPLAY.format(booking.getClassDate()) + " a été annulée."
                        + (paid ? " Votre paiement de " + booking.getTotalAmount() + " EUR a été remboursé." : ""));
        return booking;
    }

    public Booking completeBooking(Long id) {
        Booking booking = findById(id);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Seule une reservation confirmee peut etre terminee (statut : "
                    + booking.getStatus() + ")");
        }
        booking.setStatus(BookingStatus.COMPLETED);
        return repository.save(booking);
    }

    // ------------------------------------------------------------------ Scheduler

    /** Annule les reservations dont le delai de paiement est depasse et libere les places. */
    public int expirePendingBookings() {
        int cancelled = 0;
        for (Booking booking : findExpired()) {
            try {
                classClient.decrementParticipants(booking.getClassId(), booking.getNumberOfSpots());
                booking.setStatus(BookingStatus.CANCELLED);
                repository.save(booking);
                notify(booking, NotificationType.BOOKING_CANCELLED,
                        "Reservation " + booking.getBookingReference() + " annulee",
                        "Votre réservation a été annulée automatiquement : le paiement n'a pas été reçu avant le "
                                + DISPLAY.format(booking.getPaymentDeadline()) + ".");
                cancelled++;
            } catch (RuntimeException e) {
                // On reessaiera au prochain passage du scheduler
                log.warn("Expiration de la reservation {} impossible pour l'instant : {}",
                        booking.getBookingReference(), e.getMessage());
            }
        }
        if (cancelled > 0) {
            log.info("{} reservation(s) expiree(s) annulee(s)", cancelled);
        }
        return cancelled;
    }

    /** Envoie un rappel BOOKING_REMINDER pour les cours confirmes ayant lieu dans les prochaines 24h. */
    public int sendClassReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> upcoming = repository.findByStatusAndReminderSentFalseAndClassDateBetween(
                BookingStatus.CONFIRMED, now, now.plusHours(24));
        for (Booking booking : upcoming) {
            notify(booking, NotificationType.BOOKING_REMINDER,
                    "Rappel : " + booking.getClassName() + " demain",
                    "Votre cours \"" + booking.getClassName() + "\" avec " + booking.getInstructor()
                            + " a lieu le " + DISPLAY.format(booking.getClassDate()) + ".");
            booking.setReminderSent(true);
            repository.save(booking);
        }
        return upcoming.size();
    }

    // ------------------------------------------------------------------ Utilitaires

    private void refundPayment(Booking booking) {
        PaymentResponse payment = paymentClient.getPaymentByBooking(booking.getId());
        if (payment.isRefunded()) {
            // Deja rembourse lors d'une tentative d'annulation precedente interrompue
            return;
        }
        paymentClient.refund(payment.id());
        log.info("Paiement {} rembourse pour la reservation {}", payment.paymentReference(),
                booking.getBookingReference());
    }

    private void notify(Booking booking, NotificationType type, String subject, String content) {
        try {
            notificationClient.send(new NotificationRequest(booking.getUserId(), booking.getUserEmail(),
                    type, subject, content));
        } catch (RuntimeException e) {
            log.warn("Notification {} non envoyee pour {} : {}", type, booking.getBookingReference(),
                    e.getMessage());
        }
    }

    private static String generateReference() {
        StringBuilder sb = new StringBuilder("BK-");
        for (int i = 0; i < 5; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
