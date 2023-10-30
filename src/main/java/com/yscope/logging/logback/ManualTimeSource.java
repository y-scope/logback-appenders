package com.yscope.logging.logback;

/**
 * Manual time source that returns the last time that was set. This can be
 * useful to control the current time during unit testing.
 */
public class ManualTimeSource implements TimeSource {
  private long currentTimeInMilliseconds = 0;

  @Override
  public synchronized void setCurrentTimeInMilliseconds (long currentTimeInMilliseconds) {
    this.currentTimeInMilliseconds = currentTimeInMilliseconds;
  }

  @Override
  public synchronized long getCurrentTimeInMilliseconds () {
    return currentTimeInMilliseconds;
  }
}
