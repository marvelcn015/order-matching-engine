package cex.crypto.trading.service;

import cex.crypto.trading.domain.User;
import cex.crypto.trading.dto.CreateUserRequest;
import cex.crypto.trading.exception.BusinessException;
import cex.crypto.trading.exception.UserNotFoundException;
import cex.crypto.trading.mapper.UserMapper;
import cex.crypto.trading.service.cache.BloomFilterService;
import cex.crypto.trading.service.cache.DistributedLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User management service
 */
@Service
@Slf4j
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BloomFilterService bloomFilterService;

    @Autowired
    private DistributedLockService lockService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Create a new user (with distributed lock protection)
     */
    @Transactional
    public User createUser(CreateUserRequest request) {
        String lockKey = "lock:user:create:" + request.getUsername();

        return lockService.executeWithLock(lockKey, () -> {
            // Check if username already exists
            if (userMapper.findByUsername(request.getUsername()) != null) {
                throw new BusinessException("Username already exists: " + request.getUsername());
            }

            // Check if email already exists
            if (userMapper.findByEmail(request.getEmail()) != null) {
                throw new BusinessException("Email already exists: " + request.getEmail());
            }

            // Hash password
            String passwordHash = passwordEncoder.encode(request.getPassword());

            // Create user entity
            User user = User.builder()
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .passwordHash(passwordHash)
                    .build();

            // Insert into database
            userMapper.insert(user);

            // Add to Bloom Filter
            bloomFilterService.addUser(user.getUserId());

            log.info("User created: userId={}, username={}", user.getUserId(), user.getUsername());
            return user;
        });
    }

    /**
     * Get user by ID (with Bloom Filter protection)
     */
    public User getUserById(Long userId) {
        // Bloom Filter check (prevents cache penetration)
        if (!bloomFilterService.mayExistUser(userId)) {
            log.debug("User not found in Bloom Filter: {}", userId);
            throw new UserNotFoundException("User not found: " + userId);
        }

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new UserNotFoundException("User not found: " + userId);
        }
        return user;
    }

    /**
     * Get user by username
     */
    public User getUserByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    /**
     * Update user
     */
    @Transactional
    public User updateUser(User user) {
        userMapper.update(user);
        log.info("User updated: userId={}", user.getUserId());
        return getUserById(user.getUserId());
    }

    /**
     * Delete user
     */
    @Transactional
    public void deleteUser(Long userId) {
        userMapper.deleteById(userId);
        log.info("User deleted: userId={}", userId);
    }
}
