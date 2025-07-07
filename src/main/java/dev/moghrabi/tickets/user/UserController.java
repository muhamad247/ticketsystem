package dev.moghrabi.tickets.user;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Benutzer")
public class UserController {

    public record UserResponse(String username, String displayName, Role role) {
        static UserResponse of(AppUser u) {
            return new UserResponse(u.getUsername(), u.getDisplayName(), u.getRole());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9._-]{3,50}") String username,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Role role) {
    }

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    @Operation(summary = "Angemeldeter Benutzer")
    public UserResponse me(Authentication auth) {
        return UserResponse.of(users.byUsername(auth.getName()));
    }

    @GetMapping("/staff")
    @Operation(summary = "Support-Mitarbeiter (für die Zuweisung)")
    public List<UserResponse> staff() {
        return users.staff().stream().map(UserResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Benutzer anlegen (nur ADMIN)")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.of(users.create(request.username(), request.displayName(), request.password(), request.role()));
    }
}
