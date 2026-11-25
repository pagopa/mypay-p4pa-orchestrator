package it.gov.pagopa.mypay2pu.orchestrator.service.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZipFileServiceTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void extractsFilesIntoDedicatedDirectory() throws IOException {
    Path zip = archive(Map.of("nested/data.csv", "a;b\n1;2"));
    Path target = temporaryDirectory.resolve("output");

    var files = new ZipFileService(10, 1024, 100).unzip(zip, target);

    assertEquals(1, files.size());
    assertEquals("a;b\n1;2", Files.readString(target.resolve("nested/data.csv")));
  }

  @Test
  void rejectsTraversalWithoutWritingOutsideTarget() throws IOException {
    Path zip = archive(Map.of("../outside.csv", "unsafe"));
    Path target = temporaryDirectory.resolve("output");

    assertThrows(IOException.class, () -> new ZipFileService(10, 1024, 100).unzip(zip, target));
    assertFalse(Files.exists(temporaryDirectory.resolve("outside.csv")));
  }

  @Test
  void rejectsExcessiveUncompressedSize() throws IOException {
    Path zip = archive(Map.of("data.csv", "123456789"));

    assertThrows(IOException.class,
      () -> new ZipFileService(10, 8, 100).unzip(zip, temporaryDirectory.resolve("output")));
  }

  @Test
  void rejectsTooManyEntries() throws IOException {
    Path zip = archive(Map.of("first.csv", "1", "second.csv", "2"));

    assertThrows(IOException.class,
      () -> new ZipFileService(1, 1024, 100).unzip(zip, temporaryDirectory.resolve("output")));
  }

  @Test
  void rejectsExistingDestination() throws IOException {
    Path zip = archive(Map.of("data.csv", "1"));
    Path target = Files.createDirectory(temporaryDirectory.resolve("output"));

    assertThrows(IOException.class, () -> new ZipFileService(10, 1024, 100).unzip(zip, target));
  }

  @Test
  void rejectsExcessiveCompressionRatio() throws IOException {
    Path zip = archive(Map.of("data.csv", "a".repeat(10_000)));

    assertThrows(IOException.class,
      () -> new ZipFileService(10, 20_000, 2).unzip(zip, temporaryDirectory.resolve("output")));
  }

  @Test
  void rejectsCorruptedStoredEntry() throws IOException {
    byte[] content = "original-content".getBytes(StandardCharsets.UTF_8);
    CRC32 crc = new CRC32();
    crc.update(content);
    Path zip = temporaryDirectory.resolve("source.zip");
    try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
      ZipEntry entry = new ZipEntry("data.csv");
      entry.setMethod(ZipEntry.STORED);
      entry.setSize(content.length);
      entry.setCompressedSize(content.length);
      entry.setCrc(crc.getValue());
      output.putNextEntry(entry);
      output.write(content);
      output.closeEntry();
    }
    byte[] bytes = Files.readAllBytes(zip);
    int dataOffset = 30 + "data.csv".length();
    bytes[dataOffset] ^= 1;
    Files.write(zip, bytes);

    assertThrows(IOException.class,
      () -> new ZipFileService(10, 1024, 100).unzip(zip, temporaryDirectory.resolve("output")));
  }

  private Path archive(Map<String, String> entries) throws IOException {
    Path zip = temporaryDirectory.resolve("source.zip");
    try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
      for (var entry : entries.entrySet()) {
        output.putNextEntry(new ZipEntry(entry.getKey()));
        output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
      }
    }
    return zip;
  }
}
