package org.formation.booking.controller;

import jakarta.validation.Valid;
import org.formation.booking.dto.BookingRequest;
import org.formation.booking.dto.ConfirmPaymentRequest;
import org.formation.booking.entity.Booking;
import org.formation.booking.entity.BookingStatus;
import org.formation.booking.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @GetMapping
    public List<Booking> findAll(@RequestParam(required = false) BookingStatus status) {
        return service.findAll(status);
    }

    @GetMapping("/{id}")
    public Booking findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/user/{userId}")
    public List<Booking> findByUser(@PathVariable Long userId) {
        return service.findByUser(userId);
    }

    /** Reservations PENDING_PAYMENT dont la deadline de paiement est depassee. */
    @GetMapping("/expired")
    public List<Booking> findExpired() {
        return service.findExpired();
    }

    @PostMapping
    public ResponseEntity<Booking> create(@Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createBooking(request));
    }

    @PatchMapping("/{id}/confirm")
    public Booking confirm(@PathVariable Long id, @Valid @RequestBody ConfirmPaymentRequest request) {
        return service.confirmBooking(id, request);
    }

    @PatchMapping("/{id}/cancel")
    public Booking cancel(@PathVariable Long id) {
        return service.cancelBooking(id);
    }

    @PatchMapping("/{id}/complete")
    public Booking complete(@PathVariable Long id) {
        return service.completeBooking(id);
    }
}
