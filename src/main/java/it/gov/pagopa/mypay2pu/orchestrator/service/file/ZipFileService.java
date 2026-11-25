package it.gov.pagopa.mypay2pu.orchestrator.service.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class ZipFileService {
  private final int maxEntries;
  private final long maxUncompressedSize;
  private final double maxCompressionRatio;

  public ZipFileService(
    @Value("${zip-file.max-entries}") int maxEntries,
    @Value("${zip-file.max-uncompressed-size}") long maxUncompressedSize,
    @Value("${zip-file.max-compression-ratio}") double maxCompressionRatio
  ) {
    if (maxEntries <= 0 || maxUncompressedSize <= 0 || !Double.isFinite(maxCompressionRatio)
      || maxCompressionRatio <= 0) {
      throw new IllegalArgumentException("ZIP limits must be positive and finite");
    }
    this.maxEntries = maxEntries;
    this.maxUncompressedSize = maxUncompressedSize;
    this.maxCompressionRatio = maxCompressionRatio;
  }

  /**
   * Extracts into a new directory. The caller owns that directory and its cleanup, including on failure.
   */
  // TODO: Integrate nested ZIP extraction and temporary-directory cleanup with the reject file handler.
  public List<Path> unzip(Path source, Path target) throws IOException {
    Path root = target.toAbsolutePath().normalize();
    Files.createDirectory(root);
    List<Path> extracted = new ArrayList<>();
    long totalSize = 0;
    int count = 0;

    try (ZipFile zip = new ZipFile(source.toFile())) {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (++count > maxEntries) {
          throw new IOException("ZIP exceeds the entry limit");
        }
        Path destination = resolveEntry(root, entry);
        if (entry.isDirectory()) {
          Files.createDirectories(destination);
          continue;
        }
        if (entry.getSize() < 0 || entry.getCompressedSize() < 0) {
          throw new IOException("ZIP entry has unknown size");
        }
        Files.createDirectories(destination.getParent());
        long entrySize = 0;
        CRC32 crc = new CRC32();
        try (InputStream input = zip.getInputStream(entry);
             OutputStream output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
          byte[] buffer = new byte[8192];
          int read;
          while ((read = input.read(buffer)) != -1) {
            if (read > maxUncompressedSize - totalSize) {
              throw new IOException("ZIP exceeds the uncompressed size limit");
            }
            totalSize += read;
            entrySize += read;
            if (entrySize > entry.getSize()
              || (entry.getCompressedSize() == 0 && entrySize > 0)
              || (entry.getCompressedSize() > 0
              && (double) entrySize / entry.getCompressedSize() > maxCompressionRatio)) {
              throw new IOException("ZIP entry exceeds its size or compression ratio limit");
            }
            crc.update(buffer, 0, read);
            output.write(buffer, 0, read);
          }
        }
        if (entrySize != entry.getSize() || crc.getValue() != entry.getCrc()) {
          throw new IOException("ZIP entry does not match its size or checksum");
        }
        extracted.add(destination);
      }
    }
    return List.copyOf(extracted);
  }

  private Path resolveEntry(Path root, ZipEntry entry) throws IOException {
    String name = entry.getName();
    if (name.isBlank() || name.contains("\\") || name.contains(":")) {
      throw new IOException("Invalid ZIP entry name");
    }
    try {
      Path path = Path.of(name);
      Path destination = root.resolve(path).normalize();
      if (path.isAbsolute() || !destination.startsWith(root) || destination.equals(root)) {
        throw new IOException("ZIP entry is outside the destination");
      }
      return destination;
    } catch (InvalidPathException exception) {
      throw new IOException("Invalid ZIP entry name", exception);
    }
  }
}
