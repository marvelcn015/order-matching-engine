package cex.crypto.trading.config.typehandler;

import cex.crypto.trading.enums.OrderSide;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis TypeHandler for OrderSide enum
 * Maps between OrderSide enum and VARCHAR database column
 */
public class OrderSideTypeHandler extends BaseTypeHandler<OrderSide> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, OrderSide parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }

    @Override
    public OrderSide getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : OrderSide.valueOf(value);
    }

    @Override
    public OrderSide getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : OrderSide.valueOf(value);
    }

    @Override
    public OrderSide getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : OrderSide.valueOf(value);
    }
}
