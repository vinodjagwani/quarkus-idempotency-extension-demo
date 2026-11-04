package demo.checkout.service;

import demo.checkout.model.Order;
import demo.checkout.repository.ChargeRepository;
import demo.checkout.repository.OrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates an order and charges the customer in one database transaction.
 * The charge is the side effect that must happen exactly once.
 */
@ApplicationScoped
public class CheckoutService {

    private final OrderRepository orderRepository;
    private final ChargeRepository chargeRepository;
    private final PaymentGateway paymentGateway;

    public CheckoutService(final OrderRepository orderRepository,
                           final ChargeRepository chargeRepository,
                           final PaymentGateway paymentGateway) {
        this.orderRepository = orderRepository;
        this.chargeRepository = chargeRepository;
        this.paymentGateway = paymentGateway;
    }

    @Transactional
    public Order checkout(final String item, final BigDecimal amount) {
        final Order order = newPaidOrder(item, amount);
        orderRepository.insert(order);
        // shortcut: gateway call stays inside the transaction so the retry window is visible; move it out for real payments.
        paymentGateway.charge();
        chargeRepository.insert(order);
        return order;
    }

    public Optional<Order> findOrder(final UUID id) {
        return orderRepository.findById(id);
    }

    public long countCharges() {
        return chargeRepository.count();
    }

    private Order newPaidOrder(final String item, final BigDecimal amount) {
        return new Order(UUID.randomUUID(), item, amount, "PAID");
    }
}
