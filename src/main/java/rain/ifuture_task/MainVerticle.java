package rain.ifuture_task;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;

public class MainVerticle extends AbstractVerticle {
  private SqlClient sqlClient;
  private PgPool pgPool;

  @Override
  public void start(Promise<Void> startPromise) {
    setUpPostgres();

    DeploymentOptions options = new DeploymentOptions().setInstances(
      Runtime.getRuntime().availableProcessors()
    );
    BalanceService balanceService = new BalanceService(sqlClient, pgPool, vertx.sharedData());
    vertx.deployVerticle(() -> new BalanceVerticle(balanceService), options);
  }

  private void setUpPostgres() {
    PgConnectOptions connectOptions = new PgConnectOptions()
      .setPort(Integer.parseInt(System.getenv("POSTGRES_PORT")))
      .setHost(System.getenv("POSTGRES_HOST"))
      .setDatabase(System.getenv("POSTGRES_DB"))
      .setUser(System.getenv("POSTGRES_USER"))
      .setPassword(System.getenv("POSTGRES_PASSWORD"))
      .setCachePreparedStatements(true)
      .setPreparedStatementCacheMaxSize(1000)
      .setReconnectAttempts(2)
      .setReconnectInterval(1000);

    PoolOptions poolOptions = new PoolOptions()
      .setMaxSize(50);

    sqlClient = PgPool.client(vertx, connectOptions, poolOptions);
    pgPool = PgPool.pool(vertx, connectOptions, poolOptions);
  }
}
