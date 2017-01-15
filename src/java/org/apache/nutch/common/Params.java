package org.apache.nutch.common;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by vincent on 16-9-24.
 */
public class Params {
  private List<Pair<String, Object>> paramsList = new LinkedList<>();
  private String captionFormat = String.format("%20sParams Table%-25s\n", "----------", "----------");
  private String headerFormat = String.format("%25s   %-25s\n", "Name", "Value");
  private String rowFormat = "%25s : %s";
  private String kvDelimiter = " : ";
  private Logger LOG = LoggerFactory.getLogger(Params.class);
  private Logger tmpLOG = null;

  public Params() {
  }

  public Params(String key, Object value, Object... others) {
    this.paramsList.addAll(toArgList(key, value, others));
  }

  public Params(Map<String, Object> args) {
    args.entrySet().forEach(e -> this.paramsList.add(Pair.of(e.getKey(), e.getValue())));
  }

  public Object get(String name) {
    Pair<String, Object> entry = CollectionUtils.find(paramsList, e -> e.getKey().equals(name));
    return entry == null ? null : entry.getValue();
  }

  public String get(String name, String defaultValue) {
    String value = (String)get(name);
    return value == null ? defaultValue : value;
  }

  public String getString(String name) {
    return (String)get(name);
  }

  public <T extends Enum<T>> T getEnum(String name, T defaultValue) {
    String val = (String)this.get(name);
    return null == val ? defaultValue : Enum.valueOf(defaultValue.getDeclaringClass(), val);
  }

  public Integer getInt(String name, Integer defaultValue) {
    Integer value = (Integer) get(name);
    return value == null ? defaultValue : value;
  }

  public Long getLong(String name, Long defaultValue) {
    Long value = (Long) get(name);
    return value == null ? defaultValue : value;
  }

  public Boolean getBoolean(String name, Boolean defaultValue) {
    Boolean value = (Boolean) get(name);
    return value == null ? defaultValue : value;
  }

  public String[] getStrings(String name, String[] defaultValue) {
    String valueString = get(name, null);
    if (valueString == null) {
      return defaultValue;
    }
    return org.apache.hadoop.util.StringUtils.getStrings(valueString);
  }

  public Path getPath(String name) throws IOException {
    String value = getString(name);
    if (value == null) return null;

    Path path = Paths.get(value);
    Files.createDirectories(path.getParent());

    return path;
  }

  public Path getPath(String name, Path defaultValue) throws IOException {
    String value = getString(name);
    Path path = value == null ? Paths.get(value) : defaultValue;
    Files.createDirectories(path.getParent());
    return path;
  }

  public Instant getInstant(String name, Instant defaultValue) {
    Instant value = (Instant) get(name);
    return value == null ? defaultValue : value;
  }

  public Duration getDuration(String name, Duration defaultValue) {
    Duration value = (Duration) get(name);
    return value == null ? defaultValue : value;
  }

  public String format() {
    return format(paramsList);
  }

  public String formatAsLine() {
    return formatAsLine(paramsList);
  }

  public Params withCaptionFormat(String captionFormat) {
    this.captionFormat = captionFormat;
    return this;
  }

  public Params withHeaderFormat(String headerFormat) {
    this.headerFormat = headerFormat;
    return this;
  }

  public Params withRowFormat(String rowFormat) {
    this.rowFormat = rowFormat;
    return this;
  }

  public Params withKVDelimiter(String kvDelimiter) {
    this.kvDelimiter = kvDelimiter;
    return this;
  }

  public Params sorted() {
    this.paramsList = this.paramsList.stream()
        .sorted((p1, p2) -> p1.getKey().compareTo(p2.getKey()))
        .collect(Collectors.toList());
    return this;
  }

  public Params distinct() {
    this.paramsList = this.paramsList.stream()
        .distinct()
        .collect(Collectors.toList());
    return this;
  }

  public Params merge(Params... others) {
    if (others != null && others.length > 0) {
      Arrays.stream(others).forEach(params -> this.paramsList.addAll(params.getParamsList()));
    }
    return this;
  }

  public List<Pair<String, Object>> getParamsList() {
    return paramsList;
  }

  public Map<String, Object> asMap() {
    Map<String, Object> result = new HashMap<>();
    paramsList.forEach(p -> result.put(p.getKey(), p.getValue()));
    return result;
  }

  public Params withLogger(Logger logger) {
    this.tmpLOG = logger;
    return this;
  }

  public void debug() {
    debug(false);
  }

  public void debug(boolean inline) {
    if (tmpLOG != null) {
      tmpLOG.debug(inline ? formatAsLine() : format());
    }
    else {
      LOG.debug(inline ? formatAsLine() : format());
    }
  }

  public void info() {
    info(false);
  }

  public void info(boolean inline) {
    if (tmpLOG != null) {
      tmpLOG.info(inline ? formatAsLine() : format());
    }
    else {
      LOG.info(inline ? formatAsLine() : format());
    }
  }

  @Override
  public String toString() {
    return format();
  }

  public static Params of(String key, Object value, Object... others) {
    return new Params(key, value, others);
  }

  public static Params of(Map<String, Object> args) {
    return new Params(args);
  }

  public static List<Pair<String, Object>> toArgList(String key, Object value, Object... others) {
    List<Pair<String, Object>> results = new LinkedList<>();

    results.add(Pair.of(key, value));

    if (others == null || others.length < 2) {
      return results;
    }

    if (others.length % 2 != 0) {
      throw new RuntimeException("expected name/value pairs");
    }

    for (int i = 0; i < others.length; i += 2) {
      Object k = others[i];
      Object v = others[i + 1];

      if (k != null && v != null) {
        results.add(Pair.of(String.valueOf(others[i]), others[i + 1]));
      }
    }

    return results;
  }

  /**
   * Convert K/V pairs array into a map.
   *
   * @param others A K/V pairs array, the length of the array must be a even number
   *                null key or null value pair is ignored
   * @return A map contains all non-null key/values
   * */
  public static Map<String, Object> toArgMap(String key, Object value, Object... others) {
    Map<String, Object> results = new LinkedHashMap<>();

    results.put(key, value);

    if (others == null || others.length < 2) {
      return results;
    }

    if (others.length % 2 != 0) {
      throw new RuntimeException("expected name/value pairs");
    }

    for (int i = 0; i < others.length; i += 2) {
      Object k = others[i];
      Object v = others[i + 1];

      if (k != null && v != null) {
        results.put(String.valueOf(others[i]), others[i + 1]);
      }
    }

    return results;
  }

  public static String formatAsLine(String key, Object value, Object... others) {
    return Params.of(key, value, others).formatAsLine();
  }

  public static String format(String key, Object value, Object... others) {
    return Params.of(key, value, others).format();
  }

  private String format(List<Pair<String, Object>> params) {
    if (params.isEmpty()) {
      return "";
    }

    StringBuilder sb = new StringBuilder();

    sb.append('\n');
    sb.append(captionFormat);
    sb.append(headerFormat);
    int i = 0;
    for (Pair<String, Object> param : params) {
      if (i++ > 0) {
        sb.append("\n");
      }

      sb.append(String.format(rowFormat, param.getKey(), param.getValue()));
    }

    sb.append('\n');

    return sb.toString();
  }

  private String formatAsLine(List<Pair<String, Object>> params) {
    if (params.isEmpty()) {
      return "";
    }

    StringBuilder sb = new StringBuilder();

    int i = 0;
    for (Pair<String, Object> arg : params) {
      if (i++ > 0) {
        sb.append(' ');
      }

      sb.append(arg.getKey());
      sb.append(kvDelimiter);
      sb.append(arg.getValue());
    }

    return sb.toString();
  }
}
