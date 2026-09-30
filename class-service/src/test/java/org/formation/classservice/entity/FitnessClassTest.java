package org.formation.classservice.entity;

import org.formation.classservice.exception.NoSpotsAvailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FitnessClassTest {

    @Test
    void shouldIncrementParticipants_whenSpotsAvailable() {
        FitnessClass fitnessClass = classWith(10, 5);

        fitnessClass.incrementParticipants(2);

        assertThat(fitnessClass.getCurrentParticipants()).isEqualTo(7);
        assertThat(fitnessClass.getAvailableSpots()).isEqualTo(3);
    }

    @Test
    void shouldThrowException_whenNoSpotsAvailable() {
        FitnessClass fitnessClass = classWith(10, 9);

        assertThatThrownBy(() -> fitnessClass.incrementParticipants(2))
                .isInstanceOf(NoSpotsAvailableException.class);
        assertThat(fitnessClass.getCurrentParticipants()).isEqualTo(9);
    }

    @Test
    void shouldNeverGoBelowZero_whenDecrementing() {
        FitnessClass fitnessClass = classWith(10, 1);

        fitnessClass.decrementParticipants(3);

        assertThat(fitnessClass.getCurrentParticipants()).isZero();
    }

    private static FitnessClass classWith(int max, int current) {
        FitnessClass fitnessClass = new FitnessClass();
        fitnessClass.setMaxParticipants(max);
        fitnessClass.setCurrentParticipants(current);
        return fitnessClass;
    }
}
