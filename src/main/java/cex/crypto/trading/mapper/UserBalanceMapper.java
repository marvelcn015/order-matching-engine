package cex.crypto.trading.mapper;

import cex.crypto.trading.domain.UserBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * MyBatis mapper for UserBalance operations
 */
@Mapper
public interface UserBalanceMapper {
    /**
     * Insert a new balance record
     */
    void insert(UserBalance balance);

    /**
     * Find balance by user ID and currency
     */
    UserBalance findByUserIdAndCurrency(@Param("userId") Long userId, @Param("currency") String currency);

    /**
     * Find all balances for a user
     */
    List<UserBalance> findByUserId(@Param("userId") Long userId);

    /**
     * Update available balance
     */
    void updateAvailableBalance(@Param("userId") Long userId,
                                 @Param("currency") String currency,
                                 @Param("amount") BigDecimal amount);

    /**
     * Update frozen balance
     */
    void updateFrozenBalance(@Param("userId") Long userId,
                             @Param("currency") String currency,
                             @Param("amount") BigDecimal amount);

    /**
     * Delete balance record
     */
    void deleteById(@Param("id") Long id);
}
