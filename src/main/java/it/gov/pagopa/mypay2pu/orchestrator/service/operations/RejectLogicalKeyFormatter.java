package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RejectLogicalKeyFormatter {

  // TODO: use the per-type reject handlers to supply keys from all discard files of the same IPA.
  public String formatValues(Collection<String> values) {
    if (ObjectUtils.isEmpty(values)) {
      throw invalidKey("Reject logical keys must not be empty");
    }

    Set<String> unique = new LinkedHashSet<>();
    for (String value : values) {
      unique.add(validateValue(value));
    }
    return String.join(",", unique);
  }

  public String formatPairs(Collection<KeyPair> pairs) {
    if (ObjectUtils.isEmpty(pairs)) {
      throw invalidKey("Reject logical key pairs must not be empty");
    }

    Set<KeyPair> unique = new LinkedHashSet<>();
    for (KeyPair pair : pairs) {
      if (pair == null) {
        throw invalidKey("Reject logical key pair must not be null");
      }
      unique.add(new KeyPair(validateValue(pair.left()), validateValue(pair.right())));
    }
    String left = unique.stream().map(KeyPair::left).collect(Collectors.joining(","));
    String right = unique.stream().map(KeyPair::right).collect(Collectors.joining(","));
    return left + "|" + right;
  }

  private String validateValue(String value) {
    if (!StringUtils.hasText(value) || value.contains(",") || value.contains("|")) {
      throw invalidKey("Reject logical key must be non-empty and cannot contain separators");
    }
    return value.trim();
  }

  private InvalidValueException invalidKey(String message) {
    return new InvalidValueException("INVALID_REJECT_LOGICAL_KEY", message);
  }

  public record KeyPair(String left, String right) {}
}
