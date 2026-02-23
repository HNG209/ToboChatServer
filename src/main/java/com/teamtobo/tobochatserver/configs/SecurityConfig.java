package com.teamtobo.tobochatserver.configs;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private static final String[] PUBLIC_ENDPOINTS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/test/init"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // Tắt CSRF vì ta dùng Token (Stateless)
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Tạm tắt CORS để test cho dễ (hoặc cấu hình nếu cần)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll() // Cho phép Swagger
                        .anyRequest().authenticated() // Tất cả cái khác phải có Token
                )
                // Kích hoạt tính năng xác thực bằng JWT Token
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {})
                        .authenticationEntryPoint(new AuthEntryPoint())
                );

        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Cho phép Frontend (Next.js) và Mobile
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000", // Next.js Web
                "http://localhost:8081", // Expo Web (nếu có)
                "exp://*"                // Expo Mobile (Development)
        ));

        // Cho phép các method
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Cho phép các header (đặc biệt là Authorization để gửi Token)
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));

        // Cho phép gửi credentials (nếu sau này cần cookie, dù hiện tại dùng Bearer thì ko bắt buộc nhưng nên bật)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}