package org.formation.payment.controller;

import jakarta.validation.Valid;
import org.formation.payment.dto.PaymentRequest;
import org.formation.payment.entity.Payment;
import org.formation.payment.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    /** Appele par booking-service. Renvoie 201 avec status SUCCESS ou FAILED. */
    @PostMapping
    public ResponseEntity<Payment> process(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.process(request));
    }

    @GetMapping("/{id}")
    public Payment findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/booking/{bookingId}")
    public Payment findByBooking(@PathVariable Long bookingId) {
        return service.findByBooking(bookingId);
    }

    @PostMapping("/{id}/refund")
    public Payment refund(@PathVariable Long id) {
        return service.refund(id);
    }

    @GetMapping("/user/{userId}")
    public List<Payment> findByUser(@PathVariable Long userId) {
        return service.findByUser(userId);
    }
}
