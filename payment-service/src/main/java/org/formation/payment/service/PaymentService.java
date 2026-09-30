package org.formation.payment.service;

import org.formation.payment.dto.PaymentRequest;
import org.formation.payment.entity.Payment;
import org.formation.payment.entity.PaymentStatus;
import org.formation.payment.exception.ConflictException;
import org.formation.payment.exception.ResourceNotFoundException;
import org.formation.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentRepository repository;
    private final BigDecimal maxAcceptedAmount;

    public PaymentService(PaymentRepository repository,
                          @Value("${payment.simulation.max-accepted-amount:100}") BigDecimal maxAcceptedAmount) {
        this.repository = repository;
        this.maxAcceptedAmount = maxAcceptedAmount;
    }

    /**
     * Simulation de passerelle de paiement :
     * montant &lt; 100 EUR accepte (SUCCESS), montant &gt;= 100 EUR refuse (FAILED).
     * Chaque tentative est historisee, y compris les echecs.
     */
    @Transactional
    public Payment process(PaymentRequest request) {
        if (request.paymentMethod().isCard() && request.cardLastFour() == null) {
            throw new IllegalArgumentException("cardLastFour est obligatoire pour un paiement par carte");
        }
        if (repository.existsByBookingIdAndStatus(request.bookingId(), PaymentStatus.SUCCESS)) {
            throw new ConflictException("La reservation " + request.bookingId() + " est deja payee");
        }

        Payment payment = new Payment();
        payment.setPaymentReference(generateReference());
        payment.setBookingId(request.bookingId());
        payment.setBookingReference(request.bookingReference());
        payment.setUserId(request.userId());
        payment.setAmount(request.amount());
        payment.setPaymentMethod(request.paymentMethod());
        payment.setCardLastFour(request.paymentMethod().isCard() ? request.cardLastFour() : null);
        payment.setTransactionId(request.transactionId() != null
                ? request.transactionId()
                : "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        payment.setPaymentDate(LocalDateTime.now());

        boolean accepted = request.amount().compareTo(maxAcceptedAmount) < 0;
        payment.setStatus(accepted ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);

        Payment saved = repository.save(payment);
        log.info("Paiement {} pour la reservation {} : {} ({} EUR)", saved.getPaymentReference(),
                saved.getBookingId(), saved.getStatus(), saved.getAmount());
        return saved;
    }

    @Transactional
    public Payment refund(Long id) {
        Payment payment = findById(id);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ConflictException("Seul un paiement reussi peut etre rembourse (statut actuel : "
                    + payment.getStatus() + ")");
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        log.info("Paiement {} rembourse", payment.getPaymentReference());
        return repository.save(payment);
    }

    @Transactional(readOnly = true)
    public Payment findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement introuvable : id=" + id));
    }

    @Transactional(readOnly = true)
    public Payment findByBooking(Long bookingId) {
        return repository.findFirstByBookingIdOrderByPaymentDateDescIdDesc(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun paiement pour la reservation : bookingId=" + bookingId));
    }

    @Transactional(readOnly = true)
    public List<Payment> findByUser(Long userId) {
        return repository.findByUserIdOrderByPaymentDateDesc(userId);
    }

    private static String generateReference() {
        StringBuilder sb = new StringBuilder("PAY-");
        for (int i = 0; i < 5; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
