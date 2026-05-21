package com.suryakiran.taskmanagementtool.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration class for caching.
 * Enables caching annotations and configures cache manager.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Primary cache manager using in-memory caching.
     * Can be replaced with Redis or other distributed caching solutions.
     * @return cache manager
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        
        // Define cache names used in the application
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "tasks",
            "userTasks", 
            "taskById",
            "userTaskById",
            "filteredTasks",
            "userFilteredTasks",
            "allUsers",
            "userById",
            "tokenRoles"
        ));
        
        return cacheManager;
    }

    /**
     * Alternative Redis cache manager configuration.
     * Uncomment and configure for production use with Redis.
     */
    /*
    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(config)
            .build();
    }
    */
}
