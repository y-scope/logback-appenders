package com.yscope.logging.logback;

import java.util.Objects;

/**
 * A simple class to contain the components of a Logback Layout's date conversion
 * pattern.
 */
public class DateConversionPattern {
  public char specifier;
  public String format;

  public DateConversionPattern (char specifier, String format)
  {
    this.specifier = specifier;
    this.format = format;
  }

  @Override
  public boolean equals (Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DateConversionPattern rhs = (DateConversionPattern)o;
    return specifier == rhs.specifier && Objects.equals(format, rhs.format);
  }

  @Override
  public int hashCode () {
    return Objects.hash(specifier, format);
  }

  @Override
  public String toString () {
    StringBuilder builder = new StringBuilder();
    builder.append("%");
    builder.append(specifier);
    if (null != format) {
      builder.append('{');
      builder.append(format);
      builder.append('}');
    }
    return builder.toString();
  }
}
