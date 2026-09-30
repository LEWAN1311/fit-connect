package org.formation.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.formation.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void shouldAcceptPayment_whenAmountBelow100() throws Exception {
        pay(1L, "30.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentReference", startsWith("PAY-")))
                .andExpect(jsonPath("$.cardLastFour").value("1234"));
    }

    @Test
    void shouldRefusePayment_whenAmountIs100OrMore() throws Exception {
        pay(2L, "100.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void shouldRejectSecondPayment_whenBookingAlreadyPaid() throws Exception {
        pay(3L, "30.00").andExpect(status().isCreated());
        pay(3L, "30.00").andExpect(status().isConflict());
    }

    @Test
    void shouldRefundSuccessfulPayment_onlyOnce() throws Exception {
        String response = pay(4L, "45.00").andReturn().getResponse().getContentAsString();
        long paymentId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/payments/{id}/refund", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        mockMvc.perform(post("/api/payments/{id}/refund", paymentId))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/payments/booking/{bookingId}", 4L))
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        mockMvc.perform(get("/api/payments/user/{userId}", 1L))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldRequireCardDigits_forCardPayment() throws Exception {
        String body = """
                {"bookingId":5,"userId":1,"amount":20.00,"paymentMethod":"CREDIT_CARD"}
                """;
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    private ResultActions pay(long bookingId, String amount) throws Exception {
        String body = """
                {"bookingId":%d,"bookingReference":"BK-TEST%d","userId":1,"amount":%s,
                 "paymentMethod":"CREDIT_CARD","cardLastFour":"1234","transactionId":"txn_123456"}
                """.formatted(bookingId, bookingId, amount);
        return mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
