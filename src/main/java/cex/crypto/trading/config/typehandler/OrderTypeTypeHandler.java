package cex.crypto.trading.config.typehandler;

import cex.crypto.trading.enums.OrderType;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis TypeHandler for OrderType enum
 * Maps between OrderType enum and VARCHAR database column
 */
public class OrderTypeTypeHandler extends BaseTypeHandler<OrderType> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, OrderType parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }

    @Override
    public OrderType getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : OrderType.valueOf(value);
    }

    @Override
    public OrderType getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : OrderType.valueOf(value);
    }

    @Override
    public OrderType getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : OrderType.valueOf(value);
    }
}
