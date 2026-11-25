package it.gov.pagopa.mypay2pu.orchestrator.service.file;

import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidFileException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service class for performing common file operations, including
 * validation, validate ZIP entry names and extraction of ZIP files.
 * <p>
 * This class provides secure methods to handle files, ensuring safety
 * against potential vulnerabilities, such as ZIP Slip attacks.
 * </p>
 */
@Slf4j
@Service("zipFileUtilitiesService")
public class ZipFileService {
  /** Maximum number of entries to extract from the ZIP file */
  private final int maxEntries;
  /** Maximum total size (in bytes) of all uncompressed data, for example 50MB */
  private final long maxUncompressedSize;
  /** Maximum allowed compression ratio (compressed size / uncompressed size), for example 10% */
  private final double maxCompressionRatio;

  public ZipFileService(@Value("${zip-file.max-entries}") int maxEntries,
                        @Value("${zip-file.max-uncompressed-size}") long maxUncompressedSize,
                        @Value("${zip-file.max-compression-ratio}") double maxCompressionRatio) {
    this.maxEntries = maxEntries;
    this.maxUncompressedSize = maxUncompressedSize;
    this.maxCompressionRatio = maxCompressionRatio;
  }

  /**
   * Extracts the contents of a ZIP file to a specified target directory and returns
   * a list of paths to the extracted files.
   * <BR />
   * This method performs secure extraction by validating:
   * <ul>
   *   <li>The number of entries in the ZIP file does not exceed a predefined threshold.</li>
   *   <li>The total uncompressed size of all extracted data does not exceed a maximum allowed size.</li>
   *   <li>The compression ratio of each entry is within acceptable limits to mitigate ZIP bomb attacks.</li>
   *   <li>Paths are validated to prevent ZIP Slip attacks.</li>
   * </ul>
   *
   * If any validation fails during extraction, the method will throw an exception
   * and terminate the process.
   *
   * @param source the path to the ZIP file to be extracted
   * @param target the target directory where the contents will be extracted
   * @return a list of {@link Path} objects representing the extracted files
   * @throws InvalidFileException if:
   *         <ul>
   *           <li>The ZIP file contains an invalid entry name (ZIP Slip vulnerability).</li>
   *           <li>The ZIP file exceeds the maximum allowed uncompressed size.</li>
   *           <li>The ZIP file contains an excessive number of entries.</li>
   *           <li>An entry in the ZIP file has a suspiciously high compression ratio.</li>
   *           <li>An I/O error occurs during extraction.</li>
   *         </ul>
   */
  public List<Path> unzip(Path source, Path target) {
    List<Path> extractedPaths = new ArrayList<>();

    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(source.toFile()))) {
      long totalUncompressedSize = 0;
      int entryCount = 0;

      ZipEntry zipEntry;
      while ((zipEntry = zis.getNextEntry()) != null) {
        if(isMacosxFolder(zipEntry)){
          continue;
        }
        validateEntryCount(++entryCount);
        Path targetPath = validateAndPrepareTargetPath(zipEntry, target);

        if(zipEntry.isDirectory()){
          Files.createDirectories(targetPath);
        } else {
          long totalSizeEntry = 0;
          byte[] buffer = new byte[2048];

          Files.createDirectories(targetPath.getParent());

          try (OutputStream out = Files.newOutputStream(targetPath, StandardOpenOption.CREATE)) {
            int bytesRead;
            while ((bytesRead = zis.read(buffer)) > 0) {
              totalSizeEntry += bytesRead;
              totalUncompressedSize += bytesRead;

              validateUncompressedSize(totalUncompressedSize);
              validateCompressionRatio(zipEntry, totalSizeEntry);

              out.write(buffer, 0, bytesRead);
            }
          }
          extractedPaths.add(targetPath);
        }
        zis.closeEntry();
      }
    } catch (IOException e) {
      throw new InvalidFileException("Error while unzipping file: " + source, e);
    }
    return extractedPaths;
  }

  private boolean isMacosxFolder(ZipEntry zipEntry) {
    return zipEntry.getName().startsWith("__MACOSX/");
  }

  /**
   * Extracts the contents of a ZIP file to its own directory and returns a list of paths
   * to the extracted files.
   * <BR />
   * This method uses the directory of the provided ZIP file as the default extraction location.
   * The contents of the ZIP file will be extracted into a subdirectory named after the ZIP file
   * (without its extension) within the same directory.
   * <BR />
   * Internally, this method delegates the extraction process to {@link #unzip(Path, Path)}.
   *
   * @param path the path to the ZIP file to be extracted
   * @return a list of {@link Path} objects representing the extracted files
   * @throws InvalidFileException if:
   *         <ul>
   *           <li>The ZIP file contains an invalid entry name (ZIP Slip vulnerability).</li>
   *           <li>The ZIP file exceeds the maximum allowed uncompressed size.</li>
   *           <li>The ZIP file contains an excessive number of entries.</li>
   *           <li>An entry in the ZIP file has a suspiciously high compression ratio.</li>
   *           <li>An I/O error occurs during extraction.</li>
   *         </ul>
   */
  public List<Path> unzip(Path path) { return unzip(path, path.getParent()); }

  /**
   * Validates the entry count against the maximum allowed entries.
   *
   * @param entryCount the current entry count.
   * @throws InvalidFileException if the entry count exceeds the maximum allowed.
   */
  private void validateEntryCount(int entryCount) {
    if (entryCount > maxEntries) {
      throw new InvalidFileException("ZIP file exceeds the maximum number of entries");
    }
  }

  /**
   * Validates and prepares the target path for the current ZIP entry.
   *
   * @param zipEntry the ZIP entry to process.
   * @param target the target directory for extraction.
   * @return the validated and normalized target path.
   * @throws InvalidFileException if the ZIP entry is invalid.
   */
  private Path validateAndPrepareTargetPath(ZipEntry zipEntry, Path target) throws IOException {
    checkFileName(zipEntry);
    Path targetPath = zipSlipProtect(zipEntry, target);
    Files.createDirectories(targetPath.getParent());
    return targetPath;
  }

  /**
   * Validates the total uncompressed size against the maximum allowed size.
   *
   * @param totalUncompressedSize the current total uncompressed size.
   * @throws InvalidFileException if the uncompressed size exceeds the maximum allowed.
   */
  private void validateUncompressedSize(long totalUncompressedSize) {
    if (totalUncompressedSize > maxUncompressedSize) {
      throw new InvalidFileException("ZIP file exceeds the maximum allowed uncompressed size");
    }
  }

  /**
   * Validates the compression ratio of the current ZIP entry.
   *
   * @param zipEntry the ZIP entry to validate.
   * @param totalSizeEntry the current uncompressed size of the entry.
   * @throws InvalidFileException if the compression ratio exceeds the maximum allowed.
   */
  private void validateCompressionRatio(ZipEntry zipEntry, long totalSizeEntry) {
    if (zipEntry.getCompressedSize() > 0) {
      double compressionRatio = (double) totalSizeEntry / zipEntry.getCompressedSize();
      if (compressionRatio > maxCompressionRatio) {
        throw new InvalidFileException("ZIP file contains an entry with excessive compression ratio");
      }
    }
  }

  /**
   * Protects against ZIP Slip vulnerability by validating the path of an extracted entry.
   *
   * @param zipEntry the ZIP entry to validate.
   * @param targetDir the target directory for extraction.
   * @return a safe, normalized path towards zipEntry within the target directory.
   * @throws InvalidFileException if the ZIP entry is outside the target directory.
   */
  private Path zipSlipProtect(ZipEntry zipEntry, Path targetDir) {
    String checkedFilename = checkFileName(zipEntry.getName());
    Path targetDirResolved = targetDir.resolve(checkedFilename);
    Path normalizePath = targetDirResolved.normalize();
    if (!normalizePath.startsWith(targetDir)) {
      throw new InvalidFileException("Bad zip entry: " + zipEntry.getName());
    }
    return normalizePath;
  }

  /**
   * Validates the safety of a file name within a ZIP archive.
   * <BR />
   * The file name is considered safe if it:
   * <ul>
   *   <li>Starts with an alphanumeric character.</li>
   *   <li>Does not contain the string ".." (prevents directory traversal).</li>
   * </ul>
   *
   * @param fileName the name of the ZIP entry to validate.
   * @return the file name if it is deemed safe.
   * @throws InvalidFileException if the file name is deemed unsafe.
   */
  private String checkFileName(String fileName) {
    if (!Character.isLetterOrDigit(fileName.charAt(0)) || fileName.contains("..")) {
      throw new InvalidFileException("Potential Zip Slip exploit detected: " + fileName);
    }
    return fileName;
  }

  /**
   * Validates the safety of a {@link ZipEntry}'s file name within a ZIP archive.
   * <p>
   * This method extracts the file name from the {@code ZipEntry} and delegates
   * the validation to {@link #checkFileName(String)}.
   * </p>
   *
   * @param entry the ZIP entry to validate.
   * @return the original {@code ZipEntry} if the file name is deemed safe.
   */
  private ZipEntry checkFileName(ZipEntry entry) {
    checkFileName(entry.getName());
    return entry;
  }
}
