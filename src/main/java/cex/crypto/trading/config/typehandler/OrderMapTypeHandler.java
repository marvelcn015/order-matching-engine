package cex.crypto.trading.config.typehandler;

import cex.crypto.trading.domain.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * MyBatis TypeHandler for ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>>
 * Serializes/deserializes the order map to/from JSON for database storage
 */
public class OrderMapTypeHandler extends BaseTypeHandler<ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>>> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        // Register JavaTimeModule to handle LocalDateTime
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
            ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> parameter,
            JdbcType jdbcType) throws SQLException {
        try {
            String json = objectMapper.writeValueAsString(parameter);
            ps.setString(i, json);
        } catch (JsonProcessingException e) {
            throw new SQLException("Error converting OrderMap to JSON", e);
        }
    }

    @Override
    public ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> getNullableResult(
            ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return parseJson(json);
    }

    @Override
    public ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> getNullableResult(
            ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return parseJson(json);
    }

    @Override
    public ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> getNullableResult(
            CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return parseJson(json);
    }

    private ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> parseJson(String json) throws SQLException {
        if (json == null || json.trim().isEmpty()) {
            return new ConcurrentSkipListMap<>();
        }

        try {
            // Deserialize to a regular map structure first
            TypeReference<ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>>> typeRef =
                new TypeReference<ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>>>() {};

            return objectMapper.readValue(json, typeRef);
        } catch (IOException e) {
            throw new SQLException("Error parsing JSON to OrderMap", e);
        }
    }

    /**
     * Create a buy order map (descending order - highest price first)
     */
    public static ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> createBuyOrderMap() {
        return new ConcurrentSkipListMap<>(Comparator.reverseOrder());
    }

    /**
     * Create a sell order map (ascending order - lowest price first)
     */
    public static ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> createSellOrderMap() {
        return new ConcurrentSkipListMap<>();
    }
}
