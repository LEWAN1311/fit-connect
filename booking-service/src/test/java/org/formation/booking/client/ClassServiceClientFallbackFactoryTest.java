package org.formation.booking.client;

import feign.FeignException;
import feign.Request;
import feign.Response;
import org.formation.booking.exception.NoSpotsAvailableException;
import org.formation.booking.exception.ResourceNotFoundException;
import org.formation.booking.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassServiceClientFallbackFactoryTest {

    private final ClassServiceClientFallbackFactory factory = new ClassServiceClientFallbackFactory();

    @Test
    void shouldTranslate409OnIncrement_intoNoSpotsAvailable() {
        FeignException conflict = feignError(409, "{\"message\":\"Plus de places disponibles\"}");

        assertThatThrownBy(() -> factory.create(conflict).incrementParticipants(1L, 2))
                .isInstanceOf(NoSpotsAvailableException.class)
                .hasMessageContaining("Plus de places disponibles");
    }

    @Test
    void shouldTranslate404_intoResourceNotFound_withRemoteMessage() {
        FeignException notFound = feignError(404, "{\"message\":\"Cours introuvable : id=1\"}");

        assertThatThrownBy(() -> factory.create(notFound).getClassById(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Cours introuvable : id=1");
    }

    @Test
    void shouldTranslateTechnicalFailure_intoServiceUnavailable() {
        assertThatThrownBy(() -> factory.create(new ConnectException("Connection refused")).getClassById(1L))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThatThrownBy(() -> factory.create(feignError(500, "")).decrementParticipants(1L, 1))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    private static FeignException feignError(int status, String body) {
        Request request = Request.create(Request.HttpMethod.PATCH, "http://class-service/api/classes/1",
                Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .status(status)
                .reason("error")
                .request(request)
                .headers(Map.of())
                .body(body, StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("ClassServiceClient#call", response);
    }
}
