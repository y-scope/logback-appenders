package com.yscope.logging.logback;

import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.Layout;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdOutputStream;
import com.yscope.clp.irstream.AbstractClpIrOutputStream;
import com.yscope.clp.irstream.EightByteClpIrOutputStream;
import com.yscope.clp.irstream.FourByteClpIrOutputStream;
import org.apache.commons.io.output.CountingOutputStream;


public class ClpIrFileAppender extends AppenderBase<ILoggingEvent> implements Flushable {
  private String timestampPattern = null;
  private Layout<ILoggingEvent> layout;
  protected String pattern;
  private final static String DEFAULT_PATTERN = "[%thread] %logger %msg";

  private int estimatedFormattedTimestampLength = 0;
  private long uncompressedSizeInBytes = 0;

  private AbstractClpIrOutputStream clpIrOutputStream;
  private CountingOutputStream countingOutputStream;

  private boolean activated = false;

  // Appender settings
  // NOTE: It may appear that these settings are never set but Logback sets these
  // through reflection
  // For descriptions of these settings, see the (non-default) constructor
  private int compressionLevel = 3;
  private boolean closeFrameOnFlush = true;
  private String file;
  private boolean useFourByteEncoding = true;

  public ClpIrFileAppender () {
  }

  /**
   * Constructs a ClpIrFileAppender
   * @param filePath Output file path
   * @param pattern Logback pattern for formatting log events. It would only be converted to
   * {@code ch.qos.logback.classic.PatternLayout},
   * for now. For pattern string, callers should not add a date conversion since this appender
   * stores timestamps and messages separately. Any date patterns found in the
   * pattern will be removed.
   * @param context Logback Layout context
   * @param useFourByteEncoding Whether to use CLP's four-byte encoding instead
   * of the default eight-byte encoding. The four-byte encoding has lower
   * memory usage but can also result in lower compression ratio.
   * @param closeFrameOnFlush Whether to close the Zstandard frame on flush
   * @param compressionLevel Compression level to use for Zstandard. Valid
   * levels are 1 to 19.
   * @throws IOException on I/O error
   */
  public ClpIrFileAppender (
      String filePath,
      String pattern,
      Context context,
      boolean useFourByteEncoding,
      boolean closeFrameOnFlush,
      int compressionLevel
  ) throws IOException {
    super();

    setFile(filePath);
    setPattern(pattern);
    setContext(context);
    setUseFourByteEncoding(useFourByteEncoding);
    setCompressionLevel(compressionLevel);
    setCloseFrameOnFlush(closeFrameOnFlush);

    // NOTE: We don't enable the shutdown hook since the caller should handle
    // closing the appender properly when shutting down (enabling the hook may
    // also be confusing).
    startHelper(false);
  }

  public String getPattern() {
    return pattern;
  }

  public void setPattern(String pattern) {
    this.pattern = pattern;
  }

  public void setFile (String file) {
    if (file == null) {
      this.file = file;
    } else {
      this.file = file.trim();
    }
  }

  /**
   * @return The amount of data written to this appender for the current output
   * file, in bytes.
   * <p>
   * NOTE:
   * <ul>
   *   <li>This may be slightly inaccurate since we use an estimate of the
   *   timestamp length for performance reasons.</li>
   *   <li>This will be reset when a new output file is opened.</li>
   * </ul>
   */
  public synchronized long getUncompressedSize () {
    return uncompressedSizeInBytes;
  }

  public Layout<ILoggingEvent> getLayout() {
    return layout;
  }

  /**
   * @return The amount of data written by this appender to the current output
   * file, in bytes. This will be reset when a new output file is opened.
   */
  public synchronized long getCompressedSize () {
    return countingOutputStream.getByteCount();
  }

  public void setUseFourByteEncoding (boolean useFourByteEncoding) {
    this.useFourByteEncoding = useFourByteEncoding;
  }

  public void setCompressionLevel (int compressionLevel) {
    this.compressionLevel = compressionLevel;
  }

  public void setCloseFrameOnFlush (boolean closeFrameOnFlush) {
    this.closeFrameOnFlush = closeFrameOnFlush;
  }

  public Layout<ILoggingEvent> buildLayout() {
    if (this.pattern == null) {
      this.pattern = DEFAULT_PATTERN;
    }

    PatternLayout layout = new PatternLayout();
    layout.setPattern(this.getPattern());
    layout.setContext(this.getContext());
    layout.start();
    return layout;
  }

  /**
   * Closes the previous file and starts a new file with the given path
   * @param path Path for the new file
   * @throws IOException on I/O error
   */
  public synchronized void startNewFile (String path) throws IOException {
    if (false == activated) {
      throw new IllegalStateException("Appender not activated.");
    }

    if (!this.isStarted()) {
      throw new IllegalStateException("Appender already closed.");
    }

    clpIrOutputStream.close();
    uncompressedSizeInBytes = 0;

    setFile(path);
    sanitizeFilePath();
    createOutputStream();
  }

  /**
   * Flushes the appender. If closeFrameOnFlush was set, the Zstandard frame is
   * closed and all log events are written to the output stream. If not, then
   * some log events may still be buffered in memory.
   * @throws IOException on I/O error
   */
  @Override
  public synchronized void flush () throws IOException {
    clpIrOutputStream.flush();
  }

  /**
   * Appends the given log event to the IR stream
   * @param event The log event
   */
  @Override
  public void append(ILoggingEvent event) {
    try {
      String formattedEvent = layout.doLayout(event);
      byte[] formattedEventBytes = formattedEvent.getBytes(StandardCharsets.ISO_8859_1);
      ByteBuffer message = ByteBuffer.wrap(formattedEventBytes);
      clpIrOutputStream.writeLogEvent(event.getTimeStamp(), message);
      uncompressedSizeInBytes += estimatedFormattedTimestampLength + message.limit();
    } catch (IOException ex) {
      this.addError("Failed to write log event.", ex);
    }
  };

  /**
   * Activates the appender's options. This should not be called when this
   * appender is instantiated manually.
   */
  @Override
  public void start () {

    if (activated) {
      this.addWarn("Already activated.");
      return;
    }

    try {
      startHelper(true);
    } catch (Exception ex) {
      this.addError("Failed to activate appender.", ex);
      super.stop();
    }
  }

  /**
   * Closes the appender. Once closed, the appender cannot be reopened.
   */
  @Override
  public synchronized void stop () {
    if (!this.isStarted()) {
      return;
    }

    try {
      clpIrOutputStream.close();
    } catch (IOException ex) {
      this.addError("Failed to close output file.", ex);
    }

    super.stop();
  }

  /**
   * Helper method to activate options.
   * @param enableShutdownHook Whether to enable a shutdown hook to close the
   * appender.
   * @throws IOException on I/O error
   */
  private void startHelper (boolean enableShutdownHook) throws IOException {
    super.start();

    validateOptionsAndInit();

    activated = true;
    if (enableShutdownHook) {
      // logback may not attempt to close the appender when the JVM shuts down, so
      // this hook ensures we try to close the appender before shutdown.
      Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }
  }

  /**
   * Validates the appender's settings (e.g., compression level) and initializes
   * the appender with them.
   * @throws IOException on I/O error
   */
  private void validateOptionsAndInit () throws IOException {
    this.pattern = processConversionPattern(pattern);
    layout = buildLayout();

    if (compressionLevel < Zstd.minCompressionLevel()
        || Zstd.maxCompressionLevel() < compressionLevel)
    {
      throw new IllegalArgumentException("compressionLevel is outside of valid range: ["
          + Zstd.minCompressionLevel() + ", "
          + Zstd.maxCompressionLevel() + "]");
    }

    sanitizeFilePath();

    createOutputStream();
  }

  private void sanitizeFilePath () {
    if (null == file) {
      throw new IllegalArgumentException("file option not set.");
    }
    // Trim surrounding spaces
    file = file.trim();
  }

  /**
   * Creates the CLP IR output stream, the file output stream, and any necessary
   * streams in between.
   * @throws IOException on I/O error
   */
  private void createOutputStream () throws IOException {
    FileOutputStream fileOutputStream = Utils.createOutputFile(file);
    countingOutputStream = new CountingOutputStream(fileOutputStream);
    ZstdOutputStream zstdOutputStream =
        new ZstdOutputStream(countingOutputStream, compressionLevel);
    zstdOutputStream.setCloseFrameOnFlush(closeFrameOnFlush);
    // Get the local time zone in case we need to determine the time zone
    // of timestamps printed in the content of log messages. This is not the
    // time zone used to display log messages to the user (that will be
    // determined by the user's locale, etc.).
    String timeZoneId = ZonedDateTime.now().getZone().toString();
    if (useFourByteEncoding) {
      clpIrOutputStream =
          new FourByteClpIrOutputStream(timestampPattern, timeZoneId, zstdOutputStream);
    } else {
      clpIrOutputStream =
          new EightByteClpIrOutputStream(timestampPattern, timeZoneId, zstdOutputStream);
    }

    uncompressedSizeInBytes += timestampPattern.getBytes(StandardCharsets.ISO_8859_1).length;
    uncompressedSizeInBytes += timeZoneId.length();
  }

  /**
   * Gets the timestamp pattern from the given Logback Layout's conversion pattern
   * and creates a conversion pattern that doesn't contain any date conversion
   * patterns.
   * <p>
   * E.g., if the conversion pattern is
   * "%d{yyyy-MM-dd HH:mm:ss.SSSZ} %p [%c{1}] %m%n", this method will set the
   * timestamp pattern to "yyyy-MM-dd HH:mm:ss.SSSZ" and create the conversion
   * pattern, " %p [%c{1}] %m%n".
   * @param pattern Logback pattern for formatting log events
   * @return The conversion pattern without date conversion patterns
   */
  private String processConversionPattern (String pattern) {
    DateConversionPatternExtractor datePatternExtractor =
        new DateConversionPatternExtractor(pattern);

    for (DateConversionPattern datePattern : datePatternExtractor.getDateConversionPatterns()) {
      if (null != timestampPattern) {
        this.addError("Found multiple date conversion specifiers in pattern. Only the first will "
            + "be preserved.");
        continue;
      }

      // + 1 is the character after the '%'
      if ('r' == datePattern.specifier) {
        this.addError("%r is unsupported and will be ignored.");
      } else if ('d' == datePattern.specifier) {

        if (null == datePattern.format) {
          // Pattern is "%d" which implies ISO8601 ("yyyy-MM-dd HH:mm:ss,SSS")
          timestampPattern = "yyyy-MM-dd HH:mm:ss,SSS";
          estimatedFormattedTimestampLength = timestampPattern.length();
        } else {
          // Pattern is "%d{...}"
          switch (datePattern.format) {
            case "ABSOLUTE":
              timestampPattern = "HH:mm:ss,SSS";
              estimatedFormattedTimestampLength = timestampPattern.length();
              break;
            case "DATE":
              timestampPattern = "dd MMM yyyy HH:mm:ss,SSS";
              estimatedFormattedTimestampLength = timestampPattern.length();
              break;
            case "ISO8601":
              timestampPattern = "yyyy-MM-dd HH:mm:ss,SSS";
              estimatedFormattedTimestampLength = timestampPattern.length();
              break;
            default:
              timestampPattern = datePattern.format;
              // NOTE: We use getBytes(ISO_8859_1) since the user's dateFormat
              // may contain Unicode characters
              estimatedFormattedTimestampLength =
                  timestampPattern.getBytes(StandardCharsets.ISO_8859_1).length;
              break;
          }
        }
      }
    }

    return datePatternExtractor.getConversionPatternWithoutDates();
  }
}
