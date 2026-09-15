package ge.andaneri.crm.web;

import ge.andaneri.crm.domain.Role;
import ge.andaneri.crm.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserDto(Long id, String username, String fullName, String phone, Role role, boolean active,
            java.time.Instant lastLoginAt, String lastLoginIp, java.time.Instant createdAt) {
        public static UserDto of(User user) {
            return new UserDto(user.getId(), user.getUsername(), user.getFullName(), user.getPhone(), user.getRole(), user.isActive(),
                    user.getLastLoginAt(), user.getLastLoginIp(), user.getCreatedAt());
        }
    }

    /** Who a business or task is assigned to, as shown next to it. */
    public record UserRef(Long id, String fullName) {
        public static UserRef of(User user) {
            return user == null ? null : new UserRef(user.getId(), user.getFullName());
        }
    }

    public record UserCreate(
            @NotBlank @Size(max = 60) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
            @NotBlank @Size(max = 120) String fullName,
            @Size(max = 40) String phone,
            @NotNull Role role,
            @NotBlank @Size(min = 8, max = 100) String password) {
    }

    /** A blank password leaves the current one alone. */
    public record UserUpdate(
            @NotBlank @Size(max = 120) String fullName,
            @Size(max = 40) String phone,
            @NotNull Role role,
            /** Null keeps the account as it is. */
            Boolean active,
            @Size(max = 100) String password) {
    }
}
