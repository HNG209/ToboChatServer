package com.teamtobo.tobochatserver.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserRateLimit {
    String apiName();
    int capacity() default 5;
    int refillTokens() default 1;
    int refillSeconds() default 1;
}
