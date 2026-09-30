package org.formation.booking.repository;

import org.formation.booking.entity.Booking;
import org.formation.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserIdOrderByBookingDateDesc(Long userId);

    List<Booking> findByStatusOrderByBookingDateDesc(BookingStatus status);

    List<Booking> findByStatusAndPaymentDeadlineBefore(BookingStatus status, LocalDateTime now);

    List<Booking> findByStatusAndReminderSentFalseAndClassDateBetween(BookingStatus status,
                                                                      LocalDateTime from, LocalDateTime to);
}
