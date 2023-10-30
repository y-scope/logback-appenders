package com.yscope.logging.logback;

/**
 * Interface for a time source. This interface is currently used to swap out
 * real time sources (e.g., System.currentTimeMillis()) with manual time sources
 * that can be used during unit tests.
 */
public interface TimeSource {
  /**
   * If supported, sets the current time for this time source.
   * @param currentTimeInMilliseconds Current time in milliseconds from some
   * fixed point in time (e.g. the UNIX epoch).
   */
  public void setCurrentTimeInMilliseconds (long currentTimeInMilliseconds);

  /**
   * @return The time source's current time
   */
  public long getCurrentTimeInMilliseconds ();
}
