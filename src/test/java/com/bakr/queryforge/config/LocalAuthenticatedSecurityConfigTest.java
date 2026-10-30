package com.bakr.queryforge.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the important distinction between the opt-in developer bypass and the
 * normal authenticated local Compose profile.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:local-auth-security;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=never",
        "spring.security.oauth2.client.registration.queryforge.client-id=test-client",
        "spring.security.oauth2.client.registration.queryforge.client-secret=test-secret"
})
@ActiveProfiles("local-auth")
@AutoConfigureMockMvc
class LocalAuthenticatedSecurityConfigTest {

    @Autowired
    MockMvc mvc;

    @Test
    void localAuthenticatedProfileDoesNotPermitAnonymousApiReads() throws Exception {
        mvc.perform(get("/api/runs"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void localAuthenticatedProfileEnforcesRolesAndCsrfOnRunCreation() throws Exception {
        mvc.perform(post("/api/runs")
                        .with(user("viewer"))
                        .with(csrf())
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
}
