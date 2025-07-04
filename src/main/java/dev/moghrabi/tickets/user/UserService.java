package dev.moghrabi.tickets.user;

import java.util.List;
import java.util.Set;

import dev.moghrabi.tickets.error.ApiException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Benutzerverwaltung und gleichzeitig Quelle für Spring Security: Da es genau
 * eine UserDetailsService-Bean und einen PasswordEncoder gibt, baut Spring
 * Boot die Anmeldung per HTTP Basic damit automatisch zusammen.
 */
@Service
public class UserService implements UserDetailsService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .build();
    }

    @Transactional(readOnly = true)
    public AppUser byUsername(String username) {
        return users.findByUsername(username).orElseThrow(() -> ApiException.notFound("Benutzer " + username));
    }

    @Transactional(readOnly = true)
    public List<AppUser> staff() {
        return users.findByRoleInOrderByDisplayName(Set.of(Role.AGENT, Role.ADMIN));
    }

    @Transactional
    public AppUser create(String username, String displayName, String rawPassword, Role role) {
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("Benutzername ist vergeben: " + username);
        }
        return users.save(new AppUser(username, displayName, passwordEncoder.encode(rawPassword), role));
    }
}
