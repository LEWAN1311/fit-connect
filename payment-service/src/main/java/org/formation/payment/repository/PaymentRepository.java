package org.formation.payment.repository;

import org.formation.payment.entity.Payment;
import org.formation.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Une reservation peut avoir plusieurs tentatives : on renvoie la plus recente. */
    Optional<Payment> findFirstByBookingIdOrderByPaymentDateDescIdDesc(Long bookingId);

    boolean existsByBookingIdAndStatus(Long bookingId, PaymentStatus status);

    List<Payment> findByUserIdOrderByPaymentDateDesc(Long userId);
}
