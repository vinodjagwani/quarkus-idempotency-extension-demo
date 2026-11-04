package demo.checkout.repository;

import demo.checkout.model.Order;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class OrderRepository {

    private final AgroalDataSource dataSource;

    public OrderRepository(final AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(final Order order) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO orders (id, item, amount, status) VALUES (?, ?, ?, ?)")) {
            statement.setObject(1, order.id());
            statement.setString(2, order.item());
            statement.setBigDecimal(3, order.amount());
            statement.setString(4, order.status());
            statement.executeUpdate();
        } catch (final SQLException e) {
            throw new IllegalStateException("Could not insert order " + order.id(), e);
        }
    }

    public Optional<Order> findById(final UUID id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, item, amount, status FROM orders WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(toOrder(rows)) : Optional.empty();
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("Could not load order " + id, e);
        }
    }

    private Order toOrder(final ResultSet row) throws SQLException {
        return new Order(row.getObject("id", UUID.class), row.getString("item"),
                row.getBigDecimal("amount"), row.getString("status"));
    }
}
