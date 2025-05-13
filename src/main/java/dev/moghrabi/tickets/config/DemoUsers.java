package dev.moghrabi.tickets.config;

import dev.moghrabi.tickets.user.Role;
import dev.moghrabi.tickets.user.UserRepository;
import dev.moghrabi.tickets.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Legt bei leerer Datenbank Demo-Benutzer an. Abschalten mit tickets.demo-users=false. */
@Component
@ConditionalOnProperty(name = "tickets.demo-users", havingValue = "true")
public class DemoUsers implements ApplicationRunner {

    static final String PASSWORD = "demo123";
    private static final Logger log = LoggerFactory.getLogger(DemoUsers.class);

    private final UserRepository repository;
    private final UserService users;

    public DemoUsers(UserRepository repository, UserService users) {
        this.repository = repository;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        users.create("anna", "Anna Berger", PASSWORD, Role.REPORTER);
        users.create("dora", "Dora Steiner", PASSWORD, Role.REPORTER);
        users.create("ben", "Ben Huber", PASSWORD, Role.AGENT);
        users.create("clara", "Clara Wagner", PASSWORD, Role.AGENT);
        users.create("admin", "Admin", PASSWORD, Role.ADMIN);
        log.warn("Demo-Benutzer angelegt (Passwort '{}'). Nicht für den echten Betrieb!", PASSWORD);
    }
}
