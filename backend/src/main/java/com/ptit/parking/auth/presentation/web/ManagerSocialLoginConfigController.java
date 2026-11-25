package com.ptit.parking.auth.presentation.web;

import com.ptit.parking.auth.infrastructure.persistence.SocialLoginSettingsStore.Provider;
import com.ptit.parking.auth.presentation.web.SocialLoginClients.ProviderView;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(SocialLoginClients.MANAGER_CONFIG)
class ManagerSocialLoginConfigController {

    record UpdateRequest(@NotNull Boolean enabled, Boolean ptitEnabled, @NotNull @Size(max = 256) String clientId,
                         @Size(max = 1024) String clientSecret) {

        @Override
        public String toString() {
            return "UpdateRequest[enabled=" + enabled + ", ptitEnabled=" + ptitEnabled + "]";
        }
    }

    private final SocialLoginClients clients;

    ManagerSocialLoginConfigController(SocialLoginClients clients) {
        this.clients = clients;
    }

    @GetMapping
    List<ProviderView> view() {
        return clients.views();
    }

    @PutMapping("/{id}")
    ProviderView update(@PathVariable String id, @Valid @RequestBody UpdateRequest body) {
        return clients.update(provider(id), body.enabled(), body.ptitEnabled(), body.clientId(), body.clientSecret());
    }

    @DeleteMapping("/{id}")
    ProviderView reset(@PathVariable String id) {
        return clients.reset(provider(id));
    }

    private static Provider provider(String id) {
        return Provider.find(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }
}
