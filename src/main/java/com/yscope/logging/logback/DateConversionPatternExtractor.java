package com.yscope.logging.logback;

import java.util.ArrayList;
import java.util.List;

/**
 * A class to extract all date conversion patterns from a Logback conversion
 * pattern. This is useful for Logback appenders that want to store a log event's
 * message separate from its timestamp.
 * <p>
 * For example, given the conversion pattern:
 * <pre>"%d%d %d{HH:mm:ss' on 'yy/MM/dd}%d %r %p %c{1}: %m%n"</pre>
 * This class would extract 5 date conversion patterns:
 * <ol>
 *   <li>"%d"</li>
 *   <li>"%d"</li>
 *   <li>"%d{HH:mm:ss' on 'yy/MM/dd}"</li>
 *   <li>"%d"</li>
 *   <li>"%r"</li>
 * </ol>
 *
 * And the conversion pattern without the dates would be:
 * <pre>"   %p %c{1}: %m%n"</pre>
 */
class DateConversionPatternExtractor {
  private enum STATES {
    LITERAL,
    PERCENT,
    DATE_SPECIFIER,
    DATE_SPECIFIER_OPEN_BRACE,
    DATE_SPECIFIER_CLOSE_BRACE,
    DATE_SPECIFIER_OPEN_QUOTE,
  }

  private final List<DateConversionPattern> dateConversionPatterns = new ArrayList<>();
  private final String conversionPatternWithoutDates;

  public DateConversionPatternExtractor (String conversionPattern) {
    // The parsing algorithm uses a state machine with the states and
    // transitions in the table below. States are named based on the character
    // that led to that state.
    //
    // | Current state              | Transition character  | Action                              | Next state                 |
    // |----------------------------|-----------------------|-------------------------------------|----------------------------|
    // | LITERAL                    | [^%]                  | -                                   | -                          |
    // | LITERAL                    | %                     | Start pattern                       | PERCENT                    |
    // | PERCENT                    | [^d]                  | if 'r' == c: Capture pattern        | LITERAL                    |
    // | PERCENT                    | d                     | -                                   | DATE_SPECIFIER             |
    // | DATE_SPECIFIER             | [^%{]                 | Capture pattern                     | LITERAL                    |
    // | DATE_SPECIFIER             | %                     | Capture pattern & start new pattern | PERCENT                    |
    // | DATE_SPECIFIER             | {                     | -                                   | DATE_SPECIFIER_OPEN_BRACE  |
    // | DATE_SPECIFIER_OPEN_BRACE  | [^'}]                 | -                                   | -                          |
    // | DATE_SPECIFIER_OPEN_BRACE  | '                     | -                                   | DATE_SPECIFIER_OPEN_QUOTE  |
    // | DATE_SPECIFIER_OPEN_BRACE  | }                     | -                                   | DATE_SPECIFIER_CLOSE_BRACE |
    // | DATE_SPECIFIER_CLOSE_BRACE | [^{%]                 | Capture pattern                     | LITERAL                    |
    // | DATE_SPECIFIER_CLOSE_BRACE | %                     | Capture pattern & start new pattern | PERCENT                    |
    // | DATE_SPECIFIER_OPEN_QUOTE  | [^']                  | -                                   | -                          |
    // | DATE_SPECIFIER_OPEN_QUOTE  | '                     | -                                   | DATE_SPECIFIER_OPEN_BRACE  |

    if (conversionPattern == null || conversionPattern.isEmpty()) {
      throw new IllegalArgumentException("Pattern " + conversionPattern
          + " is not valid.");
    }

    STATES currentState = STATES.LITERAL;

    int conversionPatternBeginOffset = 0;
    int conversionPatternEndOffset = 0;
    int formatBeginOffset = 0;
    String format = null;
    StringBuilder newPatternBuilder = new StringBuilder();
    for (int i = 0; i < conversionPattern.length(); ++i) {
      char c = conversionPattern.charAt(i);

      switch (currentState) {
        case LITERAL:
          if ('%' == c) {
            conversionPatternBeginOffset = i;
            currentState = STATES.PERCENT;
          }
          break;
        case PERCENT:
          if ('d' == c) {
            currentState = STATES.DATE_SPECIFIER;
          } else {
            if ('r' == c) {
              newPatternBuilder.append(conversionPattern, conversionPatternEndOffset,
                  conversionPatternBeginOffset);
              conversionPatternEndOffset = i + 1;
              dateConversionPatterns.add(
                  new DateConversionPattern('r', null));
            }
            currentState = STATES.LITERAL;
          }
          break;
        case DATE_SPECIFIER:
          if ('{' == c) {
            formatBeginOffset = i + 1;
            currentState = STATES.DATE_SPECIFIER_OPEN_BRACE;
          } else {
            // End of date conversion pattern
            newPatternBuilder.append(conversionPattern, conversionPatternEndOffset,
                conversionPatternBeginOffset);
            conversionPatternEndOffset = i;

            dateConversionPatterns.add(
                new DateConversionPattern('d', null));

            if ('%' == c) {
              conversionPatternBeginOffset = i;
              currentState = STATES.PERCENT;
            } else {
              currentState = STATES.LITERAL;
            }
          }
          break;
        case DATE_SPECIFIER_OPEN_BRACE:
          if ('\'' == c) {
            currentState = STATES.DATE_SPECIFIER_OPEN_QUOTE;
          } else if ('}' == c) {
            format = conversionPattern.substring(formatBeginOffset, i);
            currentState = STATES.DATE_SPECIFIER_CLOSE_BRACE;
          }
          break;
        case DATE_SPECIFIER_CLOSE_BRACE:
          // End of date conversion pattern
          newPatternBuilder.append(conversionPattern, conversionPatternEndOffset,
              conversionPatternBeginOffset);
          conversionPatternEndOffset = i;

          dateConversionPatterns.add(
              new DateConversionPattern('d', format));

          if ('%' == c) {
            conversionPatternBeginOffset = i;
            currentState = STATES.PERCENT;
          } else {
            currentState = STATES.LITERAL;
          }
          break;
        case DATE_SPECIFIER_OPEN_QUOTE:
          if ('\'' == c) {
            currentState = STATES.DATE_SPECIFIER_OPEN_BRACE;
          }
          break;
      }
    }
    // Handle the conversion pattern ending with an unprocessed date conversion
    // pattern. This could happen with "%d" or "%d{...}" because we can't know
    // they're complete unless we read the character following the date pattern
    // or the pattern ends.
    if (STATES.DATE_SPECIFIER == currentState || STATES.DATE_SPECIFIER_CLOSE_BRACE == currentState)
    {
      // End of date conversion pattern
      newPatternBuilder.append(conversionPattern, conversionPatternEndOffset,
          conversionPatternBeginOffset);
      conversionPatternEndOffset = conversionPattern.length();

      dateConversionPatterns.add(
          new DateConversionPattern('d', format));
    }

    // Append any remaining text after the last date conversion pattern
    if (conversionPatternEndOffset < conversionPattern.length()) {
      newPatternBuilder.append(conversionPattern, conversionPatternEndOffset,
          conversionPattern.length());
    }

    conversionPatternWithoutDates = newPatternBuilder.toString();
  }

  /**
   * @return All date conversion patterns extracted from the conversion pattern.
   */
  public List<DateConversionPattern> getDateConversionPatterns () {
    return dateConversionPatterns;
  }

  /**
   * @return The conversion pattern with all date conversion patterns extracted.
   */
  public String getConversionPatternWithoutDates () {
    return conversionPatternWithoutDates;
  }
}
