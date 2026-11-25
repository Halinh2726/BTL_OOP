package com.ptit.parking.auth.presentation.web;

import com.ptit.parking.auth.infrastructure.persistence.SocialLoginSettingsStore;
import com.ptit.parking.auth.infrastructure.persistence.SocialLoginSettingsStore.Provider;
import com.ptit.parking.auth.infrastructure.persistence.SocialLoginSettingsStore.Settings;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

@Component
public class SocialLoginClients implements ClientRegistrationRepository {

    public static final String BASE = "/api/v1/auth/social";
    public static final String START = BASE + "/start";
    public static final String CALLBACK = BASE + "/callback";
    public static final String MANAGER_CONFIG = "/api/v1/manager/social-login-config";
    static final String MICROSOFT_PTIT = "microsoft-ptit";

    record ProviderView(String provider, String clientId, boolean hasSecret, boolean enabled, boolean active,
                        boolean managed, String redirectUri) {}

    private final SocialLoginSettingsStore store;
    private final Map<Provider, Settings> defaults;
    private final URI origin;

    SocialLoginClients(SocialLoginSettingsStore store,
                       @Value("${parking.social.origin}") String origin,
                       @Value("${parking.social.google.client-id}") String googleId,
                       @Value("${parking.social.google.client-secret}") String googleSecret,
                       @Value("${parking.social.microsoft.client-id}") String microsoftId,
                       @Value("${parking.social.microsoft.client-secret}") String microsoftSecret) {
        this.store = store;
        this.origin = publicOrigin(origin);
        this.defaults = Map.of(Provider.GOOGLE, environment(googleId, googleSecret),
                Provider.MICROSOFT, environment(microsoftId, microsoftSecret));
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        if (MICROSOFT_PTIT.equals(registrationId)) {
            return registration(Provider.MICROSOFT, registrationId);
        }
        return Provider.find(registrationId).map(provider -> registration(provider, provider.id())).orElse(null);
    }

    public List<String> providers() {
        return Arrays.stream(Provider.values()).filter(provider -> effective(provider).active())
                .map(Provider::id).toList();
    }

    boolean isActive(String id) {
        return Provider.find(id).map(this::effective).map(Settings::active).orElse(false);
    }

    List<ProviderView> views() {
        return Arrays.stream(Provider.values()).map(this::view).toList();
    }

    synchronized ProviderView update(Provider provider, boolean enabled, String clientId, String rawSecret) {
        Settings original = effective(provider);
        String id = clientId.strip();
        String secret = rawSecret == null ? "" : rawSecret.strip();
        if (secret.isEmpty() && id.equals(original.clientId())) {
            secret = original.clientSecret();
        }
        Settings settings = new Settings(enabled, id, secret);
        if (enabled && !settings.active()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Vui lòng nhập Client ID và Client Secret để bật đăng nhập.");
        }
        store.save(provider, settings);
        return toView(provider, settings, true);
    }

    synchronized ProviderView reset(Provider provider) {
        store.reset(provider);
        return toView(provider, defaults.get(provider), false);
    }

    private ProviderView view(Provider provider) {
        return store.find(provider).map(settings -> toView(provider, settings, true))
                .orElseGet(() -> toView(provider, defaults.get(provider), false));
    }

    private ProviderView toView(Provider provider, Settings settings, boolean managed) {
        return new ProviderView(provider.id(), settings.clientId(), settings.hasSecret(), settings.enabled(),
                settings.active(), managed, redirectUri(provider));
    }

    private Settings effective(Provider provider) {
        return store.find(provider).orElse(defaults.get(provider));
    }

    private ClientRegistration registration(Provider provider, String registrationId) {
        Settings settings = effective(provider);
        if (!settings.active()) {
            return null;
        }
        var builder = switch (provider) {
            case GOOGLE -> CommonOAuth2Provider.GOOGLE.getBuilder(provider.id())
                    .tokenUri("https://oauth2.googleapis.com/token")
                    .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo");
            case MICROSOFT -> microsoft(registrationId);
        };
        return builder.clientId(settings.clientId())
                .clientSecret(settings.clientSecret())
                .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build())
                .redirectUri(redirectUri(provider))
                .build();
    }

    private String redirectUri(Provider provider) {
        return origin + CALLBACK + "/" + provider.id();
    }

    private static ClientRegistration.Builder microsoft(String registrationId) {
        String tenant = MICROSOFT_PTIT.equals(registrationId) ? "ptit.edu.vn" : "common";
        String authority = "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0";
        return ClientRegistration.withRegistrationId(registrationId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri(authority + "/authorize")
                .tokenUri(authority + "/token")
                .scope("https://graph.microsoft.com/User.Read")
                .userInfoUri("https://graph.microsoft.com/v1.0/me?$select=id,displayName,mail,userPrincipalName")
                .userNameAttributeName("id")
                .clientName("Microsoft");
    }

    private static Settings environment(String clientId, String clientSecret) {
        return new Settings(!clientId.isBlank() && !clientSecret.isBlank(), clientId.strip(), clientSecret.strip());
    }

    private static URI publicOrigin(String raw) {
        URI origin = URI.create(raw.strip().replaceFirst("/$", ""));
        boolean loopback = "localhost".equalsIgnoreCase(origin.getHost()) || "127.0.0.1".equals(origin.getHost());
        if (origin.getHost() == null || origin.getRawUserInfo() != null || !origin.getRawPath().isEmpty()
                || origin.getRawQuery() != null || origin.getRawFragment() != null
                || !("https".equals(origin.getScheme()) || ("http".equals(origin.getScheme()) && loopback))) {
            throw new IllegalArgumentException("OAUTH_PUBLIC_ORIGIN must be an HTTPS origin or a local HTTP origin");
        }
        return origin;
    }
}
