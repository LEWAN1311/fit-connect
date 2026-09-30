package org.formation.booking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Desactivable (booking.scheduler.enabled=false), notamment pour les tests. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "booking.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {
}
