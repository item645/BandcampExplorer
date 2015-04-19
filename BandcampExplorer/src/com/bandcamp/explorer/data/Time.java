package com.bandcamp.explorer.data;

/**
 * An immutable time duration to represent a time of release or a single audio track. 
 */
public final class Time implements Comparable<Time> {

	private final int seconds;
	private final String asString;


	/**
	 * Constructs an instance of Time using specified duration in seconds.
	 */
	Time(int seconds) {
		this.seconds = seconds;
		this.asString = formatSeconds(seconds);
	}


	/**
	 * Helper method to convert specified duration given as number of seconds 
	 * to a string representation, which can be either in HH:mm:ss format 
	 * (if duration >= 1 hour) or mm:ss format (if duration < 1 hour).
	 * 
	 * @param seconds a number of seconds that represents duration
	 * @return a duration as string
	 * @throws IllegalArgumentException if specified number of seconds is negative
	 */
	public static String formatSeconds(int seconds) {
		if (seconds < 0)
			throw new IllegalArgumentException("A number of seconds is negative: " + seconds);

		int m = seconds / 60 % 60;
		int s = seconds % 60;
		if (seconds >= 3600) {
			int h = seconds / 3600;
			return String.format("%02d:%02d:%02d", h, m, s);
		}
		else
			return String.format("%02d:%02d", m, s);
	}


	/**
	 * Returns a hash code for this time.
	 */
	@Override
	public int hashCode() {
		return seconds;
	}


	/**
	 * Tests this time object for equality with another.
	 * Two time objects are equal if they contain same number of seconds.
	 */
	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		return other instanceof Time ? this.seconds == ((Time)other).seconds : false;
	}


	/** 
	 * Compares this time to another using their duration in seconds.
	 */
	@Override
	public int compareTo(Time other) {
		return Integer.compare(this.seconds, other.seconds);
	}


	/**
	 * Returns a string representation of this time, which can be either in
	 * HH:mm:ss format (if duration >= 1 hour) or mm:ss format (if duration < 1 hour). 
	 */
	@Override
	public String toString() {
		return asString;
	}


	/**
	 * Returns a duration of this time in seconds.
	 */
	public int getSeconds() {
		return seconds;
	}
}