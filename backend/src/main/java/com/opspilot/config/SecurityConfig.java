package com.opspilot.config;

import com.opspilot.security.JwtAuthenticationFilter;
import com.opspilot.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http, UserDetailsService metricsUserDetailsService,
                                                     BCryptPasswordEncoder passwordEncoder) throws Exception {
        DaoAuthenticationProvider metricsAuthenticationProvider = new DaoAuthenticationProvider(metricsUserDetailsService);
        metricsAuthenticationProvider.setPasswordEncoder(passwordEncoder);
        RequestMatcher actuatorBasePath = PathPatternRequestMatcher.withDefaults().matcher("/actuator/**");
        RequestMatcher health = PathPatternRequestMatcher.withDefaults().matcher("/actuator/health");
        RequestMatcher healthSubpaths = PathPatternRequestMatcher.withDefaults().matcher("/actuator/health/**");
        RequestMatcher prometheus = PathPatternRequestMatcher.withDefaults().matcher("/actuator/prometheus");
        RequestMatcher livez = PathPatternRequestMatcher.withDefaults().matcher("/livez");
        RequestMatcher readyz = PathPatternRequestMatcher.withDefaults().matcher("/readyz");
        RequestMatcher actuatorEndpoints = new OrRequestMatcher(
                actuatorBasePath, livez, readyz);
        return http.securityMatcher(actuatorEndpoints)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(metricsAuthenticationProvider)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(health, healthSubpaths, livez, readyz).permitAll()
                        .requestMatchers(prometheus).hasRole("METRICS")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
                                            RestAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    UserDetailsService metricsUserDetailsService(
            @Value("${app.metrics.username}") String username,
            @Value("${app.metrics.password}") String password,
            BCryptPasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("METRICS")
                .build());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(CorsOriginPolicy.parse(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
