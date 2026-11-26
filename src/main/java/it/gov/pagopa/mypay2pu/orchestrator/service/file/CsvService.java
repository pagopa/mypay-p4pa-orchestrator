package it.gov.pagopa.mypay2pu.orchestrator.service.file;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;

import static com.opencsv.enums.CSVReaderNullFieldIndicator.EMPTY_SEPARATORS;

@Slf4j
@Service
public class CsvService {
  private final char separator;
  private final char quoteChar;

  public CsvService(@Value("${csv.separator}") char separator,
                    @Value("${csv.quote-char}") char quoteChar) {
    this.separator = separator;
    this.quoteChar = quoteChar;
  }

  /**
   * Reads a CSV file and processes it using the provided row processor.
   *
   * <p>This method ensures that the file is properly closed after processing
   * by requiring a processing function that consumes the iterator.</p>
   *
   * <p>To optimize memory usage in batch processing, this method uses {@code CsvToBean.iterator()}
   * instead of {@code parse()} or {@code stream()}. This approach reads and processes one record at a time,
   * reducing memory consumption at the cost of lower performance.</p>
   *
   * @param <T>          The generic type of the DTO to which CSV rows will be mapped.
   * @param <R>          The type of the result returned by the processing function.
   * @param csvFilePath  The path to the CSV file to read.
   * @param typeClass    The class type to map each row of the CSV to.
   * @param rowProcessor A function that processes the iterator and returns a result.
   * @return The result produced by the row processor.
   * @throws IOException If an error occurs while reading the file.
   */
  // TODO: Connect the reject-file handlers and their type-specific CSV DTOs.
  public <T, R> R readCsv(Path csvFilePath,
                          Class<T> typeClass,
                          BiFunction<Iterator<T>, List<CsvException>, R> rowProcessor) throws IOException {

    try (PushbackReader reader = new PushbackReader(Files.newBufferedReader(csvFilePath, StandardCharsets.UTF_8))) {
      int first = reader.read();
      if (first != -1 && first != '\uFEFF') {
        reader.unread(first);
      }

      HeaderColumnNameMappingStrategy<T> strategy = new HeaderColumnNameMappingStrategy<>();
      strategy.setType(typeClass);
      CsvToBean<T> csvToBean = new CsvToBeanBuilder<T>(reader)
        .withType(typeClass)
        .withMappingStrategy(strategy)
        .withSeparator(separator)
        .withQuoteChar(quoteChar)
        .withIgnoreLeadingWhiteSpace(true)
        .withFieldAsNull(EMPTY_SEPARATORS)
        .withThrowExceptions(false)
        .build();

      Iterator<T> rows = csvToBean.iterator();
      R result = rowProcessor.apply(rows, Collections.unmodifiableList(csvToBean.getCapturedExceptions()));
      if (rows.hasNext()) {
        throw new IOException("CSV processor did not consume all rows");
      }
      List<CsvException> errors = csvToBean.getCapturedExceptions();
      if (!errors.isEmpty()) {
        throw new IOException("CSV contains invalid rows", errors.getFirst());
      }
      return result;
    }
  }
}
