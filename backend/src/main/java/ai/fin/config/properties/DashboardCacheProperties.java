package ai.fin.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.cache.dashboard")
public record DashboardCacheProperties(

    @DefaultValue("true")
    boolean enabled,

    @DefaultValue("15m")
    Duration ttl,

    @DefaultValue("2m")
    Duration emptyTtl,

    @DefaultValue("60s")
    Duration maxJitter,

    @DefaultValue("dashboard:v1")
    String keyPrefix
) {
}
