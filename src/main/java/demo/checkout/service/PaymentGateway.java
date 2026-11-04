package demo.checkout.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Stands in for a slow remote payment provider; the latency is what makes clients time out and retry. */
@ApplicationScoped
public class PaymentGateway {

    private final Duration latency;

    public PaymentGateway(@ConfigProperty(name = "demo.payment-delay") final Duration latency) {
        this.latency = latency;
    }

    public void charge() {
        try {
            Thread.sleep(latency);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling the payment gateway", e);
        }
    }
}
