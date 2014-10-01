package com.bandcamp.explorer.data;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/**
 * Represents an individual audio track on release.
 */
public final class Track {

	private final ReadOnlyIntegerProperty number;
	private final ReadOnlyStringProperty artist;
	private final ReadOnlyStringProperty title;
	private final ReadOnlyObjectProperty<Time> time;
	private final String fileLink;


	/**
	 * Class for representing a track time.
	 */
	public static final class Time implements Comparable<Time> {

		public static final DateTimeFormatter HH_mm_ss = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH);
		public static final DateTimeFormatter mm_ss = DateTimeFormatter.ofPattern("mm:ss", Locale.ENGLISH);

		private final int seconds;
		private final String asString;


		/**
		 * Constructs an instance of Time using specified duration in seconds.
		 */
		Time(int seconds) {
			this.seconds = seconds;
			this.asString = LocalTime.ofSecondOfDay(seconds).format(seconds >= 3600 ? HH_mm_ss : mm_ss);
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


	/**
	 * Constructs a track instance using specified parameters.
	 * 
	 * @param number track number (should be > 0)
	 * @param artist artist name
	 * @param title a title
	 * @param seconds track time in seconds
	 * @param fileLink an URL string that points to this track's actual audio file
	 */
	Track(int number, String artist, String title, float seconds, String fileLink) {
		assert number > 0;
		assert artist != null;
		assert title != null;
		assert seconds >= 0.0 && !Float.isNaN(seconds);

		this.number = new ReadOnlyIntegerWrapper(number).getReadOnlyProperty();
		this.artist = new ReadOnlyStringWrapper(artist).getReadOnlyProperty();
		this.title = new ReadOnlyStringWrapper(title).getReadOnlyProperty();
		this.time = new ReadOnlyObjectWrapper<>(new Time(Math.round(seconds))).getReadOnlyProperty();
		this.fileLink = fileLink;
	}


	/**
	 * Returns a string representation of this track.
	 */
	@Override
	public String toString() {
		return new StringBuilder(Integer.toString(number.get())).append(". ").append(artist.get())
				.append(" - ").append(title.get()).append(" (").append(time.get()).append(")")
				.toString();
	}


	/**
	 * Returns a number of this track on release.
	 */
	public int getNumber() {
		return number.get();
	}


	/**
	 * Returns track number as read-only JavaFX property.
	 */
	public ReadOnlyIntegerProperty numberProperty() {
		return number;
	}


	/**
	 * Returns artist name.
	 */
	public String getArtist() {
		return artist.get();
	}


	/**
	 * Returns artist name as read-only JavaFX property.
	 */
	public ReadOnlyStringProperty artistProperty() {
		return artist;
	}


	/**
	 * Returns a title of this track.
	 */
	public String getTitle() {
		return title.get();
	}


	/**
	 * Returns a title of this track as read-only JavaFX property.
	 */
	public ReadOnlyStringProperty titleProperty() {
		return title;
	}


	/**
	 * Returns track time.
	 */
	public Time getTime() {
		return time.get();
	}


	/**
	 * Returns track time as read-only JavaFX property.
	 */
	public ReadOnlyObjectProperty<Time> timeProperty() {
		return time;
	}


	/**
	 * Returns an URL string that points to this track's actual audio file.
	 * If no audio file is available, returns null.
	 */
	public String getFileLink() {
		return fileLink;
	}


	/**
	 * Returns true if this track can be played (i.e. if this track has associated audio data).
	 */
	public boolean isPlayable() {
		return fileLink != null;
	}

}
