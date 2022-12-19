package rain.ifuture_task;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.vertx.core.Future;
import io.vertx.core.shareddata.SharedData;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;

import java.util.Optional;

public class BalanceService {
  private static int MAX_CACHE_SIZE = 10000;
  private SqlClient sqlClient;
  private PgPool pgPool;
  private SharedData balanceLock;
  private Cache<Long, Long> balanceCache;

  public BalanceService(SqlClient sqlClient, PgPool pgPool, SharedData balanceLock) {
    this.sqlClient = sqlClient;
    this.pgPool = pgPool;
    this.balanceLock = balanceLock;
    configureCache();
  }

  private void configureCache() {
    balanceCache = CacheBuilder.newBuilder().maximumSize(MAX_CACHE_SIZE).build();
  }

  Future<Optional<Long>> getBalance(Long id) {
    Long balance = balanceCache.getIfPresent(id);
    if (balance != null) {
      return Future.succeededFuture(Optional.of(balance));
    }

    return balanceLock.getLock(id.toString())
      .compose(lock -> {
        Future<Optional<Long>> futureResult = sqlClient
          .preparedQuery("SELECT * FROM balance WHERE id=$1")
          .execute(Tuple.of(id))
          .compose(rowSet -> {
            if (rowSet.size() == 0) {
              return Future.succeededFuture(Optional.empty());
            }
            Long result = rowSet.iterator().next().getLong("amount");
            balanceCache.put(id, result);
            lock.release();
            return Future.succeededFuture(Optional.of(result));
          });
        return futureResult;
      });
  }

  Future<Long> changeBalance(Long id, Long amount) {
    return balanceLock.getLock(id.toString())
      .compose(lock -> {
        Future<Long> futureResult = sqlClient
          .preparedQuery("" +
            "INSERT INTO balance (id, amount) " +
            "VALUES ($1, $2) " +
            "ON CONFLICT (id) DO UPDATE " +
            "SET amount = balance.amount + EXCLUDED.amount " +
            "RETURNING amount"
          )
          .execute(Tuple.of(id, amount))
          .compose(rowSet -> {
            Long result = rowSet.iterator().next().getLong("amount");
            balanceCache.put(id, result);
            lock.release();
            return Future.succeededFuture(result);
          });
        return futureResult;
      });
  }
}
