package org.formation.classservice.service;

import org.formation.classservice.dto.ClassFilter;
import org.formation.classservice.dto.FitnessClassCreateRequest;
import org.formation.classservice.dto.FitnessClassUpdateRequest;
import org.formation.classservice.entity.ClassStatus;
import org.formation.classservice.entity.FitnessClass;
import org.formation.classservice.exception.ConflictException;
import org.formation.classservice.exception.ResourceNotFoundException;
import org.formation.classservice.repository.FitnessClassRepository;
import org.formation.classservice.repository.FitnessClassSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.function.Consumer;

@Service
public class FitnessClassService {

    private static final Logger log = LoggerFactory.getLogger(FitnessClassService.class);

    private final FitnessClassRepository repository;

    public FitnessClassService(FitnessClassRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<FitnessClass> findAll(ClassFilter filter, Pageable pageable) {
        return repository.findAll(FitnessClassSpecifications.withFilter(filter), pageable);
    }

    @Transactional(readOnly = true)
    public FitnessClass findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cours introuvable : id=" + id));
    }

    @Transactional
    public FitnessClass create(FitnessClassCreateRequest request) {
        FitnessClass fitnessClass = new FitnessClass();
        fitnessClass.setName(request.name());
        fitnessClass.setDescription(request.description());
        fitnessClass.setInstructor(request.instructor());
        fitnessClass.setGymLocation(request.gymLocation());
        fitnessClass.setCategory(request.category());
        fitnessClass.setLevel(request.level());
        fitnessClass.setDurationMinutes(request.durationMinutes());
        fitnessClass.setMaxParticipants(request.maxParticipants());
        fitnessClass.setPrice(request.price());
        fitnessClass.setDateTime(request.dateTime());
        fitnessClass.setCurrentParticipants(0);
        fitnessClass.setStatus(ClassStatus.SCHEDULED);
        return repository.save(fitnessClass);
    }

    /** Mise a jour partielle : seuls les champs non null de la requete sont appliques. */
    @Transactional
    public FitnessClass update(Long id, FitnessClassUpdateRequest request) {
        FitnessClass fitnessClass = findById(id);
        if (request.maxParticipants() != null
                && request.maxParticipants() < fitnessClass.getCurrentParticipants()) {
            throw new ConflictException("maxParticipants (" + request.maxParticipants()
                    + ") ne peut pas etre inferieur au nombre de participants inscrits ("
                    + fitnessClass.getCurrentParticipants() + ")");
        }
        setIfPresent(request.name(), fitnessClass::setName);
        setIfPresent(request.description(), fitnessClass::setDescription);
        setIfPresent(request.instructor(), fitnessClass::setInstructor);
        setIfPresent(request.gymLocation(), fitnessClass::setGymLocation);
        setIfPresent(request.category(), fitnessClass::setCategory);
        setIfPresent(request.level(), fitnessClass::setLevel);
        setIfPresent(request.durationMinutes(), fitnessClass::setDurationMinutes);
        setIfPresent(request.maxParticipants(), fitnessClass::setMaxParticipants);
        setIfPresent(request.price(), fitnessClass::setPrice);
        setIfPresent(request.dateTime(), fitnessClass::setDateTime);
        setIfPresent(request.status(), fitnessClass::setStatus);
        return repository.save(fitnessClass);
    }

    /**
     * Supprime le cours s'il n'a aucun inscrit, sinon l'annule (status CANCELLED)
     * afin de conserver la coherence avec les reservations existantes.
     */
    @Transactional
    public void deleteOrCancel(Long id) {
        FitnessClass fitnessClass = findById(id);
        if (fitnessClass.getCurrentParticipants() == 0) {
            repository.delete(fitnessClass);
            log.info("Cours {} supprime", id);
        } else {
            fitnessClass.setStatus(ClassStatus.CANCELLED);
            repository.save(fitnessClass);
            log.info("Cours {} annule ({} participants inscrits)", id, fitnessClass.getCurrentParticipants());
        }
    }

    /**
     * Appele par booking-service. Le @Version de FitnessClass garantit qu'une mise a jour
     * concurrente provoque une ObjectOptimisticLockingFailureException (409) au lieu d'une surreservation.
     */
    @Transactional
    public FitnessClass incrementParticipants(Long id, int spots) {
        requirePositive(spots);
        FitnessClass fitnessClass = findById(id);
        if (fitnessClass.getStatus() != ClassStatus.SCHEDULED) {
            throw new ConflictException("Le cours n'est pas ouvert a la reservation (statut : "
                    + fitnessClass.getStatus() + ")");
        }
        if (fitnessClass.getDateTime().isBefore(LocalDateTime.now())) {
            throw new ConflictException("Le cours est deja passe");
        }
        fitnessClass.incrementParticipants(spots);
        return repository.saveAndFlush(fitnessClass);
    }

    @Transactional
    public FitnessClass decrementParticipants(Long id, int spots) {
        requirePositive(spots);
        FitnessClass fitnessClass = findById(id);
        fitnessClass.decrementParticipants(spots);
        return repository.saveAndFlush(fitnessClass);
    }

    private static void requirePositive(int spots) {
        if (spots < 1) {
            throw new IllegalArgumentException("Le nombre de places doit etre superieur ou egal a 1");
        }
    }

    private static <T> void setIfPresent(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
