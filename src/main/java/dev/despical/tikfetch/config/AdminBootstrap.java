package dev.despical.tikfetch.config;

import dev.despical.tikfetch.entity.Admin;
import dev.despical.tikfetch.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppProperties properties;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String @NonNull ... args) {
        if (adminRepository.count() > 0) {
            return;
        }

        Admin admin = new Admin();
        admin.setUsername(properties.admin().username());
        admin.setEmail(properties.admin().email());
        admin.setPasswordHash(passwordEncoder.encode(properties.admin().password()));

        adminRepository.save(admin);

        LOGGER.info("Created initial TikFetch admin '{}'.", admin.getUsername());
    }
}
