package com.yscope.logging.logback;

import java.util.ArrayList;
import java.util.List;

import com.yscope.logging.logback.DateConversionPattern;
import com.yscope.logging.logback.DateConversionPatternExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DateConversionPatternExtractorTest {
  @Test
  public void testExtraction () {
    DateConversionPattern datePattern;

    // Validate extracting %d at different positions in the conversion pattern
    datePattern = new DateConversionPattern('d', null);
    testExtractingASingleSimpleDatePattern(
        "%d %p %c{1} %% %m%n",
        " %p %c{1} %% %m%n",
        datePattern
    );
    testExtractingASingleSimpleDatePattern(
        "%p %d %c{1} %% %m%n",
        "%p  %c{1} %% %m%n",
        datePattern
    );
    testExtractingASingleSimpleDatePattern(
        "%p %c{1} %% %m%n %d",
        "%p %c{1} %% %m%n ",
        datePattern
    );

    // Validate extracting %d{...} at different positions in the conversion
    // pattern
    datePattern = new DateConversionPattern('d', "HH:mm:ss' on 'yy/MM/dd");
    testExtractingASingleSimpleDatePattern(
        "%d{HH:mm:ss' on 'yy/MM/dd} %p %c{1} %% %m%n",
        " %p %c{1} %% %m%n",
        datePattern
    );
    testExtractingASingleSimpleDatePattern(
        "%p %d{HH:mm:ss' on 'yy/MM/dd} %c{1} %% %m%n",
        "%p  %c{1} %% %m%n",
        datePattern
    );
    testExtractingASingleSimpleDatePattern(
        "%p %c{1} %% %m%n %d{HH:mm:ss' on 'yy/MM/dd}",
        "%p %c{1} %% %m%n ",
        datePattern
    );

    datePattern = new DateConversionPattern('d', "HH:mm:ss' on 'yy/MM/dd, Australia/Perth");
    testExtractingASingleDatePattern(
        "%d{HH:mm:ss' on 'yy/MM/dd, Australia/Perth} %p %c{1} %% %m%n",
        " %p %c{1} %% %m%n",
        datePattern
    );

    // Validate extracting %r at different positions in the conversion pattern
    datePattern = new DateConversionPattern('r', null);
    testExtractingASingleSimpleDatePattern(
        "%r %p %c{1} %% %m%n",
        " %p %c{1} %% %m%n",
        datePattern
    );
    testExtractingASingleSimpleDatePattern(
        "%p %r %c{1} %% %m%n",
        "%p  %c{1} %% %m%n",
        datePattern
    );
    testExtractingASingleSimpleDatePattern(
        "%p %c{1} %% %m%n %r",
        "%p %c{1} %% %m%n ",
        datePattern
    );

    ArrayList<DateConversionPattern> datePatterns = new ArrayList<>();

    // Validate extracting multiple simple date conversion patterns
    datePatterns.add(new DateConversionPattern('d', null));
    datePatterns.add(new DateConversionPattern('d', null));
    datePatterns.add(new DateConversionPattern('d', "HH:mm:ss' on 'yy/MM/dd"));
    datePatterns.add(new DateConversionPattern('d', null));
    datePatterns.add(new DateConversionPattern('r', null));
    testExtractingMultipleDatePatterns(
        "%d%d %d{HH:mm:ss' on 'yy/MM/dd}%d %r %p %c{1}: %m%n",
        "   %p %c{1}: %m%n",
        datePatterns
    );

    // Validate extracting multiple simple date conversion patterns
    datePatterns.clear();
    datePatterns.add(new DateConversionPattern('d', null));
    datePatterns.add(new DateConversionPattern('d', null));
    datePatterns.add(new DateConversionPattern('d', "HH:mm:ss' on 'yy/MM/dd, Australia/Perth"));
    datePatterns.add(new DateConversionPattern('d', null));
    datePatterns.add(new DateConversionPattern('r', null));
    testExtractingMultipleDatePatterns(
        "%d%d %d{HH:mm:ss' on 'yy/MM/dd, Australia/Perth}%d %r %p %c{1}: %m%n",
        "   %p %c{1}: %m%n",
        datePatterns
    );
  }

  /**
   * Tests extracting a date pattern from the given conversion pattern, both
   * when used with a Log4j PatternLayout or an EnhancedPatternLayout
   * @param pattern
   * @param expectedPatternWithoutDates
   * @param expectedDatePattern
   */
  private void testExtractingASingleSimpleDatePattern (
      String pattern,
      String expectedPatternWithoutDates,
      DateConversionPattern expectedDatePattern
  ) {
    testExtractingASingleDatePattern(pattern, expectedPatternWithoutDates,
        expectedDatePattern);
  }

  private void testExtractingASingleDatePattern (
      String pattern,
      String expectedPatternWithoutDates,
      DateConversionPattern expectedDatePattern
  ) {
    DateConversionPatternExtractor parser = new DateConversionPatternExtractor(pattern);
    List<DateConversionPattern> parsedDatePatterns = parser.getDateConversionPatterns();
    assertEquals(1, parsedDatePatterns.size());
    assertEquals(expectedDatePattern, parsedDatePatterns.get(0));
    assertEquals(expectedPatternWithoutDates, parser.getConversionPatternWithoutDates());
  }

  private void testExtractingMultipleDatePatterns (
      String pattern,
      String expectedPattern,
      List<DateConversionPattern> expectedDatePatterns
  ) {


    DateConversionPatternExtractor parser = new DateConversionPatternExtractor(pattern);
    List<DateConversionPattern> parsedDatePatterns = parser.getDateConversionPatterns();
    assertEquals(expectedDatePatterns.size(), parsedDatePatterns.size());

    for (int i = 0; i < expectedDatePatterns.size(); ++i) {
      assertEquals(expectedDatePatterns.get(i), parsedDatePatterns.get(i));
    }
    assertEquals(expectedPattern, parser.getConversionPatternWithoutDates());
  }
}