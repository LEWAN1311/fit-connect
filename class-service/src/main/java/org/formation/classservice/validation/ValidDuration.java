package org.formation.classservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

/** La duree d'un cours doit valoir 30, 45, 60 ou 90 minutes. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidDuration.Validator.class)
public @interface ValidDuration {

    String message() default "La duree doit etre de 30, 45, 60 ou 90 minutes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidDuration, Integer> {

        private static final Set<Integer> ALLOWED = Set.of(30, 45, 60, 90);

        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            return value == null || ALLOWED.contains(value);
        }
    }
}
