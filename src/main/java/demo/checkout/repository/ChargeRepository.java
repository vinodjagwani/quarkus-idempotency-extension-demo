package demo.checkout.repository;

import demo.checkout.model.Order;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** One row per money movement: a duplicate row here is the double charge the demo is about. */
@ApplicationScoped
public class ChargeRepository {

    private final AgroalDataSource dataSource;

    public ChargeRepository(final AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(final Order order) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO charges (order_id, amount) VALUES (?, ?)")) {
            statement.setObject(1, order.id());
            statement.setBigDecimal(2, order.amount());
            statement.executeUpdate();
        } catch (final SQLException e) {
            throw new IllegalStateException("Could not charge order " + order.id(), e);
        }
    }

    public long count() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM charges");
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getLong(1);
        } catch (final SQLException e) {
            throw new IllegalStateException("Could not count charges", e);
        }
    }
}
