package org.formation.classservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.formation.classservice.entity.FitnessClass;
import org.formation.classservice.repository.FitnessClassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FitnessClassIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FitnessClassRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void shouldCreateClass_andFilterByCategoryAndLevel() throws Exception {
        long id = createClass(10);

        mockMvc.perform(get("/api/classes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentParticipants").value(0))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        mockMvc.perform(get("/api/classes").param("category", "YOGA").param("level", "BEGINNER")
                        .param("location", "paris"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        mockMvc.perform(get("/api/classes").param("category", "BOXING"))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void shouldRejectInvalidClass() throws Exception {
        String invalid = """
                {"name":"Yo","description":"d","instructor":"Marie","gymLocation":"Paris",
                 "category":"YOGA","level":"BEGINNER","durationMinutes":50,"maxParticipants":40,
                 "price":2.00,"dateTime":"2020-01-01T10:00:00"}
                """;
        mockMvc.perform(post("/api/classes").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.details.durationMinutes").exists())
                .andExpect(jsonPath("$.details.maxParticipants").exists())
                .andExpect(jsonPath("$.details.price").exists())
                .andExpect(jsonPath("$.details.dateTime").exists());
    }

    @Test
    void shouldPartiallyUpdateClass_withPatch() throws Exception {
        long id = createClass(10);

        mockMvc.perform(patch("/api/classes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":22.50,\"instructor\":\"Sophie\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(22.5))
                .andExpect(jsonPath("$.instructor").value("Sophie"))
                // Les champs non envoyes restent inchanges
                .andExpect(jsonPath("$.name").value("Yoga Vinyasa"))
                .andExpect(jsonPath("$.description").value("Cours de yoga"))
                .andExpect(jsonPath("$.category").value("YOGA"))
                .andExpect(jsonPath("$.maxParticipants").value(10))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void shouldRejectInvalidPartialUpdate_andKeepClassUnchanged() throws Exception {
        long id = createClass(10);
        String invalid = """
                {"name":"Yo","description":"   ","durationMinutes":50,"maxParticipants":40,"price":1.00,
                 "dateTime":"2020-01-01T10:00:00"}
                """;

        mockMvc.perform(patch("/api/classes/{id}", id).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.details.description").exists())
                .andExpect(jsonPath("$.details.durationMinutes").exists())
                .andExpect(jsonPath("$.details.maxParticipants").exists())
                .andExpect(jsonPath("$.details.price").exists())
                .andExpect(jsonPath("$.details.dateTime").exists());

        mockMvc.perform(get("/api/classes/{id}", id))
                .andExpect(jsonPath("$.name").value("Yoga Vinyasa"))
                .andExpect(jsonPath("$.price").value(15.0));
    }

    @Test
    void shouldReturn409_whenPatchReducesMaxParticipantsBelowCurrent() throws Exception {
        long id = createClass(10);
        mockMvc.perform(patch("/api/classes/{id}/increment", id).param("spots", "6"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/classes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxParticipants\":5}"))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/classes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxParticipants\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSpots").value(0));
    }

    @Test
    void shouldNoLongerAcceptPut() throws Exception {
        long id = createClass(10);

        mockMvc.perform(put("/api/classes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":20.00}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void shouldIncrementThenReturn409_whenClassIsFull() throws Exception {
        long id = createClass(5);

        mockMvc.perform(patch("/api/classes/{id}/increment", id).param("spots", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentParticipants").value(4));

        mockMvc.perform(patch("/api/classes/{id}/increment", id).param("spots", "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("places")));

        mockMvc.perform(patch("/api/classes/{id}/decrement", id).param("spots", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentParticipants").value(0));
    }

    @Test
    void shouldRejectConcurrentUpdate_thanksToOptimisticLocking() throws Exception {
        long id = createClass(10);

        // Deux "transactions" lisent la meme version du cours
        FitnessClass firstReader = repository.findById(id).orElseThrow();
        FitnessClass secondReader = repository.findById(id).orElseThrow();

        firstReader.incrementParticipants(2);
        repository.saveAndFlush(firstReader);

        // La seconde ecriture repose sur une version perimee : rejetee par JPA
        secondReader.incrementParticipants(2);
        assertThatThrownBy(() -> repository.saveAndFlush(secondReader))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(repository.findById(id).orElseThrow().getCurrentParticipants()).isEqualTo(2);
    }

    private long createClass(int maxParticipants) throws Exception {
        String body = """
                {"name":"Yoga Vinyasa","description":"Cours de yoga","instructor":"Marie",
                 "gymLocation":"Paris 11e","category":"YOGA","level":"BEGINNER","durationMinutes":60,
                 "maxParticipants":%d,"price":15.00,"dateTime":"%s"}
                """.formatted(maxParticipants, LocalDateTime.now().plusDays(3).truncatedTo(ChronoUnit.SECONDS));
        String response = mockMvc.perform(post("/api/classes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("id").asLong();
    }
}
