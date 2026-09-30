package org.formation.booking.client;

import org.formation.booking.dto.PaymentRequest;
import org.formation.booking.dto.PaymentResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/** Le paiement est critique : pas de valeur par defaut, l'erreur est remontee au client. */
@Component
public class PaymentServiceClientFallbackFactory implements FallbackFactory<PaymentServiceClient> {

    private static final String SERVICE = "payment-service";

    @Override
    public PaymentServiceClient create(Throwable cause) {
        return new PaymentServiceClient() {
            @Override
            public PaymentResponse processPayment(PaymentRequest request) {
                throw RemoteErrorTranslator.translate(cause, SERVICE);
            }

            @Override
            public PaymentResponse getPaymentByBooking(Long bookingId) {
                throw RemoteErrorTranslator.translate(cause, SERVICE);
            }

            @Override
            public PaymentResponse refund(Long paymentId) {
                throw RemoteErrorTranslator.translate(cause, SERVICE);
            }
        };
    }
}
