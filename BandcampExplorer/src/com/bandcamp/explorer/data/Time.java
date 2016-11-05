package com.bandcamp.explorer.data;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * An immutable time duration to represent a time of release or a single audio track. 
 */
public final class Time implements Comparable<Time> {

	/**
	 * Max value for caching, corresponds to 10:00.
	 */
	private static final int CACHE_MAX_VALUE = 600;

	/**
	 * Lazily-populated cache to store frequently used Time values in 00:00-10:00 range.
	 * A number of seconds which constitutes time value serves as the index of array element
	 * that holds corresponding Time instance.
	 * We use AtomicReferenceArray instead of regular array to obtain volatile access semantics
	 * for individual array elements, thus ensuring visibility of updates to those elements
	 * for all reader threads.
	 */
	private static final AtomicReferenceArray<Time> cache = new AtomicReferenceArray<>(CACHE_MAX_VALUE);


	private final int seconds;
	private final String asString;


	/**
	 * Constructs an instance of Time using specified duration in seconds.
	 */
	private Time(int seconds) {
		this.seconds = seconds;
		this.asString = formatSeconds(seconds);
	}


	/**
	 * Obtains a Time object corresponding to the specified number of seconds.
	 * For some frequently used time durations this method may return
	 * a cached instance.
	 * 
	 * @param seconds a duration of time in seconds
	 * @return a Time instance, either new or cached
	 * @throws IllegalArgumentException if specified number of seconds is negative
	 */
	static Time ofSeconds(int seconds) {
		if (seconds >= 0 && seconds <= CACHE_MAX_VALUE) {
			Time time = cache.get(seconds);
			if (time == null)
				// There is a window for "check-then-act" race condition, which, however,
				// does no harm other than instantiating new Time object for same value of 
				// seconds more than once on (very) rare occasion.
				cache.set(seconds, time = new Time(seconds));
			return time;
		}
		return new Time(seconds);
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

		StringBuilder result = new StringBuilder();

		if (seconds >= 3600) {
			appendPadded(result, seconds / 3600);
			result.append(':');
		}
		appendPadded(result, seconds / 60 % 60);
		result.append(':');
		appendPadded(result, seconds % 60);

		return result.toString();
	}


	/**
	 * Appends the given number to string builder, adding leading zero
	 * if number < 10.
	 * 
	 * @param sb string builder
	 * @param n a number
	 */
	private static void appendPadded(StringBuilder sb, int n) {
		if (n < 10)
			sb.append('0');
		sb.append(Integer.toString(n));
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
		return this == other || other instanceof Time && this.seconds == ((Time)other).seconds;
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
	public int seconds() {
		return seconds;
	}
}
