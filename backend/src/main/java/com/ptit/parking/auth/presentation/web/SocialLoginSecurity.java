package com.ptit.parking.auth.presentation.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class SocialLoginSecurity {

    private final SocialLoginClients clients;
    private final SocialLoginHandlers handlers;

    SocialLoginSecurity(SocialLoginClients clients, SocialLoginHandlers handlers) {
        this.clients = clients;
        this.handlers = handlers;
    }

    public void configure(HttpSecurity http) throws Exception {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(clients, SocialLoginClients.START);
        resolver.setAuthorizationRequestCustomizer(builder ->
                builder.additionalParameters(parameters -> parameters.put("prompt", "select_account")));

        http.oauth2Login(oauth -> oauth
                .clientRegistrationRepository(clients)
                .authorizedClientRepository(new DiscardProviderTokens())
                .loginPage("/login")
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                .redirectionEndpoint(endpoint -> endpoint.baseUri(SocialLoginClients.CALLBACK + "/*"))
                .successHandler(handlers::success)
                .failureHandler(handlers::failure)
                // Spring gán kho phiên mặc định trong configure(); giữ danh tính OAuth ở phạm vi một request.
                .withObjectPostProcessor(new ObjectPostProcessor<OAuth2LoginAuthenticationFilter>() {
                    @Override
                    public <T extends OAuth2LoginAuthenticationFilter> T postProcess(T filter) {
                        filter.setSecurityContextRepository(new RequestAttributeSecurityContextRepository());
                        return filter;
                    }
                }));
    }

    private static class DiscardProviderTokens implements OAuth2AuthorizedClientRepository {

        @Override
        public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id, Authentication user,
                                                                      HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication user,
                                         HttpServletRequest request, HttpServletResponse response) {}

        @Override
        public void removeAuthorizedClient(String id, Authentication user, HttpServletRequest request,
                                           HttpServletResponse response) {}
    }
}
