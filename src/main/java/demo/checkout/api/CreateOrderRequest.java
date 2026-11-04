package demo.checkout.api;

import java.math.BigDecimal;

public record CreateOrderRequest(String item, BigDecimal amount) {
}
