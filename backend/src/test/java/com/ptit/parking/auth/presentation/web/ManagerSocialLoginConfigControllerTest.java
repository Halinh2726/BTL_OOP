package com.ptit.parking.auth.presentation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.auth.application.usecase.StudentSocialSignInUseCase;
import com.ptit.parking.auth.infrastructure.persistence.SocialLoginSettingsStore;
import com.ptit.parking.auth.infrastructure.persistence.SocialLoginSettingsStore.Provider;
import com.ptit.parking.shared.infrastructure.security.WebSecurityTestConfig;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {ManagerSocialLoginConfigController.class, SocialLoginController.class}, properties = {
        "parking.social.google.client-id=", "parking.social.google.client-secret=",
        "parking.social.microsoft.client-id=", "parking.social.microsoft.client-secret="
})
@Import({WebSecurityTestConfig.class, SessionSignIn.class, SocialLoginClients.class, SocialLoginSession.class,
        SocialLoginHandlers.class, SocialLoginSecurity.class, SocialLoginSettingsStore.class})
class ManagerSocialLoginConfigControllerTest {

    private static final String CONFIG = SocialLoginClients.MANAGER_CONFIG;
    private static final String GOOGLE = CONFIG + "/google";

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

    @Test
    void managerCanEnableLoginImmediatelyAndKeepAStoredSecretWithoutReadingItFromTheApi() throws Exception {
        mvc.perform(get(SocialLoginClients.BASE + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers").isEmpty());

        mvc.perform(put(GOOGLE).with(user("manager").roles("QUAN_LY")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"clientId":"sample-client-id","clientSecret":"sample-client-secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.hasSecret").value(true))
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
        assertThat(new SocialLoginSettingsStore(settingsDirectory.toString()).find(Provider.GOOGLE))
                .hasValueSatisfying(settings -> assertThat(settings.clientSecret()).isEqualTo("sample-client-secret"));
        mvc.perform(get(CONFIG).with(user("manager").roles("QUAN_LY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].redirectUri").value(containsString("/auth/social/callback/google")))
                .andExpect(jsonPath("$[0].clientSecret").doesNotExist());
        mvc.perform(get(SocialLoginClients.BASE + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[0]").value("google"));
        mvc.perform(get(SocialLoginClients.START + "/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("client_id=sample-client-id")));

        mvc.perform(put(GOOGLE).with(user("manager").roles("QUAN_LY")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"enabled":false,"clientId":"sample-client-id"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.hasSecret").value(true));
        mvc.perform(put(GOOGLE).with(user("manager").roles("QUAN_LY")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"enabled":true,"clientId":"different-client"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(GOOGLE).with(user("manager").roles("QUAN_LY")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSecret").value(false))
                .andExpect(jsonPath("$.managed").value(false));
    }

    @Test
    void managerCanToggleMicrosoftPersonalAndPtitSeparately() throws Exception {
        String microsoft = CONFIG + "/microsoft";
        mvc.perform(put(microsoft).with(user("manager").roles("QUAN_LY")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":false,"ptitEnabled":true,"clientId":"ms-id","clientSecret":"ms-secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.ptitActive").value(true));
        mvc.perform(get(SocialLoginClients.BASE + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.length()").value(1))
                .andExpect(jsonPath("$.providers[0]").value("microsoft-ptit"));
        mvc.perform(get(SocialLoginClients.START + "/microsoft-ptit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("ptit.edu.vn")));

        mvc.perform(put(microsoft).with(user("manager").roles("QUAN_LY")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"ptitEnabled":false,"clientId":"ms-id"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.ptitActive").value(false));
        mvc.perform(get(SocialLoginClients.BASE + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.length()").value(1))
                .andExpect(jsonPath("$.providers[0]").value("microsoft"));

        mvc.perform(delete(microsoft).with(user("manager").roles("QUAN_LY")).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void configurationRequiresTheManagerRoleAndCsrf() throws Exception {
        mvc.perform(get(CONFIG)).andExpect(status().isUnauthorized());
        mvc.perform(get(CONFIG).with(user("student").roles("SINH_VIEN"))).andExpect(status().isForbidden());
        mvc.perform(put(GOOGLE).with(user("student").roles("SINH_VIEN")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(put(GOOGLE).with(user("manager").roles("QUAN_LY")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }
}
