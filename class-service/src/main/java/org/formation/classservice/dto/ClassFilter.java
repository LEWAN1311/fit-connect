package org.formation.classservice.dto;

import org.formation.classservice.entity.Category;
import org.formation.classservice.entity.ClassStatus;
import org.formation.classservice.entity.Level;

import java.time.LocalDate;

/** Criteres de filtre optionnels pour la liste / recherche de cours. */
public record ClassFilter(
        Category category,
        Level level,
        LocalDate dateFrom,
        LocalDate dateTo,
        String location,
        String instructor,
        ClassStatus status) {
}
