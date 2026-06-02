package com.teamtobo.tobochatserver.configs;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    @Bean
    public ProxyManager<byte[]> proxyManager(RedisConnectionFactory redisConnectionFactory) {
        // Trích xuất Lettuce Client từ cấu hình pool hiện tại của bạn
        LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) redisConnectionFactory;
        RedisClient redisClient = (RedisClient) lettuceFactory.getNativeClient();

        // Cấu hình ProxyManager với chiến lược xóa Key tự động trên Redis (tránh rác RAM)
        return LettuceBasedProxyManager.builderFor(redisClient)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1))
                )
                .build();
    }
}