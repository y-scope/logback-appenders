package com.yscope.logging.logback;

/**
 * Time source for the system's current time.
 */
public class SystemTimeSource implements TimeSource {
  @Override
  public void setCurrentTimeInMilliseconds (long currentTimeInMilliseconds) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getCurrentTimeInMilliseconds () {
    return System.currentTimeMillis();
  }
}
