package org.folio.db;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.SQLConnection;

import org.apache.commons.lang3.mutable.MutableObject;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.util.FutureUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import static org.folio.util.FutureUtils.mapCompletableFuture;

public final class DbUtils {

  @SuppressWarnings("squid:S2386")
  public static final String[] ALL_FIELDS = {"*"};

  private static final Logger LOG = LoggerFactory.getLogger(DbUtils.class);

  private DbUtils() {}

  public static <T> CompletableFuture<T> executeInTransaction(String tenantId, Vertx vertx,
                                                              BiFunction<PostgresClient, AsyncResult<SQLConnection>, CompletableFuture<T>> action) {
    PostgresClient postgresClient = PostgresClient.getInstance(vertx, tenantId);
    MutableObject<AsyncResult<SQLConnection>> mutableConnection = new MutableObject<>();
    MutableObject<T> mutableResult = new MutableObject<>();
    CompletableFuture<Boolean> rollbackFuture = new CompletableFuture<>();

    return CompletableFuture.completedFuture(null)
      .thenCompose(o -> {
        CompletableFuture<Void> startTxFuture = new CompletableFuture<>();
        postgresClient.startTx(connection -> {
          mutableConnection.setValue(connection);
          startTxFuture.complete(null);
        });
        return startTxFuture;
      })
      .thenCompose(o -> action.apply(postgresClient, mutableConnection.getValue()))
      .thenCompose(result -> {
        mutableResult.setValue(result);
        return endTransaction(postgresClient, mutableConnection);
      })
      .whenComplete((result, ex) -> {
        if (ex != null) {
          LOG.info("Transaction was not successful. Roll back changes.");
          postgresClient.rollbackTx(mutableConnection.getValue(), rollback -> rollbackFuture.completeExceptionally(ex));
        } else {
          rollbackFuture.complete(null);
        }
      })
      .thenCombine(rollbackFuture, (o, aBoolean) -> mutableResult.getValue());
  }

  public static <T> Future<T> executeInTransactionWithVertxFuture(String tenantId, Vertx vertx,
                                                              BiFunction<PostgresClient,
                                                                AsyncResult<SQLConnection>, Future<T>> action) {
    BiFunction<PostgresClient, AsyncResult<SQLConnection>, CompletableFuture<T>> newAction = action.andThen(FutureUtils::mapVertxFuture);
    return mapCompletableFuture(executeInTransaction(tenantId, vertx, newAction));
  }

  public static CQLWrapper getCQLWrapper(String tableName, String query, int limit, int offset) throws FieldException {
    return getCQLWrapper(tableName, query)
            .setLimit(new Limit(limit))
            .setOffset(new Offset(offset));
  }

  public static CQLWrapper getCQLWrapper(String tableName, String query) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(tableName + ".jsonb");
    return new CQLWrapper(cql2pgJson, query);
  }

  public static JsonArray createParams(Iterable<?> queryParameters) {
    JsonArray parameters = new JsonArray();

    for (Object p : queryParameters) {
      if (p != null) {
        parameters.add(p);
      } else {
        parameters.addNull();
      }
    }

    return parameters;
  }

  private static CompletionStage<Void> endTransaction(PostgresClient postgresClient, MutableObject<AsyncResult<SQLConnection>> mutableConnection) {
    Future<Void> future = Future.future();
    postgresClient.endTx(mutableConnection.getValue(), future);
    return FutureUtils.mapVertxFuture(future);
  }

}
