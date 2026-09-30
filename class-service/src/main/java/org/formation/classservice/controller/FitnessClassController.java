package org.formation.classservice.controller;

import jakarta.validation.Valid;
import org.formation.classservice.dto.ClassFilter;
import org.formation.classservice.dto.FitnessClassRequest;
import org.formation.classservice.entity.Category;
import org.formation.classservice.entity.ClassStatus;
import org.formation.classservice.entity.FitnessClass;
import org.formation.classservice.entity.Level;
import org.formation.classservice.service.FitnessClassService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/classes")
public class FitnessClassController {

    private final FitnessClassService service;

    public FitnessClassController(FitnessClassService service) {
        this.service = service;
    }

    /** Ex : ?category=YOGA&level=BEGINNER&dateFrom=2026-09-10&dateTo=2026-09-20&location=Paris&page=0&size=10&sort=dateTime,asc */
    @GetMapping
    public Page<FitnessClass> findAll(
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Level level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String instructor,
            @RequestParam(required = false) ClassStatus status,
            @PageableDefault(size = 10, sort = "dateTime", direction = Sort.Direction.ASC) Pageable pageable) {
        ClassFilter filter = new ClassFilter(category, level, dateFrom, dateTo, location, instructor, status);
        return service.findAll(filter, pageable);
    }

    /** Recherche par date (jour precis), categorie, niveau, localisation. */
    @GetMapping("/search")
    public Page<FitnessClass> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Level level,
            @RequestParam(required = false) String location,
            @PageableDefault(size = 10, sort = "dateTime", direction = Sort.Direction.ASC) Pageable pageable) {
        ClassFilter filter = new ClassFilter(category, level, date, date, location, null, null);
        return service.findAll(filter, pageable);
    }

    @GetMapping("/{id}")
    public FitnessClass findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<FitnessClass> create(@Valid @RequestBody FitnessClassRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public FitnessClass update(@PathVariable Long id, @Valid @RequestBody FitnessClassRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteOrCancel(id);
        return ResponseEntity.noContent().build();
    }

    /** Appele par booking-service. */
    @PatchMapping("/{id}/increment")
    public FitnessClass increment(@PathVariable Long id, @RequestParam(defaultValue = "1") int spots) {
        return service.incrementParticipants(id, spots);
    }

    /** Appele par booking-service. */
    @PatchMapping("/{id}/decrement")
    public FitnessClass decrement(@PathVariable Long id, @RequestParam(defaultValue = "1") int spots) {
        return service.decrementParticipants(id, spots);
    }
}
