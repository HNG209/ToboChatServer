package com.teamtobo.tobochatserver.services.impl;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final ProxyManager<byte[]> proxyManager;

    public Bucket resolveBucket(String userId, String apiName, int capacity, int refillTokens, int refillSeconds) {
        // Tạo Key duy nhất. vd: rate_limit:sendMessage:user_123
        String redisKey = "rate_limit:" + apiName + ":" + userId;

        Supplier<BucketConfiguration> configSupplier = () -> {
            Refill refill = Refill.intervally(refillTokens, Duration.ofSeconds(refillSeconds));
            Bandwidth limit = Bandwidth.classic(capacity, refill);
            return BucketConfiguration.builder().addLimit(limit).build();
        };

        return proxyManager.builder().build(redisKey.getBytes(), configSupplier);
    }
}