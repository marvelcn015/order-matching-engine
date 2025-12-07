package cex.crypto.trading.mapper;

import cex.crypto.trading.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper for User operations
 */
@Mapper
public interface UserMapper {
    /**
     * Insert a new user
     */
    void insert(User user);

    /**
     * Find user by ID
     */
    User findById(@Param("userId") Long userId);

    /**
     * Find user by username
     */
    User findByUsername(@Param("username") String username);

    /**
     * Find user by email
     */
    User findByEmail(@Param("email") String email);

    /**
     * Update user
     */
    void update(User user);

    /**
     * Delete user by ID
     */
    void deleteById(@Param("userId") Long userId);

    /**
     * Find all user IDs for Bloom Filter initialization
     * @param offset the offset for pagination
     * @param limit the number of records to fetch
     * @return list of user IDs
     */
    List<Long> findAllUserIds(@Param("offset") long offset, @Param("limit") int limit);
}
