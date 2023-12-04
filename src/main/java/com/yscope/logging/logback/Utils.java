package com.yscope.logging.logback;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import ch.qos.logback.core.CoreConstants;


public class Utils {
  /**
   * Creates and opens an output file and file output stream.
   * @param filePath Path for the file
   * @return The file output stream
   * @throws IOException on I/O error
   */
  public static FileOutputStream createOutputFile (String filePath) throws IOException {
    FileOutputStream fileOutputStream;
    try {
      // append = false since we don't support appending to an existing file
      fileOutputStream = new FileOutputStream(filePath, false);
    } catch (FileNotFoundException ex) {
      // Create the parent directory if necessary
      String parentPath = new File(filePath).getParent();
      if (null == parentPath) {
        throw ex;
      }
      Files.createDirectories(Paths.get(parentPath));

      fileOutputStream = new FileOutputStream(filePath, false);
    }
    return fileOutputStream;
  }

  /**
   * Writes the given throwable's string representation to the given output
   * stream
   * @param throwableStrRepresentation String representation of the throwable
   * @param outputStream Output stream to write to
   * @throws IOException on I/O error
   */
  public static void writeThrowableStrRepresentation (
      String[] throwableStrRepresentation,
      OutputStream outputStream
  ) throws IOException {
    byte[] lineSeparatorBytes = CoreConstants.LINE_SEPARATOR.getBytes(StandardCharsets.UTF_8);
    for (String value : throwableStrRepresentation) {
      outputStream.write(value.getBytes(StandardCharsets.UTF_8));
      outputStream.write(lineSeparatorBytes);
    }
  }
}
