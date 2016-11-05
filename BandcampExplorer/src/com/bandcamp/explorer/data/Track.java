package com.bandcamp.explorer.data;

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
	private final String link;
	private final String fileLink;


	/**
	 * Constructs a track instance using specified parameters.
	 * 
	 * @param number track number (should be > 0)
	 * @param artist artist name
	 * @param title a title
	 * @param seconds track time in seconds
	 * @param link a Bandcamp URL string of this track
	 * @param fileLink an URL string that points to this track's actual audio file
	 */
	Track(int number, String artist, String title, float seconds, String link, String fileLink) {
		assert number > 0;
		assert artist != null;
		assert title != null;
		assert seconds >= 0.0;
		assert link != null;

		this.number = new ReadOnlyIntegerWrapper(number).getReadOnlyProperty();
		this.artist = new ReadOnlyStringWrapper(artist).getReadOnlyProperty();
		this.title = new ReadOnlyStringWrapper(title).getReadOnlyProperty();
		this.time = new ReadOnlyObjectWrapper<>(Time.ofSeconds(Math.round(seconds))).getReadOnlyProperty();
		this.link = link;
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
	public int number() {
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
	public String artist() {
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
	public String title() {
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
	public Time time() {
		return time.get();
	}


	/**
	 * Returns track time as read-only JavaFX property.
	 */
	public ReadOnlyObjectProperty<Time> timeProperty() {
		return time;
	}


	/**
	 * Returns an URL string of this track's web page on Bandcamp.
	 */
	public String link() {
		return link;
	}


	/**
	 * Returns an URL string that points to this track's actual audio file.
	 * If no audio file is available, returns null.
	 */
	public String fileLink() {
		return fileLink;
	}


	/**
	 * Returns true if this track can be played (i.e. if this track has associated audio data).
	 */
	public boolean isPlayable() {
		return fileLink != null;
	}

}
