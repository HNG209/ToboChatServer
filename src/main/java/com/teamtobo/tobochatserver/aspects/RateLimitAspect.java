package com.teamtobo.tobochatserver.aspects;

import com.teamtobo.tobochatserver.annotations.UserRateLimit;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.impl.RateLimiterService;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;

    @Around("@annotation(rateLimitDef)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, UserRateLimit rateLimitDef) throws Throwable {

        String userId = getCurrentUserId();

        if (userId == null || userId.isEmpty()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Bucket userBucket = rateLimiterService.resolveBucket(
                userId,
                rateLimitDef.apiName(),
                rateLimitDef.capacity(),
                rateLimitDef.refillTokens(),
                rateLimitDef.refillSeconds()
        );

        if (userBucket.tryConsume(1)) {
            return joinPoint.proceed();
        } else {
            // block
            throw new AppException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }

    private String getCurrentUserId() {
        Object principal = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        if (principal instanceof Jwt jwt) {
            return jwt.getSubject();
        }

        throw new RuntimeException("Invalid JWT");
    }
}