package cex.crypto.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * User entity representing a user account
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    /**
     * Unique user identifier
     */
    private Long userId;

    /**
     * Username (unique)
     */
    private String username;

    /**
     * Email address (unique)
     */
    private String email;

    /**
     * Hashed password (BCrypt)
     */
    private String passwordHash;

    /**
     * Timestamp when user was created
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp when user was last updated
     */
    private LocalDateTime updatedAt;
}
