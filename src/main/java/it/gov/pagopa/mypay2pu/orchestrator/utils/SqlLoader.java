package it.gov.pagopa.mypay2pu.orchestrator.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class SqlLoader {
  private static final Path DB_DIRECTORY = Path.of("db");

  private final ConcurrentMap<String, String> sqlCache = new ConcurrentHashMap<>();

  public String load(String resourcePath) {
    String classpathPath = resolveResourcePath(resourcePath);
    return sqlCache.computeIfAbsent(classpathPath, this::readResource);
  }

  private String resolveResourcePath(String resourcePath) {
    if (resourcePath == null || resourcePath.isBlank()) {
      throw new IllegalArgumentException("SQL resource path must not be blank");
    }
    if (resourcePath.startsWith("/") || resourcePath.contains("\\") || resourcePath.matches("^[A-Za-z]:.*")) {
      throw new IllegalArgumentException("SQL resource path must be relative to the db resource directory");
    }

    Path resolvedPath = DB_DIRECTORY.resolve(resourcePath).normalize();
    if (!resolvedPath.startsWith(DB_DIRECTORY) || resolvedPath.equals(DB_DIRECTORY)) {
      throw new IllegalArgumentException("SQL resource path must stay inside the db resource directory");
    }
    return resolvedPath.toString().replace('\\', '/');
  }

  private String readResource(String classpathPath) {
    ClassPathResource resource = new ClassPathResource(classpathPath, getClass().getClassLoader());
    try (var inputStream = resource.getInputStream()) {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to load SQL resource: " + classpathPath, exception);
    }
  }
}
