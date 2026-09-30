package org.formation.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.formation.booking.client.ClassServiceClient;
import org.formation.booking.client.NotificationServiceClient;
import org.formation.booking.client.PaymentServiceClient;
import org.formation.booking.dto.FitnessClassDto;
import org.formation.booking.dto.NotificationType;
import org.formation.booking.dto.PaymentResponse;
import org.formation.booking.entity.Booking;
import org.formation.booking.entity.BookingStatus;
import org.formation.booking.exception.NoSpotsAvailableException;
import org.formation.booking.exception.ServiceUnavailableException;
import org.formation.booking.repository.BookingRepository;
import org.formation.booking.scheduler.BookingScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration du Saga : vraie couche web + JPA de booking-service,
 * les services distants (class, payment, notification) sont simules.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingFlowIntegrationTest {

    private static final long CLASS_ID = 101L;
    private static final int MAX_PARTICIPANTS = 10;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BookingRepository repository;
    @Autowired
    private BookingScheduler scheduler;

    @MockBean
    private ClassServiceClient classClient;
    @MockBean
    private PaymentServiceClient paymentClient;
    @MockBean
    private NotificationServiceClient notificationClient;

    /** Etat simule de class-service. */
    private final AtomicInteger currentParticipants = new AtomicInteger();
    private LocalDateTime classDate;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        currentParticipants.set(0);
        classDate = LocalDateTime.now().plusDays(3).truncatedTo(ChronoUnit.SECONDS);

        // 1. Create class (class-service simule en memoire)
        when(classClient.getClassById(CLASS_ID)).thenAnswer(invocation -> fitnessClass());
        when(classClient.incrementParticipants(eq(CLASS_ID), anyInt())).thenAnswer(invocation -> {
            int spots = invocation.getArgument(1);
            if (currentParticipants.get() + spots > MAX_PARTICIPANTS) {
                throw new NoSpotsAvailableException();
            }
            currentParticipants.addAndGet(spots);
            return fitnessClass();
        });
        when(classClient.decrementParticipants(eq(CLASS_ID), anyInt())).thenAnswer(invocation -> {
            int spots = invocation.getArgument(1);
            currentParticipants.updateAndGet(current -> Math.max(0, current - spots));
            return fitnessClass();
        });
    }

    @Test
    @Transactional
    void shouldCompleteFullBookingFlow() throws Exception {
        // 2. Create booking
        long bookingId = createBooking(2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalAmount").value(30.0))
                .andReturn().getResponse().getContentAsString()
                .transform(this::readId);

        // 3. Confirm payment
        when(paymentClient.processPayment(any())).thenReturn(
                new PaymentResponse(1L, "PAY-ABCDE", bookingId, new BigDecimal("30.00"), "SUCCESS"));
        mockMvc.perform(patch("/api/bookings/{id}/confirm", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CREDIT_CARD","cardLastFour":"1234","transactionId":"txn_123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // 4. Verify booking status = CONFIRMED
        mockMvc.perform(get("/api/bookings/{id}", bookingId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // 5. Verify spots decreased (places disponibles : 10 -> 8)
        assertThat(MAX_PARTICIPANTS - currentParticipants.get()).isEqualTo(8);
        verify(paymentClient).processPayment(argThat(p -> p.bookingId() == bookingId
                && p.amount().compareTo(new BigDecimal("30.00")) == 0));

        // 6. Verify notification sent
        verify(notificationClient).send(argThat(n -> n.type() == NotificationType.BOOKING_CONFIRMATION
                && "john@example.com".equals(n.email())));
        verify(notificationClient).send(argThat(n -> n.type() == NotificationType.PAYMENT_CONFIRMATION));
    }

    @Test
    void shouldCancelExpiredBookings() throws Exception {
        // 1. Create booking with paymentDeadline in past
        long bookingId = createBooking(2).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()
                .transform(this::readId);
        Booking booking = repository.findById(bookingId).orElseThrow();
        booking.setPaymentDeadline(LocalDateTime.now().minusMinutes(5));
        repository.save(booking);
        assertThat(currentParticipants.get()).isEqualTo(2);
        mockMvc.perform(get("/api/bookings/expired"))
                .andExpect(jsonPath("$.length()").value(1));

        // 2. Run scheduler
        scheduler.expirePendingPayments();

        // 3. Verify status = CANCELLED
        assertThat(repository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);

        // 4. Verify spots restored
        assertThat(currentParticipants.get()).isZero();
        verify(notificationClient).send(argThat(n -> n.type() == NotificationType.BOOKING_CANCELLED));
    }

    @Test
    void shouldCancelAndRefund_throughApi() throws Exception {
        long bookingId = createBooking(2).andReturn().getResponse().getContentAsString()
                .transform(this::readId);
        when(paymentClient.processPayment(any())).thenReturn(
                new PaymentResponse(7L, "PAY-ABCDE", bookingId, new BigDecimal("30.00"), "SUCCESS"));
        when(paymentClient.getPaymentByBooking(bookingId)).thenReturn(
                new PaymentResponse(7L, "PAY-ABCDE", bookingId, new BigDecimal("30.00"), "SUCCESS"));
        mockMvc.perform(patch("/api/bookings/{id}/confirm", bookingId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethod\":\"PAYPAL\"}"));

        mockMvc.perform(patch("/api/bookings/{id}/cancel", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(paymentClient).refund(7L);
        assertThat(currentParticipants.get()).isZero();

        // Une seconde annulation est refusee
        mockMvc.perform(patch("/api/bookings/{id}/cancel", bookingId))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn409_whenOverbooking() throws Exception {
        currentParticipants.set(9);

        createBooking(2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Plus de places disponibles pour ce cours"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void shouldReturn402_andKeepPendingPayment_whenPaymentRefused() throws Exception {
        long bookingId = createBooking(2).andReturn().getResponse().getContentAsString()
                .transform(this::readId);
        when(paymentClient.processPayment(any())).thenReturn(
                new PaymentResponse(2L, "PAY-FAIL1", bookingId, new BigDecimal("30.00"), "FAILED"));

        mockMvc.perform(patch("/api/bookings/{id}/confirm", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CREDIT_CARD\",\"cardLastFour\":\"4242\"}"))
                .andExpect(status().isPaymentRequired());

        mockMvc.perform(get("/api/bookings/{id}", bookingId))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void shouldReturn409_whenCancellingLessThan24hBeforeClass() throws Exception {
        classDate = LocalDateTime.now().plusHours(5).truncatedTo(ChronoUnit.SECONDS);
        long bookingId = createBooking(1).andReturn().getResponse().getContentAsString()
                .transform(this::readId);

        mockMvc.perform(patch("/api/bookings/{id}/cancel", bookingId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Annulation non autorisee")));
        verify(classClient, never()).decrementParticipants(anyLong(), anyInt());
    }

    @Test
    void shouldReturn503_whenClassServiceUnavailable() throws Exception {
        when(classClient.getClassById(CLASS_ID)).thenThrow(
                new ServiceUnavailableException("Le service class-service est indisponible", null));

        createBooking(2).andExpect(status().isServiceUnavailable());
    }

    @Test
    void shouldReturn400_whenBookingRequestInvalid() throws Exception {
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"userEmail\":\"pas-un-email\",\"classId\":101,\"numberOfSpots\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.userEmail").exists())
                .andExpect(jsonPath("$.details.userName").exists())
                .andExpect(jsonPath("$.details.numberOfSpots").exists());
    }

    private ResultActions createBooking(int spots) throws Exception {
        String body = """
                {"userId":1,"userEmail":"john@example.com","userName":"John Doe","classId":%d,"numberOfSpots":%d}
                """.formatted(CLASS_ID, spots);
        return mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private FitnessClassDto fitnessClass() {
        return new FitnessClassDto(CLASS_ID, "Yoga Vinyasa", "Marie", classDate, new BigDecimal("15.00"),
                MAX_PARTICIPANTS, currentParticipants.get(), "SCHEDULED");
    }

    private long readId(String json) {
        try {
            return objectMapper.readTree(json).get("id").asLong();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
