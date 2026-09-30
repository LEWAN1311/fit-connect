package org.formation.classservice.repository;

import org.formation.classservice.entity.FitnessClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FitnessClassRepository extends JpaRepository<FitnessClass, Long>,
        JpaSpecificationExecutor<FitnessClass> {
}
