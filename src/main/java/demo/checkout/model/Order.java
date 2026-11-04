package demo.checkout.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

public record Order(UUID id, String item, BigDecimal amount, String status) implements Serializable {
}
