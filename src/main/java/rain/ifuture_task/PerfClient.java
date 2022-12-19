package rain.ifuture_task;

import io.vertx.core.json.JsonObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PerfClient {
  // parameters
  private static int threadCount = 40;
  private static double readQuota = 0.3;
  private static double writeQuota = 1 - readQuota;
  private static List<Long> readIdList;
  private static List<Long> writeIdList;
  private static Duration testDuration = Duration.ofSeconds(10);

  //connection
  private static String basePath = "http://localhost:8081/";
  private static Duration timeout = Duration.ofSeconds(5);

  // statistics
  private static AtomicInteger getRequests = new AtomicInteger(0);
  private static AtomicInteger changeRequests = new AtomicInteger(0);
  private static AtomicInteger timeouts = new AtomicInteger(0);
  private static AtomicInteger serverErrors = new AtomicInteger(0);

  static {
    readIdList = new ArrayList<>();
    writeIdList = new ArrayList<>();
    for (long i = 100; i < 200; i++) {
      readIdList.add(i);
    }

    for (long i = 150; i < 250; i++) {
      writeIdList.add(i);
    }
  }

  public static void main(String[] args) throws InterruptedException, IOException {
    PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream("./log.txt"), StandardCharsets.UTF_8));
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(threadCount + 1);
    CountDownLatch allThreadsFinishedSignal = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      Future future = executor.submit(PerfClient::perfTask);
      executor.schedule(() -> {
        future.cancel(true);
        allThreadsFinishedSignal.countDown();
      }, testDuration.getSeconds(), TimeUnit.SECONDS);
    }
    executor.shutdown();

    allThreadsFinishedSignal.await();

    writer.printf("Время теста: %d с\n", testDuration.getSeconds());
    writer.printf("Количество запросов на получение: %d\n", getRequests.get());
    writer.printf("Количество запросов на изменение: %d\n", changeRequests.get());
    int allRequests = getRequests.get() + changeRequests.get();
    writer.printf("Сумарное количество запросов: %d\n", allRequests);
    writer.printf("Средний RPS: %f\n", ((double) allRequests) / testDuration.getSeconds());
    writer.printf("Количество таймаутов: %d\n", timeouts.get());
    writer.printf("Ошибок сервера: %d\n", serverErrors.get());

    writer.close();
  }

  public static void perfTask() {
    while (true) {
      try {
        // вероятность вызова метода getBalance
        double readProbability = readQuota / (readQuota + writeQuota);
        if (ThreadLocalRandom.current().nextDouble() < readProbability) {
          getBalance();
        } else {
          changeBalance();
        }
      } catch (InterruptedException e) {
        break;
      } catch (Exception ignored) {
        System.out.println(ignored);
      }
    }
  }

  public static int getRandomIndex(int maxIndex) {
    return ThreadLocalRandom.current().nextInt(maxIndex);
  }

  public static void getBalance() throws URISyntaxException, IOException, InterruptedException {
    Long id = readIdList.get(getRandomIndex(readIdList.size() - 1));

    try {
      HttpRequest request = HttpRequest.newBuilder()
        .uri(new URI(basePath + "api/balance" + id))
        .timeout(timeout)
        .GET()
        .build();
      HttpResponse<String> response = HttpClient.newBuilder()
        .build()
        .send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200 || response.statusCode() == 404) {
        getRequests.incrementAndGet();
      } else if (response.statusCode() >= 500) {
        serverErrors.incrementAndGet();
      }
    } catch (HttpTimeoutException e) {
      timeouts.incrementAndGet();
    }
  }

  public static void changeBalance() throws URISyntaxException, IOException, InterruptedException {
    Long id = writeIdList.get(getRandomIndex(readIdList.size() - 1));

    try {
      JsonObject body = new JsonObject()
        .put("balanceId", id)
        .put("amount", 1L);
      HttpRequest request = HttpRequest.newBuilder()
        .uri(new URI(basePath + "api/balance/add"))
        .headers("Content-Type", "application/json")
        .timeout(timeout)
        .POST(HttpRequest.BodyPublishers.ofString(body.encode()))
        .build();
      HttpResponse<String> response = HttpClient.newBuilder()
        .build()
        .send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        changeRequests.incrementAndGet();
      } else if (response.statusCode() >= 500) {
        serverErrors.incrementAndGet();
      }
    } catch (HttpTimeoutException e) {
      timeouts.incrementAndGet();
    }
  }
}
