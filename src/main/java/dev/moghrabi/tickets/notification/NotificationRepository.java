package dev.moghrabi.tickets.notification;

import java.util.List;
import java.util.Optional;

import dev.moghrabi.tickets.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByRecipientOrderByCreatedAtDescIdDesc(AppUser recipient);

    List<Notification> findTop50ByRecipientAndReadAtIsNullOrderByCreatedAtDescIdDesc(AppUser recipient);

    Optional<Notification> findByIdAndRecipient(Long id, AppUser recipient);
}
