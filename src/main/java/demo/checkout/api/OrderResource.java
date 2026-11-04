package demo.checkout.api;

import demo.checkout.model.Order;
import demo.checkout.service.CheckoutService;
import io.quarkiverse.idempotency.runtime.Idempotent;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Path("/orders")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {

    private final CheckoutService checkoutService;

    public OrderResource(final CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    /** Guarded: the extension requires an Idempotency-Key and replays the stored response on a retry. */
    @POST
    public Response create(final CreateOrderRequest request) {
        requireValid(request);
        return created(checkoutService.checkout(request.item(), request.amount()));
    }

    /** Same business logic with the guard switched off, to show what a retry does without it. */
    @POST
    @Path("/unsafe")
    @Idempotent(enabled = false)
    public Response createUnsafe(final CreateOrderRequest request) {
        requireValid(request);
        return created(checkoutService.checkout(request.item(), request.amount()));
    }

    @GET
    @Path("/{id}")
    public Order get(@PathParam("id") final UUID id) {
        return checkoutService.findOrder(id).orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("/charges/count")
    public Map<String, Long> chargeCount() {
        return Map.of("charges", checkoutService.countCharges());
    }

    private void requireValid(final CreateOrderRequest request) {
        if (request == null || request.item() == null || request.item().isBlank()
                || request.amount() == null || request.amount().signum() <= 0) {
            throw new BadRequestException("item and a positive amount are required");
        }
    }

    private Response created(final Order order) {
        return Response.created(URI.create("/orders/" + order.id())).entity(order).build();
    }
}
