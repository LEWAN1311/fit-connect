package org.formation.booking.service;

import org.formation.booking.client.ClassServiceClient;
import org.formation.booking.client.NotificationServiceClient;
import org.formation.booking.client.PaymentServiceClient;
import org.formation.booking.dto.BookingRequest;
import org.formation.booking.dto.ConfirmPaymentRequest;
import org.formation.booking.dto.FitnessClassDto;
import org.formation.booking.dto.NotificationType;
import org.formation.booking.dto.PaymentMethod;
import org.formation.booking.dto.PaymentResponse;
import org.formation.booking.entity.Booking;
import org.formation.booking.entity.BookingStatus;
import org.formation.booking.exception.ConflictException;
import org.formation.booking.exception.NoSpotsAvailableException;
import org.formation.booking.exception.PaymentFailedException;
import org.formation.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final long CLASS_ID = 101L;
    private static final LocalDateTime CLASS_DATE = LocalDateTime.now().plusDays(3).truncatedTo(ChronoUnit.MINUTES);

    @Mock
    private BookingRepository repository;
    @Mock
    private ClassServiceClient classClient;
    @Mock
    private PaymentServiceClient paymentClient;
    @Mock
    private NotificationServiceClient notificationClient;

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(repository, classClient, paymentClient, notificationClient, 60, 24);
    }

    @Test
    void shouldCreateBooking_whenSpotsAvailable() {
        // Given: class with 10 spots, 5 current participants
        AtomicInteger currentParticipants = new AtomicInteger(5);
        when(classClient.getClassById(CLASS_ID)).thenReturn(fitnessClass(10, currentParticipants.get()));
        when(classClient.incrementParticipants(CLASS_ID, 2)).thenAnswer(invocation -> {
            currentParticipants.addAndGet(2);
            return fitnessClass(10, currentParticipants.get());
        });
        when(repository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: booking 2 spots
        Booking booking = service.createBooking(bookingRequest(2));

        // Then: status = PENDING_PAYMENT, spots = 7
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(currentParticipants.get()).isEqualTo(7);
        assertThat(booking.getBookingReference()).matches("BK-[A-Z0-9]{5}");
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("30.00");
        assertThat(booking.getClassName()).isEqualTo("Yoga Vinyasa");
        assertThat(booking.getPaymentDeadline()).isEqualTo(booking.getBookingDate().plusHours(1));
        assertThat(booking.getCancellationDeadline()).isEqualTo(CLASS_DATE.minusHours(24));
        verify(notificationClient).send(argThat(n -> n.type() == NotificationType.BOOKING_CONFIRMATION
                && n.content().contains("Payez avant")));
    }

    @Test
    void shouldThrowException_whenNoSpotsAvailable() {
        // Given: class with 10 spots, 9 current participants
        when(classClient.getClassById(CLASS_ID)).thenReturn(fitnessClass(10, 9));

        // When: booking 2 spots / Then: NoSpotsAvailableException
        assertThatThrownBy(() -> service.createBooking(bookingRequest(2)))
                .isInstanceOf(NoSpotsAvailableException.class);
        verify(classClient, never()).incrementParticipants(anyLong(), anyInt());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowException_whenConcurrentBookingTookLastSpots() {
        // Given: places visibles a l'etape 1, mais quelqu'un a reserve entre-temps (class-service repond 409)
        when(classClient.getClassById(CLASS_ID)).thenReturn(fitnessClass(10, 8));
        when(classClient.incrementParticipants(CLASS_ID, 2)).thenThrow(new NoSpotsAvailableException());

        // Then: 409, compensation inutile car aucune reservation n'a ete creee
        assertThatThrownBy(() -> service.createBooking(bookingRequest(2)))
                .isInstanceOf(NoSpotsAvailableException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldCancelBookingAndRefund_whenWithinDeadline() {
        // Given: confirmed booking, cancellationDeadline in future
        Booking booking = existingBooking(BookingStatus.CONFIRMED, CLASS_DATE);
        when(repository.findById(1L)).thenReturn(Optional.of(booking));
        when(paymentClient.getPaymentByBooking(1L))
                .thenReturn(new PaymentResponse(55L, "PAY-AB123", 1L, new BigDecimal("30.00"), "SUCCESS"));
        when(repository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: cancel booking
        Booking cancelled = service.cancelBooking(1L);

        // Then: status = CANCELLED, payment refunded, spots decreased
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(paymentClient).refund(55L);
        verify(classClient).decrementParticipants(CLASS_ID, 2);
        verify(notificationClient).send(argThat(n -> n.type() == NotificationType.BOOKING_CANCELLED));
    }

    @Test
    void shouldRejectCancellation_whenDeadlinePassed() {
        // Cours dans 10h : la limite d'annulation gratuite (J-24h) est depassee
        Booking booking = existingBooking(BookingStatus.CONFIRMED, LocalDateTime.now().plusHours(10));
        when(repository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancelBooking(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Annulation non autorisee");
        verify(paymentClient, never()).refund(anyLong());
        verify(classClient, never()).decrementParticipants(anyLong(), anyInt());
    }

    @Test
    void shouldConfirmBooking_whenPaymentSucceeds() {
        Booking booking = existingBooking(BookingStatus.PENDING_PAYMENT, CLASS_DATE);
        when(repository.findById(1L)).thenReturn(Optional.of(booking));
        when(paymentClient.processPayment(any()))
                .thenReturn(new PaymentResponse(55L, "PAY-AB123", 1L, new BigDecimal("30.00"), "SUCCESS"));
        when(repository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking confirmed = service.confirmBooking(1L, cardPayment());

        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(paymentClient).processPayment(argThat(p -> p.bookingId().equals(1L)
                && p.amount().compareTo(new BigDecimal("30.00")) == 0));
        verify(notificationClient).send(argThat(n -> n.type() == NotificationType.PAYMENT_CONFIRMATION));
    }

    @Test
    void shouldKeepPendingPayment_whenPaymentRefused() {
        Booking booking = existingBooking(BookingStatus.PENDING_PAYMENT, CLASS_DATE);
        when(repository.findById(1L)).thenReturn(Optional.of(booking));
        when(paymentClient.processPayment(any()))
                .thenReturn(new PaymentResponse(56L, "PAY-CD456", 1L, new BigDecimal("30.00"), "FAILED"));

        assertThatThrownBy(() -> service.confirmBooking(1L, cardPayment()))
                .isInstanceOf(PaymentFailedException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectConfirmation_whenPaymentDeadlineExpired() {
        Booking booking = existingBooking(BookingStatus.PENDING_PAYMENT, CLASS_DATE);
        booking.setPaymentDeadline(LocalDateTime.now().minusMinutes(1));
        when(repository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.confirmBooking(1L, cardPayment()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Paiement expire");
        verify(paymentClient, never()).processPayment(any());
    }

    private static FitnessClassDto fitnessClass(int max, int current) {
        return new FitnessClassDto(CLASS_ID, "Yoga Vinyasa", "Marie", CLASS_DATE, new BigDecimal("15.00"),
                max, current, "SCHEDULED");
    }

    private static BookingRequest bookingRequest(int spots) {
        return new BookingRequest(1L, "john@example.com", "John Doe", CLASS_ID, spots);
    }

    private static ConfirmPaymentRequest cardPayment() {
        return new ConfirmPaymentRequest(PaymentMethod.CREDIT_CARD, "1234", "txn_123456");
    }

    private static Booking existingBooking(BookingStatus status, LocalDateTime classDate) {
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setBookingReference("BK-TEST1");
        booking.setUserId(1L);
        booking.setUserEmail("john@example.com");
        booking.setUserName("John Doe");
        booking.setClassId(CLASS_ID);
        booking.setClassName("Yoga Vinyasa");
        booking.setClassDate(classDate);
        booking.setInstructor("Marie");
        booking.setPrice(new BigDecimal("15.00"));
        booking.setNumberOfSpots(2);
        booking.setTotalAmount(new BigDecimal("30.00"));
        booking.setBookingDate(LocalDateTime.now().minusMinutes(10));
        booking.setPaymentDeadline(LocalDateTime.now().plusMinutes(50));
        booking.setCancellationDeadline(classDate.minusHours(24));
        booking.setStatus(status);
        return booking;
    }
}
