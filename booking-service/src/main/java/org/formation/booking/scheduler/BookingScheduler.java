package org.formation.booking.scheduler;

import org.formation.booking.service.BookingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingScheduler {

    private final BookingService bookingService;

    public BookingScheduler(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** Expiration des paiements en attente (toutes les 5 minutes par defaut). */
    @Scheduled(fixedRateString = "${booking.scheduler.expiration-rate-ms:300000}",
            initialDelayString = "${booking.scheduler.initial-delay-ms:30000}")
    public void expirePendingPayments() {
        bookingService.expirePendingBookings();
    }

    /** Rappel des cours ayant lieu dans les prochaines 24h (toutes les 15 minutes par defaut). */
    @Scheduled(fixedRateString = "${booking.scheduler.reminder-rate-ms:900000}",
            initialDelayString = "${booking.scheduler.initial-delay-ms:30000}")
    public void sendClassReminders() {
        bookingService.sendClassReminders();
    }
}
