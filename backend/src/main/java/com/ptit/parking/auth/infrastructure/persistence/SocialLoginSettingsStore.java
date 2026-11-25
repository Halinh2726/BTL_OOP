package com.ptit.parking.auth.infrastructure.persistence;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Lưu cấu hình do quản lý nhập trong thư mục riêng của backend. */
@Component
public class SocialLoginSettingsStore {

    public enum Provider {
        GOOGLE("google"), MICROSOFT("microsoft");

        private final String id;

        Provider(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Optional<Provider> find(String id) {
            return switch (id) {
                case "google" -> Optional.of(GOOGLE);
                case "microsoft" -> Optional.of(MICROSOFT);
                case null, default -> Optional.empty();
            };
        }
    }

    public record Settings(boolean enabled, boolean ptitEnabled, String clientId, String clientSecret) {

        public boolean hasSecret() {
            return !clientSecret.isBlank();
        }

        public boolean hasCredentials() {
            return !clientId.isBlank() && hasSecret();
        }

        public boolean active() {
            return enabled && hasCredentials();
        }

        public boolean ptitActive() {
            return ptitEnabled && hasCredentials();
        }

        public boolean anyActive() {
            return active() || ptitActive();
        }

        @Override
        public String toString() {
            return "Settings[enabled=" + enabled + ", ptitEnabled=" + ptitEnabled + ", hasSecret=" + hasSecret() + "]";
        }
    }

    private final Path directory;
    private final boolean posix;

    public SocialLoginSettingsStore(@Value("${parking.social.config-directory}") String directory) {
        this.directory = Path.of(directory);
        this.posix = this.directory.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    public Optional<Settings> find(Provider provider) {
        try (var input = Files.newInputStream(path(provider))) {
            Properties saved = new Properties();
            saved.load(input);
            boolean enabled = Boolean.parseBoolean(saved.getProperty("enabled"));
            boolean ptitEnabled = provider == Provider.MICROSOFT
                    && (saved.containsKey("ptitEnabled")
                    ? Boolean.parseBoolean(saved.getProperty("ptitEnabled"))
                    : enabled);
            return Optional.of(new Settings(enabled, ptitEnabled,
                    saved.getProperty("clientId", ""), saved.getProperty("clientSecret", "")));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "Không đọc được cấu hình đăng nhập.");
        }
    }

    public void save(Provider provider, Settings settings) {
        try {
            if (posix) {
                Files.createDirectories(directory,
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            } else {
                Files.createDirectories(directory);
            }
            Path temporary = Files.createTempFile(directory, provider.id(), ".tmp");
            try {
                Properties saved = new Properties();
                saved.setProperty("enabled", Boolean.toString(settings.enabled()));
                saved.setProperty("ptitEnabled", Boolean.toString(provider == Provider.MICROSOFT && settings.ptitEnabled()));
                saved.setProperty("clientId", settings.clientId());
                saved.setProperty("clientSecret", settings.clientSecret());
                try (var output = Files.newOutputStream(temporary)) {
                    saved.store(output, null);
                }
                Files.move(temporary, path(provider), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "Không lưu được cấu hình đăng nhập.");
        }
    }

    public void reset(Provider provider) {
        try {
            Files.deleteIfExists(path(provider));
        } catch (IOException e) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "Không khôi phục được cấu hình đăng nhập.");
        }
    }

    private Path path(Provider provider) {
        return directory.resolve(provider.id() + ".properties");
    }
}
