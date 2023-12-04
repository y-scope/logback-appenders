package com.yscope.logging.logback;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;


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
}
