package ge.andaneri.crm.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the frontend from this application, so the whole CRM is one address and one port. Files come
 * from the built frontend on disk (see FrontendLocation) or from the jar's own copy. Any path that is not
 * a file and not under /api gets index.html, so a bookmarked /businesses/12 opens the right screen.
 * Until the first build has finished, a small page says so and refreshes itself.
 */
@Configuration
public class FrontendResources implements WebMvcConfigurer {

    private static final byte[] BUILDING = """
            <!doctype html><html lang="ka"><head><meta charset="utf-8"><meta http-equiv="refresh" content="2">
            <meta name="viewport" content="width=device-width,initial-scale=1"><title>Andaneri CRM</title></head>
            <body style="font-family:system-ui,sans-serif;display:grid;place-items:center;height:100vh;margin:0;background:#f8f6fa;color:#1c1523">
            <div style="text-align:center"><div style="font-size:22px;font-weight:600">Andaneri CRM</div>
            <p>ფრონტენდი იქმნება... / The frontend is being built...</p><p style="color:#6b6475;font-size:13px">
            This page refreshes by itself. First start takes up to a minute (npm install).</p></div></body></html>
            """.getBytes(StandardCharsets.UTF_8);

    private final FrontendLocation location;

    public FrontendResources(FrontendLocation location) {
        this.location = location;
    }

    /**
     * The bare address "/" never reaches the resource handler below (Spring treats an empty resource path as
     * no resource at all and answers 404), so it is forwarded to index.html explicitly.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        List<String> locations = new ArrayList<>();
        location.dist().map(Path::toUri).map(Object::toString).ifPresent(locations::add);
        locations.add("classpath:/static/");
        String[] roots = locations.toArray(String[]::new);

        // Built scripts and styles have content hashes in their names: safe to cache for a year.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(locations.stream().map(root -> root + "assets/").toArray(String[]::new))
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        registry.addResourceHandler("/**")
                .addResourceLocations(roots)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new SinglePageResolver());
    }

    private static final class SinglePageResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource root) throws IOException {
            if (resourcePath.startsWith("api/") || resourcePath.equals("api")) {
                return null;
            }
            Resource requested = root.createRelative(resourcePath);
            if (!resourcePath.isEmpty() && requested.exists() && requested.isReadable() && !resourcePath.endsWith("/")) {
                return requested;
            }
            // A missing file with an extension (an old script name) is a real 404, not a screen. index.html itself
            // is the exception: "/" is forwarded to it, and while it is missing the "being built" page answers.
            String last = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            if (last.contains(".") && !resourcePath.equals("index.html")) {
                return null;
            }
            Resource index = root.createRelative("index.html");
            if (index.exists() && index.isReadable()) {
                return index;
            }
            // No index.html yet, or none for a moment: vite's watch mode empties dist at the start of every
            // rebuild. The "being built" page refreshes itself until the new build is there.
            return new BuildingPage();
        }
    }

    private static final class BuildingPage extends ByteArrayResource {

        BuildingPage() {
            super(BUILDING);
        }

        @Override
        public String getFilename() {
            return "building.html";
        }

        @Override
        public long lastModified() {
            return System.currentTimeMillis();
        }
    }
}
