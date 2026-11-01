package com.bakr.queryforge.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "spring.config.import=optional:configtree:/run/secrets/",
        "spring.datasource.url=jdbc:h2:mem:production-security;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=never",
        "management.server.port=8080",
        "spring.security.oauth2.client.registration.queryforge.client-id=test-client",
        "spring.security.oauth2.client.registration.queryforge.client-secret=test-secret",
        "queryforge.auth.operator-groups=benchmark-operators",
        "queryforge.auth.enabled=true"
})
@ActiveProfiles("production")
@AutoConfigureMockMvc
class ProductionSecurityConfigTest {

    private static final HttpServer ISSUER_SERVER = startIssuerServer();

    @Autowired
    MockMvc mvc;

    @Autowired
    OAuth2UserService<OidcUserRequest, OidcUser> queryForgeOidcUserService;

    @Autowired
    ClientRegistrationRepository clientRegistrations;

    @DynamicPropertySource
    static void oidcIssuer(DynamicPropertyRegistry registry) {
        registry.add("QF_OIDC_ISSUER_URI", () -> issuerUrl());
    }

    @AfterAll
    static void stopIssuerServer() {
        ISSUER_SERVER.stop(0);
    }

    @Test
    void anonymousSessionEndpointAllowsSignInUiToRender() throws Exception {
        mvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"authEnabled":true,"authenticated":false,"operator":false,"displayName":""}
                        """))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void csrfCookieIsSecureBehindTheHttpsReverseProxy() throws Exception {
        mvc.perform(get("/api/auth/session")
                        .header("X-Forwarded-Host", "queryforge.example.test")
                        .header("X-Forwarded-Port", "443")
                        .header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(cookie().secure("XSRF-TOKEN", true));
    }

    @Test
    void backendHealthAndPrometheusScrapesAreAvailableWithoutUserLogin() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousApiReadsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/runs"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void oidcRedirectUsesTheExternalHttpsAddress() throws Exception {
        var result = mvc.perform(get("/oauth2/authorization/queryforge")
                        .header("X-Forwarded-Host", "queryforge.example.test")
                        .header("X-Forwarded-Port", "443")
                        .header("X-Forwarded-Proto", "https"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(URLDecoder.decode(location, StandardCharsets.UTF_8))
                .contains("redirect_uri=https://queryforge.example.test/login/oauth2/code/queryforge");
    }

    @Test
    void signedInReadOnlyUsersCannotCreateRuns() throws Exception {
        mvc.perform(post("/api/runs")
                        .with(user("reader"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorMutationRequiresCsrfAndPassesAuthorizationWithToken() throws Exception {
        mvc.perform(post("/api/runs")
                        .with(user("operator").roles("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/runs")
                        .with(user("operator").roles("OPERATOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void authenticatedUserCanLogOutWithCsrfToken() throws Exception {
        mvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void onlyConfiguredOidcGroupsGrantOperatorRole() {
        var operator = loadUser("operator-token");
        var reader = loadUser("reader-token");

        assertThat(operator.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_OPERATOR");
        assertThat(reader.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .doesNotContain("ROLE_OPERATOR");
    }

    private OidcUser loadUser(String accessTokenValue) {
        var client = clientRegistrations.findByRegistrationId("queryforge");
        var now = Instant.now();
        var token = new OidcIdToken(
                "test-id-token",
                now,
                now.plusSeconds(600),
                Map.of(
                        "iss", issuerUrl(),
                        "sub", "test-user",
                        "aud", List.of("test-client"),
                        "groups", List.of("operator-token".equals(accessTokenValue)
                                ? "benchmark-operators"
                                : "readers")));
        var accessToken = new OAuth2AccessToken(
                TokenType.BEARER,
                accessTokenValue,
                now,
                now.plusSeconds(600),
                Set.of("openid", "profile", "email"));
        return queryForgeOidcUserService.loadUser(new OidcUserRequest(client, accessToken, token));
    }

    private static HttpServer startIssuerServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/.well-known/openid-configuration", exchange -> {
                String issuer = issuerUrl(server);
                String body = """
                        {
                          "issuer": "%s",
                          "authorization_endpoint": "%s/authorize",
                          "token_endpoint": "%s/token",
                          "userinfo_endpoint": "%s/userinfo",
                          "jwks_uri": "%s/jwks",
                          "response_types_supported": ["code"],
                          "subject_types_supported": ["public"],
                          "id_token_signing_alg_values_supported": ["RS256"]
                        }
                        """.formatted(issuer, issuer, issuer, issuer, issuer);
                byte[] response = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(response);
                }
            });
            server.createContext("/userinfo", exchange -> {
                String group = "Bearer reader-token".equals(
                        exchange.getRequestHeaders().getFirst("Authorization"))
                        ? "readers"
                        : "benchmark-operators";
                byte[] response = ("{\"sub\":\"test-user\",\"name\":\"Test Operator\",\"groups\":[\""
                        + group + "\"]}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(response);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start the test OIDC issuer", exception);
        }
    }

    private static String issuerUrl() {
        return issuerUrl(ISSUER_SERVER);
    }

    private static String issuerUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
