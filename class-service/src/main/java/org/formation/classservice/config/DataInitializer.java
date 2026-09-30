package org.formation.classservice.config;

import org.formation.classservice.entity.Category;
import org.formation.classservice.entity.FitnessClass;
import org.formation.classservice.entity.Level;
import org.formation.classservice.repository.FitnessClassRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Jeu de donnees de demonstration (dates relatives a maintenant pour rester reservables). */
@Configuration
@Profile("!test")
public class DataInitializer {

    @Bean
    CommandLineRunner seedClasses(FitnessClassRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            LocalDateTime base = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
            repository.saveAll(List.of(
                    build("Yoga Vinyasa", "Enchainements fluides pour tous", "Marie", "Paris 11e",
                            Category.YOGA, Level.BEGINNER, 60, 20, "15.00", base.plusDays(3).withHour(18)),
                    build("CrossFit WOD", "Entrainement fonctionnel haute intensite", "Lucas", "Lyon Part-Dieu",
                            Category.CROSSFIT, Level.ADVANCED, 45, 12, "20.00", base.plusDays(4).withHour(7)),
                    build("Zumba Party", "Cardio en musique", "Sofia", "Paris 15e",
                            Category.ZUMBA, Level.INTERMEDIATE, 60, 25, "12.00", base.plusDays(5).withHour(19)),
                    build("Boxe anglaise", "Technique et sparring leger", "Karim", "Marseille Vieux-Port",
                            Category.BOXING, Level.INTERMEDIATE, 90, 10, "25.00", base.plusDays(6).withHour(20))));
        };
    }

    private static FitnessClass build(String name, String description, String instructor, String location,
                                      Category category, Level level, int duration, int max, String price,
                                      LocalDateTime dateTime) {
        FitnessClass fitnessClass = new FitnessClass();
        fitnessClass.setName(name);
        fitnessClass.setDescription(description);
        fitnessClass.setInstructor(instructor);
        fitnessClass.setGymLocation(location);
        fitnessClass.setCategory(category);
        fitnessClass.setLevel(level);
        fitnessClass.setDurationMinutes(duration);
        fitnessClass.setMaxParticipants(max);
        fitnessClass.setCurrentParticipants(0);
        fitnessClass.setPrice(new BigDecimal(price));
        fitnessClass.setDateTime(dateTime);
        return fitnessClass;
    }
}
