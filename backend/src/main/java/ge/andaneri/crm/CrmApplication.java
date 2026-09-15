package ge.andaneri.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Starts everything: the API, the database migrations, and (run from the IDE) the frontend build that
 * this same application serves. Open http://localhost:8082 once the log says the frontend is ready.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}
