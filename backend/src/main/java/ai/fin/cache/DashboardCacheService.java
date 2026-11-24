package ai.fin.cache;

import ai.fin.dto.dashboard.DashboardResponse;

import java.time.YearMonth;
import java.util.function.Supplier;

/**
 * Service for caching and managing aggregated dashboard responses in Redis.
 */
public interface DashboardCacheService {

  /**
   * Retrieves the cached DashboardResponse for the specified user and month, or executes the valueLoader
   * on a cache miss, caching the result with stampede and penetration protection.
   *
   * @param userId      The user's ID
   * @param month       The YearMonth requested
   * @param valueLoader The fallback supplier that queries the database
   * @return The DashboardResponse
   */
  DashboardResponse getOrCompute(String userId, YearMonth month, Supplier<DashboardResponse> valueLoader);

  /**
   * Evicts the cached dashboard for a specific user and month.
   *
   * @param userId The user's ID
   * @param month  The YearMonth to evict
   */
  void evictUserMonth(String userId, YearMonth month);

  /**
   * Evicts all cached dashboard months for a specific user.
   *
   * @param userId The user's ID
   */
  void evictUser(String userId);
}
