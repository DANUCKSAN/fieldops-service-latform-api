package org.electrifyingaustralia.fieldops.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityProblemHandler problemHandler,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        ))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/error",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/users/me"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/installers"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                "/api/v1/products/**",
                                "/api/v1/inventory/**",
                                "/api/v1/installers/**",
                                "/api/v1/jobs/**"
                        ).hasAnyRole("ADMIN", "WAREHOUSE_OPR")
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                jwtAuthenticationConverter
                        )))
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter =
                new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );
        return authenticationConverter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${fieldops.security.allowed-origins}")
            List<String> allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

}
