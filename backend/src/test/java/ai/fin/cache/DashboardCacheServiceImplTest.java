package ai.fin.cache;

import ai.fin.config.properties.DashboardCacheProperties;
import ai.fin.dto.dashboard.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardCacheServiceImplTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  private ObjectMapper objectMapper;
  private MeterRegistry meterRegistry;
  private DashboardCacheProperties props;
  private DashboardCacheServiceImpl cacheService;

  private final String userId = "user-123";
  private final YearMonth month = YearMonth.of(2026, 3);
  private final String expectedKey = "dashboard:v1:user:user-123:month:2026-03";

  @BeforeEach
  void setUp() {
    objectMapper = JsonMapper.builder().findAndAddModules().build();
    meterRegistry = new SimpleMeterRegistry();
    props = new DashboardCacheProperties(
        true,
        Duration.ofMinutes(15),
        Duration.ofMinutes(2),
        Duration.ofSeconds(60),
        "dashboard:v1"
    );
    cacheService = new DashboardCacheServiceImpl(props, redisTemplate, objectMapper, provider(meterRegistry));
  }

  private ObjectProvider<MeterRegistry> provider(MeterRegistry registry) {
    @SuppressWarnings("unchecked")
    ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
    lenient().when(provider.getIfAvailable()).thenReturn(registry);
    return provider;
  }

  private DashboardResponse createSampleResponse() {
    DashboardSummary summary = new DashboardSummary(
        new BigDecimal("5000.00"),
        new BigDecimal("2000.00"),
        new BigDecimal("3000.00")
    );
    List<CategoryBreakdownResponse> expenses = List.of(
        new CategoryBreakdownResponse("Food", new BigDecimal("1200.00"), 60.0)
    );
    List<CategoryBreakdownResponse> income = List.of(
        new CategoryBreakdownResponse("Salary", new BigDecimal("5000.00"), 100.0)
    );
    List<DailyTrendItem> trend = List.of(
        new DailyTrendItem(LocalDate.of(2026, 3, 1), new BigDecimal("5000.00"), new BigDecimal("1200.00"))
    );
    List<TopExpenseItem> topExpenses = List.of(
        new TopExpenseItem("tx-1", new BigDecimal("1200.00"), "Food", "Groceries", LocalDate.of(2026, 3, 1))
    );

    return new DashboardResponse("2026-03", summary, expenses, income, trend, topExpenses);
  }

  private DashboardResponse createEmptyResponse() {
    DashboardSummary summary = new DashboardSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    return new DashboardResponse(
        "2026-03",
        summary,
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList()
    );
  }

  @Test
  @DisplayName("Cache hit returns deserialized DashboardResponse without invoking loader")
  void shouldReturnCachedValueOnHit() throws Exception {
    DashboardResponse sample = createSampleResponse();
    String json = objectMapper.writeValueAsString(sample);

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(expectedKey)).thenReturn(json);

    @SuppressWarnings("unchecked")
    Supplier<DashboardResponse> loader = mock(Supplier.class);

    DashboardResponse result = cacheService.getOrCompute(userId, month, loader);

    assertThat(result).isNotNull();
    assertThat(result.month()).isEqualTo("2026-03");
    assertThat(result.summary().totalIncome()).isEqualByComparingTo("5000.00");
    assertThat(result.summary().totalExpense()).isEqualByComparingTo("2000.00");
    assertThat(result.summary().netBalance()).isEqualByComparingTo("3000.00");
    assertThat(result.expenseByCategory()).hasSize(1);
    assertThat(result.topExpenses()).hasSize(1);

    verify(valueOperations).get(expectedKey);
    verifyNoInteractions(loader);
    assertThat(meterRegistry.counter("dashboard.cache.requests", "result", "hit").count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Cache miss invokes loader and caches result with TTL and jitter")
  void shouldInvokeLoaderAndCacheOnMiss() {
    DashboardResponse sample = createSampleResponse();

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(expectedKey)).thenReturn(null);

    DashboardResponse result = cacheService.getOrCompute(userId, month, () -> sample);

    assertThat(result).isEqualTo(sample);

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

    verify(valueOperations).set(keyCaptor.capture(), jsonCaptor.capture(), ttlCaptor.capture());
    assertThat(keyCaptor.getValue()).isEqualTo(expectedKey);
    assertThat(jsonCaptor.getValue()).contains("5000.00");
    // Standard TTL is 15m (900s) + jitter (0..60s)
    assertThat(ttlCaptor.getValue().toSeconds()).isBetween(900L, 960L);
    assertThat(meterRegistry.counter("dashboard.cache.requests", "result", "miss").count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Empty dashboard result is cached with empty TTL to protect against penetration")
  void shouldCacheEmptyDashboardWithEmptyTtl() {
    DashboardResponse emptyResponse = createEmptyResponse();

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(expectedKey)).thenReturn(null);

    DashboardResponse result = cacheService.getOrCompute(userId, month, () -> emptyResponse);

    assertThat(result).isEqualTo(emptyResponse);

    ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(valueOperations).set(eq(expectedKey), anyString(), ttlCaptor.capture());

    // Empty TTL is 2m (120s) + jitter (0..30s)
    assertThat(ttlCaptor.getValue().toSeconds()).isBetween(120L, 150L);
  }

  @Test
  @DisplayName("Redis read failure gracefully falls back to database loader")
  void shouldFallbackToDatabaseWhenRedisGetFails() {
    DashboardResponse sample = createSampleResponse();

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(expectedKey)).thenThrow(new RedisConnectionFailureException("Redis connection refused"));

    DashboardResponse result = cacheService.getOrCompute(userId, month, () -> sample);

    assertThat(result).isEqualTo(sample);
    assertThat(meterRegistry.counter("dashboard.cache.errors", "operation", "get").count()).isGreaterThanOrEqualTo(1.0);
  }

  @Test
  @DisplayName("Redis write failure does not break the request and returns computed value")
  void shouldReturnComputedValueWhenRedisSetFails() {
    DashboardResponse sample = createSampleResponse();

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(expectedKey)).thenReturn(null);
    doThrow(new RedisConnectionFailureException("Redis write timeout"))
        .when(valueOperations).set(eq(expectedKey), anyString(), any(Duration.class));

    DashboardResponse result = cacheService.getOrCompute(userId, month, () -> sample);

    assertThat(result).isEqualTo(sample);
    assertThat(meterRegistry.counter("dashboard.cache.errors", "operation", "put").count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Cache disabled bypasses Redis completely")
  void shouldBypassCacheWhenDisabled() {
    DashboardCacheProperties disabledProps = new DashboardCacheProperties(
        false,
        Duration.ofMinutes(15),
        Duration.ofMinutes(2),
        Duration.ofSeconds(60),
        "dashboard:v1"
    );
    DashboardCacheServiceImpl disabledService = new DashboardCacheServiceImpl(
        disabledProps, redisTemplate, objectMapper, provider(meterRegistry)
    );

    DashboardResponse sample = createSampleResponse();
    DashboardResponse result = disabledService.getOrCompute(userId, month, () -> sample);

    assertThat(result).isEqualTo(sample);
    verifyNoInteractions(redisTemplate);
    assertThat(meterRegistry.counter("dashboard.cache.requests", "result", "bypass").count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Evict user month deletes exact cache key")
  void shouldEvictSpecificUserMonth() {
    cacheService.evictUserMonth(userId, month);

    verify(redisTemplate).delete(expectedKey);
    assertThat(meterRegistry.counter("dashboard.cache.evictions", "scope", "month").count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Evict user scans and deletes all keys matching user pattern")
  void shouldEvictAllKeysForUser() {
    List<String> keys = List.of(
        "dashboard:v1:user:user-123:month:2026-01",
        "dashboard:v1:user:user-123:month:2026-02",
        "dashboard:v1:user:user-123:month:2026-03"
    );
    Cursor<String> cursor = cursorOf(keys);
    when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
    when(redisTemplate.delete(anyCollection())).thenReturn(3L);

    cacheService.evictUser(userId);

    ArgumentCaptor<ScanOptions> optionsCaptor = ArgumentCaptor.forClass(ScanOptions.class);
    verify(redisTemplate).scan(optionsCaptor.capture());
    assertThat(optionsCaptor.getValue().toOptionString()).contains("dashboard:v1:user:user-123:*");
    verify(redisTemplate).delete(keys);
    assertThat(meterRegistry.counter("dashboard.cache.evictions", "scope", "user").count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Evict user does not call delete when no keys match")
  void shouldNotDeleteWhenNoKeysMatchForUser() {
    Cursor<String> cursor = cursorOf(List.of());
    when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);

    cacheService.evictUser(userId);

    verify(redisTemplate, never()).delete(anyCollection());
    assertThat(meterRegistry.counter("dashboard.cache.evictions", "scope", "user").count()).isEqualTo(1.0);
  }

  @SuppressWarnings("unchecked")
  private Cursor<String> cursorOf(List<String> keys) {
    Cursor<String> cursor = mock(Cursor.class);
    Iterator<String> iterator = keys.iterator();
    lenient().when(cursor.hasNext()).thenAnswer(invocation -> iterator.hasNext());
    lenient().when(cursor.next()).thenAnswer(invocation -> iterator.next());
    return cursor;
  }

  @Test
  @DisplayName("Evict failure does not throw exception")
  void shouldNotThrowWhenEvictFails() {
    when(redisTemplate.delete(expectedKey)).thenThrow(new RedisConnectionFailureException("Connection lost"));

    cacheService.evictUserMonth(userId, month);

    assertThat(meterRegistry.counter("dashboard.cache.errors", "operation", "evict").count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Concurrent requests on cache miss execute value loader only once (stampede protection)")
  void shouldPreventCacheStampedeUnderConcurrentRequests() throws Exception {
    DashboardResponse sample = createSampleResponse();
    String json = objectMapper.writeValueAsString(sample);

    AtomicInteger loaderExecutions = new AtomicInteger(0);
    Supplier<DashboardResponse> loader = () -> {
      loaderExecutions.incrementAndGet();
      try {
        Thread.sleep(50); // Simulate DB query latency
      } catch (InterruptedException ignored) {
      }
      return sample;
    };

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // Initial check returns null; after first write, subsequent get calls return cached JSON
    when(valueOperations.get(expectedKey)).thenAnswer(invocation -> {
      if (loaderExecutions.get() > 0) {
        return json;
      }
      return null;
    });

    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    List<Future<DashboardResponse>> futures = new CopyOnWriteArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      futures.add(executor.submit(() -> {
        startLatch.await();
        DashboardResponse res = cacheService.getOrCompute(userId, month, loader);
        doneLatch.countDown();
        return res;
      }));
    }

    startLatch.countDown();
    boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
    executor.shutdown();

    assertThat(completed).isTrue();
    for (Future<DashboardResponse> future : futures) {
      assertThat(future.get()).isNotNull();
      assertThat(future.get().month()).isEqualTo("2026-03");
    }

    // Single-flight ensures only 1 DB load executed
    assertThat(loaderExecutions.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("Cache keys are uniquely generated per user and month")
  void shouldGenerateUniqueKeysPerUserAndMonth() {
    String key1 = cacheService.buildKey("user-1", YearMonth.of(2026, 1));
    String key2 = cacheService.buildKey("user-2", YearMonth.of(2026, 1));
    String key3 = cacheService.buildKey("user-1", YearMonth.of(2026, 2));

    assertThat(key1).isEqualTo("dashboard:v1:user:user-1:month:2026-01");
    assertThat(key2).isEqualTo("dashboard:v1:user:user-2:month:2026-01");
    assertThat(key3).isEqualTo("dashboard:v1:user:user-1:month:2026-02");
    assertThat(key1).isNotEqualTo(key2);
    assertThat(key1).isNotEqualTo(key3);
  }
}
