package dev.despical.tikfetch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
public class TikFetchApplication {

    static void main(String[] args) {
        SpringApplication.run(TikFetchApplication.class, args);
    }
}
