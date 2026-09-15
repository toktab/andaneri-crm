package ge.andaneri.crm.web;

import ge.andaneri.crm.CrmApplication;
import ge.andaneri.crm.config.CrmProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Where the frontend project and its built files are, whichever folder the application was started from. */
@Component
public class FrontendLocation {

    private final CrmProperties properties;

    public FrontendLocation(CrmProperties properties) {
        this.properties = properties;
    }

    /** The frontend project (the folder with package.json): FRONTEND_DIR, or ./frontend, or ../frontend. */
    public Optional<Path> projectDir() {
        if (properties.frontendDir() != null && !properties.frontendDir().isBlank()) {
            Path configured = Path.of(properties.frontendDir()).toAbsolutePath().normalize();
            return Files.isRegularFile(configured.resolve("package.json")) ? Optional.of(configured) : Optional.empty();
        }
        return List.of(Path.of("frontend"), Path.of("..", "frontend")).stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(path -> Files.isRegularFile(path.resolve("package.json")))
                .findFirst();
    }

    /** Built files to serve from disk: FRONTEND_DIST, or the frontend project's dist folder. */
    public Optional<Path> dist() {
        if (properties.frontendDist() != null && !properties.frontendDist().isBlank()) {
            return Optional.of(Path.of(properties.frontendDist()).toAbsolutePath().normalize());
        }
        return projectDir().map(dir -> dir.resolve("dist"));
    }

    /** True inside a packaged jar (a server), false when started from the IDE or with spring-boot:run. */
    public boolean runningFromJar() {
        try {
            return CrmApplication.class.getProtectionDomain().getCodeSource().getLocation().toString().contains(".jar");
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
