package ge.andaneri.crm.web;

import ge.andaneri.crm.config.CrmProperties;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Run from the IDE, the backend also builds the frontend and rebuilds it on every saved change
 * ({@code npm run build:watch}), so starting CrmApplication is all it takes. It installs packages the
 * first time, writes the watcher's process id next to them, ends the watcher on shutdown, and ends a
 * leftover one from a run that was killed. A packaged jar never does this: it serves a finished build.
 *
 * <p>Switch off with FRONTEND_AUTOSTART=false, for example when running {@code npm run dev} for hot reload.
 */
@Component
public class FrontendDevRunner {

    private static final Logger log = LoggerFactory.getLogger(FrontendDevRunner.class);
    private static final String PID_FILE = ".andaneri-build.pid";

    private final CrmProperties properties;
    private final FrontendLocation location;
    private final Environment environment;
    private volatile Process watcher;
    private volatile Path pidFile;

    public FrontendDevRunner(CrmProperties properties, FrontendLocation location, Environment environment) {
        this.properties = properties;
        this.location = location;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        String url = "http://localhost:" + environment.getProperty("server.port", "8080");
        if (!properties.frontendAutostart() || location.runningFromJar()) {
            log.info("Andaneri CRM is running: {}", url);
            return;
        }
        Path dir = location.projectDir().orElse(null);
        if (dir == null) {
            log.warn("No frontend folder found next to the backend (set FRONTEND_DIR). The API is running at {}", url);
            return;
        }
        Thread thread = new Thread(() -> run(dir, url), "frontend-build");
        thread.setDaemon(true);
        thread.start();
    }

    private void run(Path dir, String url) {
        try {
            if (!Files.isDirectory(dir.resolve("node_modules"))) {
                log.info("[frontend] first start: installing packages (npm install)...");
                Process install = command(dir, "install").start();
                pipe(install, false, url);
                if (install.waitFor() != 0) {
                    log.error("[frontend] npm install failed; see the lines above.");
                    return;
                }
            }
            pidFile = dir.resolve("node_modules").resolve(PID_FILE);
            stopLeftover(pidFile);
            log.info("[frontend] building and watching {} ...", dir);
            watcher = command(dir, "run", "build:watch").start();
            Files.writeString(pidFile, Long.toString(watcher.pid()));
            pipe(watcher, true, url);
        } catch (IOException ex) {
            log.warn("[frontend] could not start the build ({}). Is Node.js installed? The API is still running at {}", ex.getMessage(), url);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static ProcessBuilder command(Path dir, String... npmArguments) {
        List<String> command = new ArrayList<>();
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            command.addAll(List.of("cmd.exe", "/c", "npm"));
        } else {
            command.add("npm");
        }
        command.addAll(List.of(npmArguments));
        return new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true);
    }

    /** Copies the build's output into this log, and announces the address once the first build is done. */
    private static void pipe(Process process, boolean announce, String url) throws IOException {
        boolean announced = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String clean = line.replaceAll("\\[[;\\d]*m", "").strip();
                if (clean.isEmpty()) {
                    continue;
                }
                log.info("[frontend] {}", clean);
                if (announce && !announced && clean.contains("built in")) {
                    announced = true;
                    log.info("==> Andaneri CRM is ready: {}", url);
                }
            }
        }
    }

    private static void stopLeftover(Path pidFile) {
        try {
            if (!Files.exists(pidFile)) {
                return;
            }
            long pid = Long.parseLong(Files.readString(pidFile).trim());
            ProcessHandle.of(pid).ifPresent(handle -> {
                handle.descendants().forEach(ProcessHandle::destroy);
                handle.destroy();
                log.info("[frontend] stopped the watcher left over from the last run (pid {})", pid);
            });
        } catch (IOException | NumberFormatException ex) {
            // An unreadable pid file just means there is nothing to stop.
        }
    }

    @PreDestroy
    public void stop() {
        Process process = watcher;
        if (process != null && process.isAlive()) {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
        }
        try {
            if (pidFile != null) {
                Files.deleteIfExists(pidFile);
            }
        } catch (IOException ex) {
            // Nothing to do: the next start checks whether that process still exists.
        }
    }
}
