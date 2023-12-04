package com.yscope.logging.logback;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.Context;
import com.yscope.logging.logback.ClpIrFileAppender;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


public class ClpIrFileAppenderTest {
  private final String patternLayoutString =
      "%d{yyyy-MM-dd HH:mm:ss.SSS Z} [%thread] %level %class.%method(%file:%line): %msg %n";
  private final int compressionLevel = 3;
  private Context context = new ch.qos.logback.classic.LoggerContext();

  @Test
  public void testFourByteIrAppender() {
    testAppender(true);
  }

  @Test
  public void testEightByteIrAppender () {
    testAppender(false);
  }

  private void testAppender (boolean useFourByteEncoding) {
    String fileName = useFourByteEncoding ? "four-byte.clp.zst" : "eight-byte.clp.zst";

    // Validate invalid arguments are detected
    assertThrowsExactly(IllegalArgumentException.class,
        () -> new ClpIrFileAppender(null, patternLayoutString, context, useFourByteEncoding,
            false, compressionLevel));
    assertThrowsExactly(FileNotFoundException.class,
        () -> new ClpIrFileAppender("", patternLayoutString, context, useFourByteEncoding, false,
            compressionLevel));
    assertThrowsExactly(IllegalArgumentException.class,
        () -> new ClpIrFileAppender(fileName, null, context, useFourByteEncoding, false,
            compressionLevel));
    assertThrowsExactly(IllegalArgumentException.class,
        () -> new ClpIrFileAppender(fileName, patternLayoutString, context, useFourByteEncoding,
            false, Integer.MIN_VALUE));
    assertThrowsExactly(IllegalArgumentException.class,
        () -> new ClpIrFileAppender(fileName, patternLayoutString, context, useFourByteEncoding,
            false, Integer.MAX_VALUE));

    // Validate different file paths
    try {
      testEmptyCreation(Paths.get(fileName), patternLayoutString, useFourByteEncoding);
      testEmptyCreation(Paths.get("a", "b", fileName), patternLayoutString, useFourByteEncoding);
    } catch (Exception ex) {
      fail(ex);
    }

    // Validate types of layouts
    try {
      testLayouts(fileName, useFourByteEncoding);
    } catch (Exception ex) {
      fail(ex);
    }

    // Test writing
    try {
      testWriting(fileName, useFourByteEncoding, false, compressionLevel);
      testWriting(fileName, useFourByteEncoding, true, compressionLevel);
      testWriting(fileName, useFourByteEncoding, false, compressionLevel + 1);
    } catch (Exception ex) {
      fail(ex);
    }
  }

  /**
   * Tests creating an empty CLP IR stream log with the given path.
   * @param filePath Path to create. Note that after the test, the entire
   * directory tree specified by the path will be deleted.
   * @param useFourByteEncoding
   * @throws IOException on I/O error
   */
  private void testEmptyCreation (
      Path filePath,
      String pattern,
      boolean useFourByteEncoding
  ) throws IOException {
    String filePathString = filePath.toString();
    ClpIrFileAppender clpIrFileAppender = new ClpIrFileAppender(filePathString, pattern, context,
        useFourByteEncoding,
        false, compressionLevel);
    clpIrFileAppender.stop();
    assertTrue(Files.exists(filePath));

    Path parent = filePath.getParent();
    if (null == parent) {
      Files.delete(filePath);
    } else {
      // Get top-level parent
      while (true) {
        Path p = parent.getParent();
        if (null == p) {
          break;
        }
        parent = p;
      }
      FileUtils.deleteDirectory(parent.toFile());
    }
  }

  /**
   * Test all possible logback layouts
   * @param filePathString
   * @param useFourByteEncoding
   * @throws IOException on I/O error
   */
  private void testLayouts (String filePathString, boolean useFourByteEncoding) throws IOException
  {
    Path filePath = Paths.get(filePathString);

    testEmptyCreation(filePath, patternLayoutString, useFourByteEncoding);

    assertThrowsExactly(IllegalArgumentException.class,
        () -> new ClpIrFileAppender(filePathString, null, context,
            useFourByteEncoding, false, compressionLevel));
    assertThrowsExactly(IllegalArgumentException.class,
        () -> new ClpIrFileAppender(filePathString, "", context,
            useFourByteEncoding, false, compressionLevel));
  }

  /**
   * Test writing log files
   * @param fileName
   * @param useFourByteEncoding
   * @param closeFrameOnFlush
   * @param compressionLevel
   * @throws IOException on I/O error
   */
  private void testWriting (
      String fileName,
      boolean useFourByteEncoding,
      boolean closeFrameOnFlush,
      int compressionLevel
  ) throws IOException {
    // TODO Once decoding support has been added to clp-ffi-java, these tests
    //  should all be verified by a decoding the stream and comparing it with
    //  the output of an uncompressed file appender.

    String message = "Static text, dictVar1, 123, 456.7, dictVar2, 987, 654.3";

    ClpIrFileAppender clpIrFileAppender = new ClpIrFileAppender(fileName, patternLayoutString,
        context,
        useFourByteEncoding,
        closeFrameOnFlush,
        compressionLevel);

    // Log some normal logs
    for (int i = 0; i < 100; ++i) {
      LoggingEvent event = new LoggingEvent();
      event.setLoggerName("com.yscope.logging.logback");
      event.setLevel(Level.INFO);
      event.setMessage(message);
      event.setCallerData(new StackTraceElement[0]);

      clpIrFileAppender.append(event);
    }

    // Log with an exception
    LoggingEvent event = new LoggingEvent();
    event.setLoggerName("com.yscope.logging.logback");
    event.setLevel(Level.INFO);
    event.setMessage(message);
    event.setThrowableProxy(new ThrowableProxy(new FileNotFoundException()));
    event.setCallerData(new StackTraceElement[0]);

    clpIrFileAppender.append(event);

    clpIrFileAppender.flush();

    // Split into a new file
    String fileName2 = fileName + ".2";
    clpIrFileAppender.startNewFile(fileName2);

    // Add some more logs
    for (int i = 0; i < 100; ++i) {
      LoggingEvent nextEvent = new LoggingEvent();
      nextEvent.setLoggerName("com.yscope.logging.logback");
      nextEvent.setLevel(Level.WARN);
      nextEvent.setMessage(message);
      nextEvent.setCallerData(new StackTraceElement[0]);

      clpIrFileAppender.append(nextEvent);
    }

    clpIrFileAppender.stop();

    // Verify file existence
    Path filePath = Paths.get(fileName);
    assertTrue(Files.exists(filePath));
    Files.delete(filePath);
    Path filePath2 = Paths.get(fileName2);
    assertTrue(Files.exists(filePath2));
    Files.delete(filePath2);
  }
}
