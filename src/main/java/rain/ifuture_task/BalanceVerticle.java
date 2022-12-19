package rain.ifuture_task;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class BalanceVerticle extends AbstractVerticle {
  private BalanceService balanceService;

  public BalanceVerticle(BalanceService balanceService) {
    this.balanceService = balanceService;
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    // Create a Router
    Router router = Router.router(vertx);

    router
      .route()
      .method(HttpMethod.GET)
      .path("/api/balance/:balanceId")
      .handler(this::getBalance);

    router
      .route()
      .method(HttpMethod.POST)
      .path("/api/balance/add")
      .handler(BodyHandler.create())
      .handler(this::changeBalance);

    // Create the HTTP server
    vertx.createHttpServer()
      // Handle every request using the router
      .requestHandler(router)
      // Start listening
      .listen(8081)
      // Print the port
      .onSuccess(server ->
        System.out.println(
          "HTTP server started on port " + server.actualPort()
        )
      );
  }

  private void getBalance(RoutingContext ctx) {
    Long id = Long.parseLong(ctx.pathParam("balanceId"));
    balanceService.getBalance(id)
      .onSuccess(amount -> {
        if (amount.isPresent()) {
          JsonObject response = new JsonObject()
            .put("balanceId", id)
            .put("amount", amount.get());
          ctx.response().putHeader("content-type", "application/json");
          ctx.response().setStatusCode(200);
          ctx.response().end(response.encode());
        } else {
          ctx.response().putHeader("content-type", "text/html");
          ctx.response().setStatusCode(404);
          ctx.response().end("Balance with specified id not found");
        }
      })
      .onFailure(err -> {
        System.out.println(err.getMessage());
        ctx.response().setStatusCode(500);
        ctx.response().end();
      });
  }

  private void changeBalance(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    Long id = body.getLong("balanceId");
    Long amount = body.getLong("amount");

    balanceService.changeBalance(id, amount)
      .onSuccess(result -> {
        ctx.response().setStatusCode(200);
        ctx.response().end();
      })
      .onFailure(err -> {
        System.out.println(err.getMessage());
        ctx.response().setStatusCode(500);
        ctx.response().end();
      });
  }
}
