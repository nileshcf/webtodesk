package com.example.api_gateway.config;

import com.example.api_gateway.security.AuthenticationManager;
import com.example.api_gateway.security.SecurityContextRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;           // ✅ reactive import
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;   // ✅ reactive import

import java.util.List;


@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class GatewaySecurityConfig {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // ✅ explicit config
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authenticationManager(authenticationManager)
                .securityContextRepository(securityContextRepository)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/user/auth/login").permitAll()      // ✅ explicit
                        .pathMatchers("/user/auth/register").permitAll()   // ✅ explicit
                        .pathMatchers("/user/auth/refresh").permitAll()
                        .pathMatchers("/user/auth/**").permitAll()
                        .pathMatchers("/actuator/health").permitAll()
                        .anyExchange().authenticated()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((swe, e) -> {
                            swe.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return swe.getResponse().setComplete();
                        })
                        .accessDeniedHandler((swe, e) -> {
                            swe.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return swe.getResponse().setComplete();
                        })
                )
                .build();
    }

    // ✅ CORS lives here only — single source of truth
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "https://webtodesk.onrender.com"
        ));
        config.setAllowedOriginPatterns(List.of("https://*.onrender.com"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}