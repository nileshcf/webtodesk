package com.example.conversion_service.service;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Mustache-based template engine for rendering Electron source files.
 * Templates are loaded from {@code classpath:/templates/electron/} and
 * cached after first compilation for performance.
 */
@Slf4j
@Service
public class TemplateEngine {

    private static final String TEMPLATE_ROOT = "templates/electron/";

    private final MustacheFactory factory = new DefaultMustacheFactory(TEMPLATE_ROOT);

    private final ConcurrentMap<String, Mustache> cache = new ConcurrentHashMap<>();

    /**
     * Renders a named template with the given context object (Map or POJO).
     *
     * @param templateName relative path within templates/electron/, e.g. "config.mustache"
     * @param context      data accessible by the template's {{}} expressions
     * @return rendered string
     */
    public String render(String templateName, Object context) {
        Mustache mustache = cache.computeIfAbsent(templateName, name -> {
            log.debug("Compiling Mustache template: {}", name);
            return factory.compile(name);
        });

        StringWriter writer = new StringWriter(2048);
        try {
            mustache.execute(writer, context).flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render template '" + templateName + "': " + e.getMessage(), e);
        }
        return writer.toString();
    }

    /** Invalidates the compiled template cache (useful in tests). */
    public void clearCache() {
        cache.clear();
        log.debug("Template cache cleared");
    }

    /** Returns the number of currently cached compiled templates. */
    public int getCacheSize() {
        return cache.size();
    }
}
