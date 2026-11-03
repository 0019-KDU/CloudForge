package com.cloudforge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.Cookie;

import com.cloudforge.config.JwtProperties;
import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.domain.User;
import com.cloudforge.identity.repository.UserRepository;
import com.cloudforge.support.TestUsers;
import com.cloudforge.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** The authentication lifecycle end to end, through the HTTP API and real PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class AuthenticationIT {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    TestUsers testUsers;
    @Autowired
    UserRepository users;
    @Autowired
    JwtProperties jwtProperties;

    // --- login and access tokens -----------------------------------------------------------

    @Test
    void loginIssuesBearerTokenAndHardenedRefreshCookie() throws Exception {
        User user = testUsers.create(Role.ENGINEER);

        MvcResult login = login(user.getEmail(), TestUsers.PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(cookie().httpOnly("cf_refresh", true))
                .andExpect(cookie().secure("cf_refresh", true))
                .andExpect(cookie().sameSite("cf_refresh", "Strict"))
                .andExpect(cookie().path("cf_refresh", "/api/auth"))
                .andReturn();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.roles[0]").value("ENGINEER"));
    }

    @Test
    void loginEmailIsCaseInsensitive() throws Exception {
        User user = testUsers.create(Role.ENGINEER);

        login(user.getEmail().toUpperCase(), TestUsers.PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unknownEmailAndWrongPasswordAreIndistinguishable() throws Exception {
        User user = testUsers.create(Role.ENGINEER);

        String wrongPassword = login(user.getEmail(), "not the password at all").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownUser = login("nobody-" + UUID.randomUUID() + "@example.com", "whatever password")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(wrongPassword, "$.code")).isEqualTo("INVALID_CREDENTIALS");
        assertThat((String) JsonPath.read(unknownUser, "$.detail"))
                .isEqualTo(JsonPath.read(wrongPassword, "$.detail"));
    }

    @Test
    void fiveFailuresLockTheAccountEvenForTheCorrectPassword() throws Exception {
        User user = testUsers.create(Role.ENGINEER);
        for (int i = 0; i < 5; i++) {
            login(user.getEmail(), "wrong password " + i).andExpect(status().isUnauthorized());
        }

        login(user.getEmail(), TestUsers.PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertThat(users.findById(user.getId()).orElseThrow().getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        User user = testUsers.create(Role.ENGINEER);
        user.disable();
        users.save(user);

        login(user.getEmail(), TestUsers.PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRejectMissingTamperedForgedAndExpiredTokens() throws Exception {
        User user = testUsers.create(Role.PLATFORM_ADMIN);
        String valid = accessToken(login(user.getEmail(), TestUsers.PASSWORD).andReturn());

        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());

        // Flip the role claim in the payload without re-signing.
        String[] parts = valid.split("\\.");
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "xx." + parts[2];
        expectRejected(tampered);

        // Correctly structured but signed with a different key.
        expectRejected(sign(new byte[32], user, Instant.now().plusSeconds(300), jwtProperties.audience()));
        // Right key, wrong audience.
        expectRejected(sign(jwtProperties.secretBytes(), user, Instant.now().plusSeconds(300), "another-api"));
        // Right key, expired beyond the allowed clock skew.
        expectRejected(sign(jwtProperties.secretBytes(), user, Instant.now().minusSeconds(120), jwtProperties.audience()));
        // Unsigned token ("alg": "none").
        expectRejected("eyJhbGciOiJub25lIn0." + parts[1] + ".");
    }

    // --- refresh rotation and logout ---------------------------------------------------------

    @Test
    void refreshRotatesTokenAndDetectsReuse() throws Exception {
        User user = testUsers.create(Role.ENGINEER);
        Cookie first = refreshCookie(login(user.getEmail(), TestUsers.PASSWORD).andReturn());

        MvcResult refreshed = refresh(first).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = refreshCookie(refreshed);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Replaying the rotated token is treated as theft: rejected, and the whole family revoked.
        refresh(first).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshTokenAndClearsCookie() throws Exception {
        User user = testUsers.create(Role.ENGINEER);
        Cookie cookie = refreshCookie(login(user.getEmail(), TestUsers.PASSWORD).andReturn());

        mockMvc.perform(post("/api/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("cf_refresh", 0));

        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void disablingUserRevokesTheirRefreshTokens() throws Exception {
        User admin = testUsers.create(Role.PLATFORM_ADMIN);
        User user = testUsers.create(Role.ENGINEER);
        Cookie cookie = refreshCookie(login(user.getEmail(), TestUsers.PASSWORD).andReturn());

        mockMvc.perform(post("/api/admin/users/{id}/disable", user.getId()).with(testUsers.bearer(admin)))
                .andExpect(status().isOk());

        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshPicksUpRoleChanges() throws Exception {
        User admin = testUsers.create(Role.PLATFORM_ADMIN);
        User user = testUsers.create(Role.ENGINEER);
        login(user.getEmail(), TestUsers.PASSWORD).andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/users/{id}/roles", user.getId()).with(testUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ENGINEER\",\"AUDITOR\"]}"))
                .andExpect(status().isOk());

        // The role change revoked old refresh tokens; a fresh login carries the new roles.
        String token = accessToken(login(user.getEmail(), TestUsers.PASSWORD).andReturn());
        List<String> roles = JsonPath.read(new String(Base64.getUrlDecoder().decode(token.split("\\.")[1])),
                "$.roles");
        assertThat(roles).containsExactly("AUDITOR", "ENGINEER");
    }

    // --- registration --------------------------------------------------------------------------

    @Test
    void invitedUserRegistersWithInvitationRolesOnlyAndCanLogIn() throws Exception {
        User admin = testUsers.create(Role.PLATFORM_ADMIN);
        User manager = testUsers.create(Role.MANAGER);
        String email = "new-" + UUID.randomUUID() + "@example.com";
        String token = invite(admin, email, manager.getId());

        // The extra "roles" field is ignored: privileges come only from the invitation.
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invitationToken\":\"" + token + "\",\"displayName\":\"New Hire\","
                                + "\"password\":\"a long unique passphrase\",\"roles\":[\"PLATFORM_ADMIN\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("ENGINEER"));

        User registered = users.findByEmail(email).orElseThrow();
        assertThat(registered.getManagerId()).isEqualTo(manager.getId());
        assertThat(registered.getPasswordHash()).startsWith("{bcrypt}");
        login(email, "a long unique passphrase").andExpect(status().isOk());

        // The token is single use.
        register(token, "a different passphrase").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_REDEEMABLE"));
    }

    @Test
    void registrationEnforcesPasswordPolicy() throws Exception {
        User admin = testUsers.create(Role.PLATFORM_ADMIN);
        String token = invite(admin, "weak-" + UUID.randomUUID() + "@example.com", null);

        register(token, "short").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        register(token, "Password1234").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("too common")));
    }

    @Test
    void unknownInvitationTokenIsRejected() throws Exception {
        register("definitely-not-a-real-token", "a long unique passphrase")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_REDEEMABLE"));
    }

    // --- helpers ---------------------------------------------------------------------------------

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions refresh(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(cookie));
    }

    private ResultActions register(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"invitationToken\":\"" + token + "\",\"displayName\":\"X\",\"password\":\"" + password + "\"}"));
    }

    private String invite(User admin, String email, UUID managerId) throws Exception {
        String manager = managerId == null ? "null" : "\"" + managerId + "\"";
        String body = mockMvc.perform(post("/api/admin/invitations").with(testUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"roles\":[\"ENGINEER\"],\"managerId\":" + manager + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private void expectRejected(String token) throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String sign(byte[] key, User user, Instant expiresAt, String audience) {
        var encoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(audience))
                .subject(user.getId().toString())
                .issuedAt(expiresAt.minusSeconds(600))
                .expiresAt(expiresAt)
                .claim("roles", List.of("PLATFORM_ADMIN"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims))
                .getTokenValue();
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("cf_refresh");
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
