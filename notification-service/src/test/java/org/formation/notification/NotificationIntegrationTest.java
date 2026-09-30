package org.formation.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.formation.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void shouldSendNotification_andListItForUser() throws Exception {
        String body = """
                {"userId":1,"email":"john@example.com","type":"BOOKING_CONFIRMATION",
                 "subject":"Reservation BK-ABCDE","content":"Votre reservation est en attente de paiement"}
                """;
        mockMvc.perform(post("/api/notifications").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sentDate").exists());

        mockMvc.perform(get("/api/notifications/user/{userId}", 1))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/notifications/pending"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldMarkFailed_whenNoRecipient_andRetryStillFails() throws Exception {
        String body = """
                {"userId":2,"type":"BOOKING_REMINDER","subject":"Rappel","content":"Votre cours est demain"}
                """;
        String response = mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(patch("/api/notifications/{id}/retry", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void shouldRejectRetry_whenAlreadySent() throws Exception {
        String body = """
                {"userId":3,"email":"jane@example.com","type":"PAYMENT_CONFIRMATION",
                 "subject":"Paiement","content":"Paiement recu"}
                """;
        String response = mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(patch("/api/notifications/{id}/retry", id))
                .andExpect(status().isConflict());
    }
}
