package org.formation.booking.client;

import org.formation.booking.dto.FitnessClassDto;
import org.formation.booking.exception.ConflictException;
import org.formation.booking.exception.NoSpotsAvailableException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class ClassServiceClientFallbackFactory implements FallbackFactory<ClassServiceClient> {

    private static final String SERVICE = "class-service";

    @Override
    public ClassServiceClient create(Throwable cause) {
        return new ClassServiceClient() {
            @Override
            public FitnessClassDto getClassById(Long id) {
                throw RemoteErrorTranslator.translate(cause, SERVICE);
            }

            @Override
            public FitnessClassDto incrementParticipants(Long id, int spots) {
                RuntimeException error = RemoteErrorTranslator.translate(cause, SERVICE);
                // 409 sur increment = capacite atteinte ou conflit de version (reservation concurrente)
                if (error instanceof ConflictException) {
                    throw new NoSpotsAvailableException(NoSpotsAvailableException.DEFAULT_MESSAGE
                            + " (" + error.getMessage() + ")");
                }
                throw error;
            }

            @Override
            public FitnessClassDto decrementParticipants(Long id, int spots) {
                throw RemoteErrorTranslator.translate(cause, SERVICE);
            }
        };
    }
}
