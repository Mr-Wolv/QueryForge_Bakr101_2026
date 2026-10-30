package com.bakr.queryforge.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@Profile("production")
public class ProductionSecurityConfig {

    private static final RequestMatcher API_REQUESTS = request -> {
        String path = request.getServletPath();
        return path.equals("/api") || path.startsWith("/api/");
    };

    @Bean
    @Order(1)
    SecurityFilterChain productionSecurityFilterChain(
            HttpSecurity http,
            OAuth2UserService<OidcUserRequest, OidcUser> queryForgeOidcUserService,
            ObjectMapper objectMapper) throws Exception {
        var csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/session").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").hasRole("OPERATOR")
                        .requestMatchers(HttpMethod.POST,
                                "/api/runs",
                                "/api/runs/*/start",
                                "/api/runs/*/cancel",
                                "/api/comparisons").hasRole("OPERATOR")
                        .requestMatchers("/api/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                (request, response, exception) -> writeProblem(
                                        response, objectMapper, HttpStatus.UNAUTHORIZED,
                                        "Authentication required"),
                                API_REQUESTS)
                        .defaultAccessDeniedHandlerFor(
                                (request, response, exception) -> writeProblem(
                                        response, objectMapper, HttpStatus.FORBIDDEN,
                                        "Operator access required"),
                                API_REQUESTS))
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(queryForgeOidcUserService)))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("__Host-QUERYFORGE_SESSION", "JSESSIONID", "XSRF-TOKEN")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; base-uri 'self'; object-src 'none'; "
                                        + "frame-ancestors 'none'; form-action 'self' https:; "
                                        + "img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                                        + "script-src 'self'; connect-src 'self'; font-src 'self'")));

        return http.build();
    }

    @Bean
    OAuth2UserService<OidcUserRequest, OidcUser> queryForgeOidcUserService(
            @Value("${queryforge.auth.group-claim:groups}") String groupClaim,
            @Value("${queryforge.auth.operator-groups:}") String configuredOperatorGroups) {
        OidcUserService delegate = new OidcUserService();
        var operatorGroups = Arrays.stream(configuredOperatorGroups.split(","))
                .map(String::strip)
                .filter(group -> !group.isEmpty())
                .collect(Collectors.toUnmodifiableSet());

        return request -> {
            OidcUser user = delegate.loadUser(request);
            var authorities = user.getAuthorities().stream()
                    .map(GrantedAuthority.class::cast)
                    .collect(Collectors.toSet());
            Object claim = user.getClaim(groupClaim);
            if (claim instanceof Collection<?> groups
                    && groups.stream().filter(String.class::isInstance)
                    .map(String.class::cast).anyMatch(operatorGroups::contains)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_OPERATOR"));
            }
            return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo());
        };
    }

    private static void writeProblem(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String detail) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), java.util.Map.of(
                "type", "about:blank",
                "title", status.getReasonPhrase(),
                "status", status.value(),
                "detail", detail));
    }
}
