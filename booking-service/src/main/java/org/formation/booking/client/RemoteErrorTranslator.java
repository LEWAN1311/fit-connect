package org.formation.booking.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.formation.booking.exception.BadRequestException;
import org.formation.booking.exception.ConflictException;
import org.formation.booking.exception.ResourceNotFoundException;
import org.formation.booking.exception.ServiceUnavailableException;

/**
 * Traduit l'erreur recue par un fallback de circuit breaker en exception metier :
 * <ul>
 *     <li>erreur fonctionnelle du service distant (4xx) : propagee avec son message (404, 409, 400)</li>
 *     <li>service injoignable, 5xx, timeout ou circuit ouvert : {@link ServiceUnavailableException} (503)</li>
 * </ul>
 */
public final class RemoteErrorTranslator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RemoteErrorTranslator() {
    }

    public static RuntimeException translate(Throwable cause, String serviceName) {
        FeignException feignException = findFeignException(cause);
        if (feignException == null || feignException.status() < 400 || feignException.status() >= 500) {
            return new ServiceUnavailableException(
                    "Le service " + serviceName + " est indisponible, veuillez reessayer plus tard", cause);
        }
        String message = extractMessage(feignException);
        return switch (feignException.status()) {
            case 404 -> new ResourceNotFoundException(message);
            case 409 -> new ConflictException(message);
            default -> new BadRequestException(message);
        };
    }

    private static FeignException findFeignException(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof FeignException feignException) {
                return feignException;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String extractMessage(FeignException exception) {
        try {
            String body = exception.contentUTF8();
            if (body != null && !body.isBlank()) {
                JsonNode message = MAPPER.readTree(body).get("message");
                if (message != null && !message.isNull()) {
                    return message.asText();
                }
            }
        } catch (Exception ignored) {
            // corps non JSON : on retombe sur le message Feign
        }
        return exception.getMessage();
    }
}
