package it.gov.pagopa.mypay2pu.orchestrator.service.file;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvMalformedLineException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvServiceTest {
  @TempDir
  Path temporaryDirectory;

  private final CsvService service = new CsvService(';', '"');

  @Test
  void readsInheritedFieldsByHeaderWithBomAndQuotedValues() throws IOException {
    Path csv = writeCsv("""
      \uFEFFCodice;Descrizione;Ignorato;Numero Riga
      A1;"Città; centro";extra;3
    """);

    List<ErrorRow> rows = service.readCsv(csv, ErrorRow.class, (iterator, errors) -> {
      List<ErrorRow> result = new ArrayList<>();
      iterator.forEachRemaining(result::add);
      return result;
    });

    assertEquals(1, rows.size());
    assertEquals(3, rows.getFirst().rowNumber);
    assertEquals("A1", rows.getFirst().code);
    assertEquals("Città; centro", rows.getFirst().description);
  }

  @Test
  void rejectsMissingRequiredHeader() throws IOException {
    Path csv = writeCsv("Codice;Descrizione\nA1;test\n");

    RuntimeException exception = assertThrows(RuntimeException.class,
      () -> service.readCsv(csv, ErrorRow.class, (iterator, errors) -> {
        iterator.forEachRemaining(row -> {});
        return null;
      }));
    assertInstanceOf(CsvRequiredFieldEmptyException.class, exception.getCause());
  }

  @Test
  void rejectsInvalidFieldInsteadOfSkippingRow() throws IOException {
    Path csv = writeCsv("Numero Riga;Codice\n1;OK\nnot-a-number;A1\n2;B2\n");

    IOException exception = assertThrows(IOException.class,
      () -> service.readCsv(csv, ErrorRow.class, (iterator, errors) -> {
        iterator.forEachRemaining(row -> {});
        return null;
      }));
    assertInstanceOf(CsvDataTypeMismatchException.class, exception.getCause());
  }

  @Test
  void rejectsMalformedQuotedInput() throws IOException {
    Path csv = writeCsv("Numero Riga;Codice\n1;\"unfinished\n");

    RuntimeException exception = assertThrows(RuntimeException.class,
      () -> service.readCsv(csv, ErrorRow.class, (iterator, errors) -> {
        iterator.forEachRemaining(row -> {});
        return null;
      }));
    assertInstanceOf(CsvMalformedLineException.class, exception.getCause());
  }

  @Test
  void rejectsProcessorThatLeavesRowsUnread() throws IOException {
    Path csv = writeCsv("Numero Riga;Codice\n1;A1\n2;B2\n");

    assertThrows(IOException.class,
      () -> service.readCsv(csv, ErrorRow.class, (iterator, errors) -> iterator.next()));
  }

  @Test
  void readsWithConfiguredSeparator() throws IOException {
    Path csv = writeCsv("Numero Riga,Codice\n4,A1\n");

    ErrorRow row = new CsvService(',', '"').readCsv(csv, ErrorRow.class, (iterator, errors) -> iterator.next());

    assertEquals(4, row.rowNumber);
    assertEquals("A1", row.code);
  }

  private Path writeCsv(String content) throws IOException {
    Path csv = temporaryDirectory.resolve("input.csv");
    Files.writeString(csv, content, StandardCharsets.UTF_8);
    return csv;
  }

  public abstract static class BaseErrorRow {
    @CsvBindByName(column = "Numero Riga", required = true)
    public Integer rowNumber;
  }

  public static class ErrorRow extends BaseErrorRow {
    @CsvBindByName(column = "Codice", required = true)
    public String code;
    @CsvBindByName(column = "Descrizione")
    public String description;
  }
}
