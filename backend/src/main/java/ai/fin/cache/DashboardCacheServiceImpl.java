package ai.fin.cache;

import ai.fin.config.properties.DashboardCacheProperties;
import ai.fin.dto.dashboard.DashboardResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Slf4j
@Service
public class DashboardCacheServiceImpl implements DashboardCacheService {

  private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
  private static final int MAX_PAYLOAD_SIZE_BYTES = 512 * 1024; // 512 KB
  private static final int SCAN_BATCH_SIZE = 200;

  private final DashboardCacheProperties props;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

  public DashboardCacheServiceImpl(DashboardCacheProperties props,
                                   StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper,
                                   ObjectProvider<MeterRegistry> meterRegistryProvider) {
    this.props = props;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistryProvider.getIfAvailable();
  }

  @Override
  public DashboardResponse getOrCompute(String userId, YearMonth month, Supplier<DashboardResponse> valueLoader) {
    if (!props.enabled()) {
      recordMetric("bypass");
      return valueLoader.get();
    }

    String cacheKey = buildKey(userId, month);

    // Fast path: check cache
    DashboardResponse cached = getFromCache(cacheKey);
    if (cached != null) {
      return cached;
    }

    // Cache miss: protect against stampede via single-flight locking per cache key
    Object lock = locks.computeIfAbsent(cacheKey, k -> new Object());
    synchronized (lock) {
      try {
        // Double-check if another concurrent thread computed and populated the cache while we waited
        DashboardResponse cachedAfterWait = getFromCache(cacheKey);
        if (cachedAfterWait != null) {
          return cachedAfterWait;
        }

        // Compute from database
        recordMetric("miss");
        DashboardResponse computed = valueLoader.get();

        if (computed != null) {
          putInCache(cacheKey, computed);
        }

        return computed;
      } finally {
        locks.remove(cacheKey, lock);
      }
    }
  }

  @Override
  public void evictUserMonth(String userId, YearMonth month) {
    if (!props.enabled() || userId == null || month == null) {
      return;
    }

    String cacheKey = buildKey(userId, month);
    try {
      Boolean deleted = redisTemplate.delete(cacheKey);
      log.debug("Evicted dashboard cache for key='{}', result={}", cacheKey, deleted);
      recordEvictionMetric("month");
    } catch (Exception e) {
      log.warn("Failed to evict dashboard cache for key='{}' — Redis unavailable?", cacheKey, e);
      recordErrorMetric("evict");
    }
  }

  @Override
  public void evictUser(String userId) {
    if (!props.enabled() || userId == null) {
      return;
    }

    String pattern = props.keyPrefix() + ":user:" + userId + ":*";
    try {
      // SCAN instead of KEYS: non-blocking on the Redis server, safe for large keyspaces
      ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH_SIZE).build();
      List<String> batch = new ArrayList<>(SCAN_BATCH_SIZE);
      long deleted = 0;

      try (Cursor<String> cursor = redisTemplate.scan(options)) {
        while (cursor.hasNext()) {
          batch.add(cursor.next());
          if (batch.size() >= SCAN_BATCH_SIZE) {
            deleted += deleteBatch(batch);
          }
        }
      }
      deleted += deleteBatch(batch);

      log.debug("Evicted {} dashboard cache keys for userId='{}'", deleted, userId);
      recordEvictionMetric("user");
    } catch (Exception e) {
      log.warn("Failed to evict dashboard cache pattern='{}' — Redis unavailable?", pattern, e);
      recordErrorMetric("evict");
    }
  }

  private long deleteBatch(List<String> keys) {
    if (keys.isEmpty()) {
      return 0;
    }
    Long removed = redisTemplate.delete(List.copyOf(keys));
    keys.clear();
    return removed != null ? removed : 0;
  }

  String buildKey(String userId, YearMonth month) {
    return String.format("%s:user:%s:month:%s",
        props.keyPrefix(),
        userId,
        month.format(MONTH_FMT)
    );
  }

  private DashboardResponse getFromCache(String cacheKey) {
    try {
      String json = redisTemplate.opsForValue().get(cacheKey);
      if (json == null) {
        return null;
      }

      DashboardResponse response = objectMapper.readValue(json, DashboardResponse.class);
      recordMetric("hit");
      log.debug("Dashboard cache hit for key='{}'", cacheKey);
      return response;
    } catch (Exception e) {
      log.warn("Failed to read/deserialize dashboard cache for key='{}'. Falling back to database.", cacheKey, e);
      recordErrorMetric("get");
      return null;
    }
  }

  private void putInCache(String cacheKey, DashboardResponse response) {
    try {
      String json = objectMapper.writeValueAsString(response);
      int payloadSize = json.getBytes(StandardCharsets.UTF_8).length;
      if (payloadSize > MAX_PAYLOAD_SIZE_BYTES) {
        log.warn("Dashboard payload size ({} bytes) exceeds limit of {} bytes for key='{}'. Skipping cache.",
            payloadSize, MAX_PAYLOAD_SIZE_BYTES, cacheKey);
        return;
      }

      Duration ttl = calculateTtl(response);
      redisTemplate.opsForValue().set(cacheKey, json, ttl);
      log.debug("Cached dashboard for key='{}' with TTL={}s", cacheKey, ttl.toSeconds());
    } catch (Exception e) {
      log.warn("Failed to write dashboard cache for key='{}' — Redis unavailable?", cacheKey, e);
      recordErrorMetric("put");
    }
  }

  private Duration calculateTtl(DashboardResponse response) {
    boolean isEmpty = isEmptyDashboard(response);
    long baseSeconds = isEmpty ? props.emptyTtl().toSeconds() : props.ttl().toSeconds();
    long maxJitterSeconds = Math.max(0, props.maxJitter().toSeconds());

    long jitter = 0;
    if (maxJitterSeconds > 0) {
      // For empty results use a smaller jitter window to avoid exceeding empty TTL
      long maxJitter = isEmpty ? Math.min(30, maxJitterSeconds) : maxJitterSeconds;
      jitter = ThreadLocalRandom.current().nextLong(0, maxJitter + 1);
    }

    return Duration.ofSeconds(baseSeconds + jitter);
  }

  private boolean isEmptyDashboard(DashboardResponse response) {
    if (response == null) {
      return true;
    }

    boolean summaryEmpty = response.summary() == null
        || ((response.summary().totalIncome() == null || response.summary().totalIncome().compareTo(BigDecimal.ZERO) == 0)
        && (response.summary().totalExpense() == null || response.summary().totalExpense().compareTo(BigDecimal.ZERO) == 0));

    boolean listsEmpty = (response.expenseByCategory() == null || response.expenseByCategory().isEmpty())
        && (response.incomeByCategory() == null || response.incomeByCategory().isEmpty())
        && (response.dailyTrend() == null || response.dailyTrend().isEmpty())
        && (response.topExpenses() == null || response.topExpenses().isEmpty());

    return summaryEmpty && listsEmpty;
  }

  private void recordMetric(String result) {
    if (meterRegistry != null) {
      try {
        meterRegistry.counter("dashboard.cache.requests", "result", result).increment();
      } catch (Exception ignored) {
      }
    }
  }

  private void recordEvictionMetric(String scope) {
    if (meterRegistry != null) {
      try {
        meterRegistry.counter("dashboard.cache.evictions", "scope", scope).increment();
      } catch (Exception ignored) {
      }
    }
  }

  private void recordErrorMetric(String operation) {
    if (meterRegistry != null) {
      try {
        meterRegistry.counter("dashboard.cache.errors", "operation", operation).increment();
      } catch (Exception ignored) {
      }
    }
  }
}
