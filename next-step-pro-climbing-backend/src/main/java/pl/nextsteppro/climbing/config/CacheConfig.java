package pl.nextsteppro.climbing.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
            // Calendar caches: short TTL (2 min) — real-time booking data
            build("calendarMonth", 200, 2),
            build("calendarWeek",  200, 2),
            build("calendarDay",   200, 2),
            // News/courses: longer TTL — content changes rarely
            build("newsList",       50, 10),
            build("newsDetail",    100, 30),
            build("courseList",     50, 30),
            build("courseDetail",  100, 60),
            build("videoList",      50, 30)
        ));
        return manager;
    }

    private static CaffeineCache build(String name, int maxSize, int ttlMinutes) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .build());
    }
}
