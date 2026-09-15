package ge.andaneri.crm.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.security.IpAccessFilter;
import ge.andaneri.crm.security.IpRules;
import ge.andaneri.crm.security.RequestLogFilter;
import ge.andaneri.crm.security.SecurityLog;
import ge.andaneri.crm.service.SettingsService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless bearer-token authentication for the JSON API under /api, and the frontend's files served
 * openly from everywhere else. The token is signed here (HS256) at sign-in and carries the user id as
 * its subject, the role as {@code roles} and the session version as {@code tv}. No cookies, so no CSRF.
 *
 * <p>Before authentication every API request passes the request log and the IP rules.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SecurityLog securityLog, SettingsService settings, UserRepository users,
            IpRules ipRules, CrmProperties properties, JwtDecoder jwtDecoder) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/root/**").hasRole("ROOT")
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "ROOT")
                        .requestMatchers("/api/**").authenticated()
                        // Everything else is the frontend: index.html, scripts, fonts.
                        .anyRequest().permitAll())
                .addFilterBefore(new RequestLogFilter(securityLog, settings, users), BearerTokenAuthenticationFilter.class)
                .addFilterBefore(new IpAccessFilter(ipRules, properties, jwtDecoder), BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter()))
                        .authenticationEntryPoint((request, response, ex) -> writeProblem(response, 401, "UNAUTHENTICATED")))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) -> writeProblem(response, 401, "UNAUTHENTICATED"))
                        .accessDeniedHandler((request, response, ex) -> writeProblem(response, 403, "FORBIDDEN")))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable);
        return http.build();
    }

    private static JwtAuthenticationConverter authenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    SecretKey jwtKey(CrmProperties properties) {
        byte[] secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("crm.jwt-secret (JWT_SECRET) must be at least 32 bytes long");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtKey) {
        return NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** bcrypt today, with the algorithm stored in each hash so it can be upgraded later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CrmProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = properties.corsOrigins() == null ? List.of()
                : properties.corsOrigins().stream().filter(origin -> !origin.isBlank()).toList();
        if (!origins.isEmpty()) {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(origins);
            cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
            cors.setAllowedHeaders(List.of("Content-Type", "Authorization"));
            cors.setExposedHeaders(List.of("Content-Disposition"));
            cors.setMaxAge(Duration.ofHours(1));
            source.registerCorsConfiguration("/api/**", cors);
        }
        return source;
    }

    private static void writeProblem(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"status\":" + status + ",\"code\":\"" + code + "\"}");
    }
}
