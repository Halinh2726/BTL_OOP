package com.ptit.parking.auth.presentation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ptit.parking.auth.application.AuthenticatedUser;
import com.ptit.parking.auth.application.usecase.GetCurrentUserUseCase;
import com.ptit.parking.auth.application.usecase.LoginUseCase;
import com.ptit.parking.auth.application.usecase.StudentSocialSignInUseCase;
import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.auth.infrastructure.persistence.SocialLoginSettingsStore;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.infrastructure.security.WebSecurityTestConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

@WebMvcTest(controllers = {SocialLoginController.class, AuthController.class}, properties = {
        "parking.social.origin=http://localhost:5173",
        "parking.social.google.client-id=test-google",
        "parking.social.google.client-secret=test-google-secret",
        "parking.social.microsoft.client-id=test-microsoft",
        "parking.social.microsoft.client-secret=test-microsoft-secret"
})
@Import({WebSecurityTestConfig.class, SessionSignIn.class, SocialLoginClients.class, SocialLoginSession.class,
        SocialLoginHandlers.class, SocialLoginSecurity.class, SocialLoginSettingsStore.class, LoginThrottle.class})
class SocialLoginControllerTest {

    private static final String BASE = SocialLoginClients.BASE;
    private static final String SESSION_CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    private static final AuthenticatedUser STUDENT =
            new AuthenticatedUser(12L, "sinhvien01", "Sinh Viên", Role.SINH_VIEN, List.of("WALLET_VIEW_OWN"));
    private static final String MICROSOFT_ID = "stable-microsoft-id";

    @TempDir
    static Path settingsDirectory;

    @DynamicPropertySource
    static void settingsDirectory(DynamicPropertyRegistry registry) {
        registry.add("parking.social.config-directory", () -> settingsDirectory.toString());
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    StudentSocialSignInUseCase students;

    @MockitoBean
    LoginUseCase loginUseCase;

    @MockitoBean
    GetCurrentUserUseCase getCurrentUser;

    @MockitoSpyBean
    SocialLoginClients clients;

    @Test
    void googleRedirectUsesThePublicOriginAndRejectsInvalidState() throws Exception {
        mvc.perform(get(BASE + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers", contains("google", "microsoft")));

        MvcResult started = mvc.perform(get(BASE + "/start/google"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = started.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("https://accounts.google.com/")
                .contains("code_challenge_method=S256", "nonce=", "state=");
        assertThat(queryParam(location, "redirect_uri")).isEqualTo("http://localhost:5173" + BASE + "/callback/google");
        MockHttpSession session = (MockHttpSession) started.getRequest().getSession(false);

        mvc.perform(get(BASE + "/callback/google").session(session)
                        .param("code", "unused").param("state", "wrong"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/login?social=failed"));
        assertThat(session.getAttribute(SESSION_CONTEXT)).isNull();
    }

    @Test
    void googleRegistrationUsesTheVerifiedGmailFromTheSignedIdToken() throws Exception {
        var key = new RSAKeyGenerator(2048).keyID("google-test-key").generate();
        String subject = "stable-google-id";
        String email = "test-student@gmail.com";
        HttpServer google = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        google.createContext("/keys", exchange -> respond(exchange, new JWKSet(key.toPublicJWK()).toString()));
        google.createContext("/userinfo", exchange -> respond(exchange, "{\"sub\":\"stable-google-id\"}"));
        ClientRegistration original = clients.findByRegistrationId("google");
        String origin = "http://127.0.0.1:" + google.getAddress().getPort();
        doReturn(ClientRegistration.withClientRegistration(original).tokenUri(origin + "/token")
                .jwkSetUri(origin + "/keys").userInfoUri(origin + "/userinfo").build())
                .when(clients).findByRegistrationId("google");

        MockHttpSession session = new MockHttpSession();
        String location = start(session, "google");
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer(original.getProviderDetails().getIssuerUri())
                .subject(subject).audience(original.getClientId()).issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60))).claim("nonce", queryParam(location, "nonce"))
                .claim("email", email).claim("email_verified", true).claim("name", "Sinh Viên").build();
        SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        token.sign(new RSASSASigner(key));
        google.createContext("/token", exchange -> respond(exchange,
                "{\"access_token\":\"test-access-token\",\"token_type\":\"Bearer\",\"id_token\":\"%s\"}"
                        .formatted(token.serialize())));
        google.start();
        try {
            callback(session, "google", location, "/register/social");
            String id = pendingId(session, email);
            when(students.register("google", subject, "B21DCCN001", "Sinh Viên", "sinhvien01", email, null))
                    .thenReturn(STUDENT);
            mvc.perform(post(BASE + "/register").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(registration(id)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.user.id").value(STUDENT.id()));
        } finally {
            google.stop(0);
        }
    }

    @Test
    void microsoftFirstSignInRequiresRegistrationThenSignsIntoTheSameStudent() throws Exception {
        HttpServer microsoft = stubMicrosoft();
        try {
            MockHttpSession session = new MockHttpSession();
            completeMicrosoftCallback(session, "/register/social");
            assertThat(session.getAttribute(SESSION_CONTEXT)).isNull();
            String password = "MatKhauMoi@123";
            var registration = JsonPath.parse(registration(pendingId(session, "student@ptit.edu.vn")));
            String body = registration.put("$", "password", password).jsonString();
            when(students.register("microsoft", MICROSOFT_ID, "B21DCCN001", "Sinh Viên", "sinhvien01", null, password))
                    .thenReturn(STUDENT);

            String weakPassword = registration.put("$", "password", "short").jsonString();
            mvc.perform(post(BASE + "/register").session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(weakPassword))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields[0].field").value("password"));
            mvc.perform(post(BASE + "/register").session(session).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
            mvc.perform(post(BASE + "/register").session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.user.id").value(STUDENT.id()))
                    .andExpect(jsonPath("$.password").doesNotExist());
            assertThat(((SecurityContextImpl) session.getAttribute(SESSION_CONTEXT)).getAuthentication().getPrincipal())
                    .isEqualTo(STUDENT);

            when(students.findLinkedUser("microsoft", MICROSOFT_ID)).thenReturn(Optional.of(STUDENT));
            MockHttpSession newSession = new MockHttpSession();
            completeMicrosoftCallback(newSession, "/student");
            assertThat(((SecurityContextImpl) newSession.getAttribute(SESSION_CONTEXT)).getAuthentication().getPrincipal())
                    .isEqualTo(STUDENT);
        } finally {
            microsoft.stop(0);
        }
    }

    @Test
    void microsoftLinkPreservesTheLocalSessionAndRequiresConfirmation() throws Exception {
        HttpServer microsoft = stubMicrosoft();
        try {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute(SESSION_CONTEXT,
                    new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(STUDENT, null, List.of())));

            completeMicrosoftCallback(session, "/account?social=confirm");
            assertThat(((SecurityContextImpl) session.getAttribute(SESSION_CONTEXT)).getAuthentication().getPrincipal())
                    .isEqualTo(STUDENT);
            String body = "{\"id\":\"%s\"}".formatted(pendingId(session, "student@ptit.edu.vn"));
            mvc.perform(post(BASE + "/link").session(session).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(post(BASE + "/link").session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNoContent());
            verify(students).link(STUDENT, "microsoft", MICROSOFT_ID);
        } finally {
            microsoft.stop(0);
        }
    }

    @Test
    void verifiedSocialSignupCanContinueToLinkAfterPasswordSignIn() throws Exception {
        HttpServer microsoft = stubMicrosoft();
        try {
            MockHttpSession session = new MockHttpSession();
            completeMicrosoftCallback(session, "/register/social");
            String id = pendingId(session, "student@ptit.edu.vn");
            when(loginUseCase.login(STUDENT.username(), "WrongPassword"))
                    .thenThrow(new DomainException(ErrorCode.INVALID_CREDENTIALS));
            when(loginUseCase.login(STUDENT.username(), "ExistingPassword")).thenReturn(STUDENT);

            mvc.perform(post("/api/v1/auth/login").session(session).contentType(MediaType.APPLICATION_JSON)
                            .content(passwordLogin(id, "WrongPassword")))
                    .andExpect(status().isUnauthorized());
            assertThat(pendingId(session, "student@ptit.edu.vn")).isEqualTo(id);

            MockHttpSession otherSession = new MockHttpSession();
            mvc.perform(post("/api/v1/auth/login").session(otherSession).contentType(MediaType.APPLICATION_JSON)
                            .content(passwordLogin(id, "ExistingPassword")))
                    .andExpect(status().isOk());
            mvc.perform(get(BASE + "/status").session(otherSession))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.pending").doesNotExist());

            mvc.perform(post("/api/v1/auth/login").session(session).contentType(MediaType.APPLICATION_JSON)
                            .content(passwordLogin(id, "ExistingPassword")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(STUDENT.id()));
            assertThat(pendingId(session, "student@ptit.edu.vn")).isEqualTo(id);
            verify(students, never()).link(STUDENT, "microsoft", MICROSOFT_ID);

            String confirmation = "{\"id\":\"%s\"}".formatted(id);
            mvc.perform(post(BASE + "/link").session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(confirmation))
                    .andExpect(status().isNoContent());
            verify(students).link(STUDENT, "microsoft", MICROSOFT_ID);
            mvc.perform(post(BASE + "/link").session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(confirmation))
                    .andExpect(status().isUnprocessableEntity());
        } finally {
            microsoft.stop(0);
        }
    }

    @Test
    void ptitSignInUsesTheSchoolTenantAndKeepsTheMicrosoftIdentity() throws Exception {
        String registrationId = SocialLoginClients.MICROSOFT_PTIT;
        var registration = clients.findByRegistrationId(registrationId);
        assertThat(registration.getProviderDetails().getTokenUri())
                .isEqualTo("https://login.microsoftonline.com/ptit.edu.vn/oauth2/v2.0/token");
        HttpServer microsoft = stubMicrosoft(registrationId);
        try {
            MockHttpSession session = new MockHttpSession();
            String location = start(session, registrationId);
            assertThat(location).startsWith("https://login.microsoftonline.com/ptit.edu.vn/oauth2/v2.0/authorize")
                    .contains("code_challenge_method=S256", "state=");
            assertThat(queryParam(location, "redirect_uri"))
                    .isEqualTo("http://localhost:5173" + BASE + "/callback/microsoft");
            callback(session, "microsoft", location, "/register/social");
            mvc.perform(get(BASE + "/status").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pending.provider").value("microsoft"));
            verify(students).findLinkedUser("microsoft", MICROSOFT_ID);
        } finally {
            microsoft.stop(0);
        }
    }

    private void completeMicrosoftCallback(MockHttpSession session, String redirectTo) throws Exception {
        String location = start(session, "microsoft");
        assertThat(location).startsWith("https://login.microsoftonline.com/common/")
                .contains("code_challenge_method=S256", "state=");
        callback(session, "microsoft", location, redirectTo);
    }

    private String start(MockHttpSession session, String provider) throws Exception {
        return mvc.perform(get(BASE + "/start/" + provider).session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
    }

    private void callback(MockHttpSession session, String provider, String location, String redirectTo) throws Exception {
        mvc.perform(get(BASE + "/callback/" + provider).session(session)
                        .param("code", "test-authorization-code").param("state", queryParam(location, "state")))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", redirectTo));
    }

    private String pendingId(MockHttpSession session, String label) throws Exception {
        MvcResult result = mvc.perform(get(BASE + "/status").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending.label").value(label))
                .andExpect(jsonPath("$.pending.subject").doesNotExist())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.pending.id");
    }

    private static String registration(String id) {
        return """
                {"id":"%s","studentCode":"b21dccn001","fullName":"Sinh Viên","username":"sinhvien01"}
                """.formatted(id);
    }

    private static String passwordLogin(String id, String password) {
        return """
                {"username":"%s","password":"%s","socialLinkId":"%s"}
                """.formatted(STUDENT.username(), password, id);
    }

    private HttpServer stubMicrosoft() throws IOException {
        return stubMicrosoft("microsoft");
    }

    private HttpServer stubMicrosoft(String registrationId) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> respond(exchange,
                "{\"access_token\":\"test-access-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}"));
        server.createContext("/me", exchange -> respond(exchange,
                """
                {"id":"%s","displayName":"Sinh Viên","userPrincipalName":"student@ptit.edu.vn"}
                """.formatted(MICROSOFT_ID)));
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        ClientRegistration original = clients.findByRegistrationId(registrationId);
        doReturn(ClientRegistration.withClientRegistration(original).tokenUri(origin + "/token")
                .userInfoUri(origin + "/me").build()).when(clients).findByRegistrationId(registrationId);
        return server;
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        try (exchange) {
            exchange.getRequestBody().readAllBytes();
            byte[] json = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
        }
    }

    private static String queryParam(String location, String key) {
        String value = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst(key);
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
