package org.formation.booking.client;

import org.formation.booking.dto.FitnessClassDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "class-service", fallbackFactory = ClassServiceClientFallbackFactory.class)
public interface ClassServiceClient {

    @GetMapping("/api/classes/{id}")
    FitnessClassDto getClassById(@PathVariable("id") Long id);

    @PatchMapping("/api/classes/{id}/increment")
    FitnessClassDto incrementParticipants(@PathVariable("id") Long id, @RequestParam("spots") int spots);

    @PatchMapping("/api/classes/{id}/decrement")
    FitnessClassDto decrementParticipants(@PathVariable("id") Long id, @RequestParam("spots") int spots);
}
