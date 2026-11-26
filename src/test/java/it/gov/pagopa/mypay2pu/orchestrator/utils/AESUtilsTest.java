package it.gov.pagopa.mypay2pu.orchestrator.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

class AESUtilsTest {

  @Test
  void test() {
    // Given
    String plain = "PLAINTEXT";
    String psw = "PSW";

    // When
    byte[] cipher = AESUtils.encrypt(psw, plain);
    String result = AESUtils.decrypt(psw, cipher);

    // Then
    Assertions.assertEquals(plain, result);
  }

  @Test
  void testStream() throws IOException {
    // Given
    String plain = "PLAINTEXT";
    String psw = "PSW";

    // When
    InputStream cipherStream = AESUtils.encrypt(psw, new ByteArrayInputStream(plain.getBytes(StandardCharsets.UTF_8)));
    InputStream resultStream = AESUtils.decrypt(psw, cipherStream);

    // Then
    Assertions.assertEquals(plain, new String(resultStream.readAllBytes(), StandardCharsets.UTF_8));
  }

  @Test
  void testFile() throws IOException {
    // Given
    String plain = "PLAINTEXT";
    Path plainFile = Path.of("build", "tmp", "plainFile.txt");
    Files.writeString(plainFile, plain);
    String psw = "PSW";
    Path decryptedFile = plainFile.getParent().resolve("decryptedFile.txt");

    Files.deleteIfExists(decryptedFile);
    Files.deleteIfExists(plainFile.getParent().resolve(plainFile.getFileName() + ".cipher"));

    // When
    File cipherFile = null;
    try {
      cipherFile = AESUtils.encrypt(psw, plainFile.toFile());
      AESUtils.decrypt(psw, cipherFile, decryptedFile.toFile());

      // Then
      Assertions.assertEquals(Files.readAllLines(decryptedFile), List.of(plain));
    } finally {
      if (cipherFile != null && cipherFile.exists()) {
        cipherFile.delete();
      }
      Files.deleteIfExists(decryptedFile);
    }
  }

  @Test
  void testFileThroughInputStream() throws IOException, NoSuchAlgorithmException {
    // Given
    String plain = "PLAINTEXT";
    String psw = "PSW";

    Path targetPath = Path.of("build", "tmp");
    String fileName = "cipherFile2.txt";
    Path expectedResultedFile = targetPath.resolve(fileName + AESUtils.CIPHER_EXTENSION);

    Files.deleteIfExists(expectedResultedFile);

    // When
    AESUtils.encryptAndSave(psw, new ByteArrayInputStream(plain.getBytes(StandardCharsets.UTF_8)), targetPath, fileName);
    try (InputStream decrypted = AESUtils.decrypt(psw, targetPath, fileName)) {
      // Then
      Assertions.assertEquals(plain, new String(decrypted.readAllBytes(), StandardCharsets.UTF_8));
    } finally {
      Files.deleteIfExists(expectedResultedFile);
    }
  }

  @Test
  void testFileThroughInputStreamUsingAlreadyExtendedFileName() throws IOException {
    // Given
    String plain = "PLAINTEXT";
    String psw = "PSW";

    Path targetPath = Path.of("build", "tmp");
    String fileName = "cipherFile2.txt";
    Path expectedResultedFile = targetPath.resolve(fileName + AESUtils.CIPHER_EXTENSION);

    Files.deleteIfExists(expectedResultedFile);

    // When
    try (ByteArrayInputStream plainStream = new ByteArrayInputStream(plain.getBytes(StandardCharsets.UTF_8))) {
      AESUtils.encryptAndSave(psw, plainStream, targetPath, fileName);
      try (InputStream decrypted = AESUtils.decrypt(psw, expectedResultedFile.toFile())) {
        // Then
        Assertions.assertEquals(plain, new String(decrypted.readAllBytes(), StandardCharsets.UTF_8));
      }
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    } finally {
      Files.deleteIfExists(expectedResultedFile);
    }
  }

  @Test
  void testDigestThroughEncryptAndSave() throws IOException, NoSuchAlgorithmException {
    // Act
    String plain = "PLAINTEXT";
    String psw = "PSW";
    Path targetPath = Path.of("build", "tmp");
    String fileName = "cipherFile2.txt";

    Path encryptedFile = targetPath.resolve(fileName + AESUtils.CIPHER_EXTENSION);
    Files.deleteIfExists(encryptedFile);

    byte[] resultDigest = AESUtils.encryptAndSave(
      psw,
      new ByteArrayInputStream(plain.getBytes(StandardCharsets.UTF_8)),
      targetPath,
      fileName
    );

    MessageDigest expectedDigest = MessageDigest.getInstance("SHA-256");
    byte[] expectedHash = expectedDigest.digest(plain.getBytes());

    Assertions.assertTrue(Files.exists(encryptedFile));
    Assertions.assertTrue(Files.size(encryptedFile) > 0);
    Assertions.assertEquals(32, resultDigest.length);
    Assertions.assertArrayEquals(expectedHash, resultDigest);
  }
}
