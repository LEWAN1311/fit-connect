package org.formation.classservice.repository;

import jakarta.persistence.criteria.Predicate;
import org.formation.classservice.dto.ClassFilter;
import org.formation.classservice.entity.FitnessClass;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class FitnessClassSpecifications {

    private FitnessClassSpecifications() {
    }

    public static Specification<FitnessClass> withFilter(ClassFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.category() != null) {
                predicates.add(cb.equal(root.get("category"), filter.category()));
            }
            if (filter.level() != null) {
                predicates.add(cb.equal(root.get("level"), filter.level()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dateTime"), filter.dateFrom().atStartOfDay()));
            }
            if (filter.dateTo() != null) {
                // dateTo inclusive : jusqu'a la fin de la journee
                predicates.add(cb.lessThan(root.get("dateTime"), filter.dateTo().plusDays(1).atStartOfDay()));
            }
            if (hasText(filter.location())) {
                predicates.add(cb.like(cb.lower(root.get("gymLocation")), containsPattern(filter.location())));
            }
            if (hasText(filter.instructor())) {
                predicates.add(cb.like(cb.lower(root.get("instructor")), containsPattern(filter.instructor())));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String containsPattern(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }
}
