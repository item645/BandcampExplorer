package com.bandcamp.explorer.data;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlySetProperty;
import javafx.beans.property.ReadOnlySetWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * Represents a release on Bandcamp website which can be either
 * album or single track.
 */
public final class Release {

	/**
	 * Pattern to locate a release data within HTML page. The data is defined in JSON format
	 * and assigned to JavaScript variable.
	 */
	private static final Pattern RELEASE_DATA = Pattern.compile("var TralbumData = \\{.+?\\};", Pattern.DOTALL);

	/**
	 * Pattern for locating tags
	 */
	private static final Pattern TAG_DATA = Pattern.compile("<a class=\"tag\".+?>.+?(?=(</a>))", Pattern.DOTALL);

	/**
	 * Pattern for numeric HTML escape codes
	 */
	private static final Pattern HTML_ESCAPE_CODE = Pattern.compile("&#\\d+;");

	/**
	 * Sets some mappings for HTML escape chars to use in tags unescaping operation
	 */
	private static final Map<String, String> HTML_SPECIALS = new HashMap<>();
	static {
		HTML_SPECIALS.put("&amp;", "&");
		HTML_SPECIALS.put("&quot;", "\"");
		HTML_SPECIALS.put("&lt;", "<");
		HTML_SPECIALS.put("&gt;", ">");
	}

	/**
	 * A factory for JavaScript engines allowing to reuse instantiated engines on per-thread basis
	 */
	private static final ThreadLocal<ScriptEngine> JS_ENGINES = new ThreadLocal<ScriptEngine>() {
		@Override
		protected ScriptEngine initialValue() {
			return new ScriptEngineManager().getEngineByName("JavaScript");
		}
	};


	/**
	 * An instance of JavaScript engine is used to extract necessary stuff from JSON data.
	 */
	private final ScriptEngine JS = JS_ENGINES.get();

	private final ReadOnlyStringProperty artist;
	private final ReadOnlyStringProperty title;
	private final ReadOnlyObjectProperty<DownloadType> downloadType;
	private final ReadOnlyObjectProperty<LocalDate> releaseDate;
	private final ReadOnlyObjectProperty<LocalDate> publishDate;
	private final ReadOnlySetProperty<String> tags;
	private final ReadOnlyStringProperty tagsString;
	private final ReadOnlyObjectProperty<URI> uri;
	private final String link;
	private final String artworkThumbLink;
	private final List<Track> tracks;
	private final String information;


	/**
	 * Defines possible download types for release.
	 */
	public static enum DownloadType {

		/**
		 * Release is free for download.
		 */
		FREE,

		/**
		 * Release can be bought for any amount you choose or can be downloaded
		 * for free when you specify a price of 0.
		 */
		NAME_YOUR_PRICE,

		/**
		 * Means that you have actually pay some minimal amount to download the release.
		 */
		PAID,

		/**
		 * Release is unavailable. This basically means that there's no
		 * digital version of a release available for download, while there
		 * still can be options to buy it in physical form, preorder it,
		 * or preview it from the site as stream-only. 
		 */
		UNAVAILABLE
	}


	/** 
	 * Loads the release using specified URL string.
	 * 
	 * @param url URL string
	 * @throws IOException if release web page cannot be loaded for some reason 
	 *         or supplied URL string is not valid
	 * @throws IllegalArgumentException if release web page does not contain valid data
	 * @throws NullPointerException if url is null
	 */
	public Release(String url) throws IOException {
		URI uri_ = URI.create(url);

		try (Scanner input = new Scanner(uri_.toURL().openStream(), StandardCharsets.UTF_8.name())) {
			String artist_ = null, title_ = null;
			DownloadType downloadType_;
			LocalDate releaseDate_, publishDate_;

			// Finding a variable containing release data and feeding it to JS engine so that we could
			// then handily read any property values we need.
			try {
				JS.eval(input.findWithinHorizon(RELEASE_DATA, 0));
			}
			catch (ScriptException | NullPointerException e) {
				throw new IllegalArgumentException("Release data is not valid or cannot be located", e);
			}

			artist_ = property("artist", String.class);
			title_ = property("current.title", String.class);
			releaseDate_ = propertyDate("album_release_date");
			publishDate_ = propertyDate("current.publish_date");
			downloadType_ = determineDownloadType();
			Set<String> tags_ = loadTags(input);
			artworkThumbLink = loadArtworkThumbLink();
			information = Objects.toString(property("current.about", String.class), "");

			// Wrapping all necessary stuff in JFX-compliant properties
			artist = new ReadOnlyStringWrapper(Objects.toString(artist_)).getReadOnlyProperty();
			title = new ReadOnlyStringWrapper(Objects.toString(title_)).getReadOnlyProperty();
			downloadType = new ReadOnlyObjectWrapper<>(downloadType_).getReadOnlyProperty();
			releaseDate = new ReadOnlyObjectWrapper<>(releaseDate_).getReadOnlyProperty();
			publishDate = new ReadOnlyObjectWrapper<>(publishDate_).getReadOnlyProperty();
			tags = new ReadOnlySetWrapper<>(FXCollections.unmodifiableObservableSet(FXCollections.observableSet(tags_)));
			tagsString = new ReadOnlyStringWrapper(tags_.stream().collect(Collectors.joining(", "))).getReadOnlyProperty();
			uri = new ReadOnlyObjectWrapper<>(uri_).getReadOnlyProperty();

			tracks = loadTracks(); // can be called only after this.artist is set

			// This serves as sort of unique identifier for release and is used for equals/hashcode
			link = (uri_.getHost() + uri_.getPath()).toLowerCase(Locale.ROOT);
		}
	}


	/**
	 * Determines the download type.
	 */
	private DownloadType determineDownloadType() {
		DownloadType result = DownloadType.UNAVAILABLE;
		Number dlPref = property("current.download_pref", Number.class);
		if (dlPref != null) {
			switch (dlPref.intValue()) {
			case 1:
				result = DownloadType.FREE;
				break;
			case 2:
				Number minPrice = property("current.minimum_price", Number.class);
				if (minPrice != null && minPrice.doubleValue() > 0.0)
					result = DownloadType.PAID;
				else {
					Number isSet = property("current.is_set_price", Number.class);
					result = isSet != null && isSet.intValue() == 1 
							? DownloadType.PAID : DownloadType.NAME_YOUR_PRICE;
				}
				break;
			default:
			}
		}
		return result;
	}


	/**
	 * Loads a set of tags for this release.
	 */
	private Set<String> loadTags(Scanner input) {
		// Tags are not included in JSON data so we need to fetch them from raw HTML.
		// Resetting to 0 position is not required here because tags are located
		// after TralbumData in HTML source.
		// While it ain't a good idea to extract data from HTML like this, using some
		// third party soup library for such a simple one-time task would be an overkill.
		Set<String> tags = new LinkedHashSet<>();
		String tagData = null;
		while ((tagData = input.findWithinHorizon(TAG_DATA, 0)) != null) {
			int i = tagData.lastIndexOf('>');
			if (i != -1)
				tags.add(unescape(tagData.substring(i+1).trim().toLowerCase(Locale.ENGLISH)));
		}
		return tags;
	}


	/**
	 * Unescapes given string, converting HTML escape symbols and codes 
	 * into appropriate chars.
	 */
	private static String unescape(String s) {
		if (s.indexOf(';') == -1) // common case optimization
			return s;
		else {
			@SuppressWarnings("resource")
			Scanner scan = new Scanner(s);
			String token = null;
			while ((token = scan.findWithinHorizon(HTML_ESCAPE_CODE, 0)) != null) {
				String code = token.substring(2, token.lastIndexOf(';'));
				try {
					// this won't work for anything beyond 0xFFFF, but still suitable
					// for bandcamp tags
					s = s.replace(token, String.valueOf((char)Integer.parseInt(code)));
				}
				catch (NumberFormatException ignored) {} // if we can't convert the code, leave it as it is
			}
			for (Map.Entry<String, String> special : HTML_SPECIALS.entrySet())
				if (s.contains(special.getKey()))
					s = s.replace(special.getKey(), special.getValue());
			return s;
		}
	}	


	/**
	 * Loads all the tracks from JSON data and returns them as unmodifiable
	 * list of Track objects.
	 */
	private List<Track> loadTracks() {
		List<Track> result = new ArrayList<>();

		Pattern splitter = null;
		Number numTracks = property("trackinfo.length", Number.class);
		if (numTracks != null) {
			for (int i = 0; i < numTracks.intValue(); i++) {
				String trackDataID = "trackinfo[" + i + "].";

				String artist = null, title = null;
				String artistTitle = property(trackDataID + "title", String.class);
				if (artistTitle != null) {
					// If there are multiple artists on this release, title property will
					// contain both artist and title, separated by " - ", so we need
					// to split them.
					if (artistTitle.contains(" - ")) {
						if (splitter == null)
							splitter = Pattern.compile(" - ");
						String[] vals = splitter.split(artistTitle, 2);
						artist = vals[0];
						title = vals[1];
						// TODO find a workaround for situations where single-artist release contains tracks with " - "
						// example: http://maxscordamaglia.bandcamp.com/album/eetudes-on-the-run
					}
					else {
						artist = this.artist.get();
						title = artistTitle;
					}
				}

				Number duration = property(trackDataID + "duration", Number.class);
				float durationValue = duration != null ? duration.floatValue() : 0.0f;
				if (durationValue < 0.0f || Float.isNaN(durationValue))
					durationValue = 0.0f;

				String fileLink = null;
				if (property(trackDataID + "file", Object.class) != null)
					fileLink = property(trackDataID + "file['mp3-128']", String.class);

				result.add(new Track(i + 1,	Objects.toString(artist), Objects.toString(title), durationValue, fileLink));
			}
		}

		return Collections.unmodifiableList(result);
	}


	/**
	 * Gets a link to release artwork thumbnail image.
	 */
	private String loadArtworkThumbLink() {
		// We get an url to small 100x100 thumbnail and modify that url
		// so it will point to a bigger 350x350 image (a standart image used
		// to display artwork on release html page). The url is changed by replacing 
		// format specifier (after '_') from 3 to 2. Replacement is done at char 
		// array level to avoid using slow regex-based methods of String.
		String s = property("artThumbURL", String.class);
		int i = s.lastIndexOf('_');
		if (i != -1 && i < s.length() - 1 && s.charAt(i+1) == '3') {
			char[] chars = s.toCharArray();
			chars[i+1] = '2';
			return String.valueOf(chars);
		}
		else
			return s;
	}


	/**
	 * Helper method for reading dates from JSON data.
	 * Returns LocalDate.MIN if date is invalid or absent.
	 */
	private LocalDate propertyDate(String name) {
		LocalDate result = LocalDate.MIN;
		String dateStr = property(name, String.class);
		if (dateStr != null) {
			try {
				result = LocalDate.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME);
			}
			catch (DateTimeParseException e) {
				logError(e);
			}
		}
		return result;
	}


	/**
	 * Helper method for extracting the value of named JSON property in typesafe manner. 
	 * @param name property name
	 * @param type a target type to convert property value to
	 */
	private <T> T property(String name, Class<T> type) {
		try {
			return type.cast(JS.eval("TralbumData." + name));
		} 
		catch (ScriptException | ClassCastException e) {
			logError(e);
			return null;
		}
	}


	/**
	 * To log errors encountered when reading individual properties from JSON.
	 */
	private void logError(Exception e) {
		System.err.println("Error processing release data: " + uri.get() + " (" + e + ")");
	}


	/**
	 * Returns a hash code for this release.
	 */
	@Override
	public int hashCode() {
		return link.hashCode();
	}


	/**
	 * Tests this release for equality with another.
	 * 
	 * Two releases are deemed equal if they were loaded from the same location
	 * at Bandcamp website. Strictly speaking, they are equal if their originating
	 * URLs contain same host and path (ignoring protocols, ports, any possible query
	 * and fragments parts, and character case).
	 * 
	 * Sometimes the same actual release can reside on more than one Bandcamp page
	 * (for example, one on artist page and another on label page).
	 * In that case these releases considered distinct.
	 */
	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		return other instanceof Release ? Objects.equals(this.link, ((Release)other).link) : false;
	}


	/**
	 * Converts this release to its string representation.
	 */
	@Override
	public String toString() {
		return new StringBuilder(artist.get()).append(" - ").append(title.get()).append(" [")
				.append(publishDate.get()).append("] [").append(downloadType.get()).append("] ")
				.append(tags.get()).append(" (").append(uri.get()).append(")").toString();
	}


	/**
	 * Returns an artist of this release.
	 */
	public String getArtist() {
		return artist.get();
	}


	/**
	 * Returns an artist as read-only JavaFX property.
	 */
	public ReadOnlyStringProperty artistProperty() {
		return artist;
	}


	/**
	 * Returns a title of this release.
	 */
	public String getTitle() {
		return title.get();
	}


	/**
	 * Returns a title as read-only JavaFX property.
	 */
	public ReadOnlyStringProperty titleProperty() {
		return title;
	}


	/**
	 * Returns a download type of this release.
	 */
	public DownloadType getDownloadType() {
		return downloadType.get();
	}


	/**
	 * Returns a download type as read-only JavaFX property.
	 */
	public ReadOnlyObjectProperty<DownloadType> downloadTypeProperty() {
		return downloadType;
	}


	/**
	 * Returns a release date.
	 * On some releases the date is not specified, in that case LocalDate.MIN is returned.
	 */
	public LocalDate getReleaseDate() {
		return releaseDate.get();
	}


	/**
	 * Returns a release date as read-only JavaFX property.
	 */
	public ReadOnlyObjectProperty<LocalDate> releaseDateProperty() {
		return releaseDate;
	}


	/**
	 * Returns a publish date, that is, the date when release was 
	 * originally published on Bandcamp.
	 */
	public LocalDate getPublishDate() {
		return publishDate.get();
	}


	/**
	 * Returns a publish date as read-only JavaFX property.
	 */
	public ReadOnlyObjectProperty<LocalDate> publishDateProperty() {
		return publishDate;
	}


	/**
	 * Returns a URI of this release.
	 */
	public URI getURI() {
		return uri.get();
	}


	/**
	 * Returns a URI as read-only JavaFX property.
	 */
	public ReadOnlyObjectProperty<URI> uriProperty() {
		return uri;
	}


	/**
	 * Returns a set of tags describing this release.
	 */
	public Set<String> getTags() {
		return tags.get();
	}


	/**
	 * Returns a set of tags as read-only JavaFX property.
	 */
	public ReadOnlySetProperty<String> tagsProperty() {
		return tags;
	}


	/**
	 * Returns a set of tags as string where individual tags
	 * are separated by comma and space.
	 */
	public String getTagsString() {
		return tagsString.get();
	}


	/**
	 * Returns a set of tags in string form as read-only JavaFX property.
	 */
	public ReadOnlyStringProperty tagsStringProperty() {
		return tagsString;
	}


	/**
	 * Returns an unmodifiable list of all tracks on this release.
	 * If no tracks were found, returns empty list.
	 */
	public List<Track> getTracks() {
		return tracks;
	}


	/**
	 * Returns a string URL to an album artwork thumbnail image.
	 * The returned URL points to a standard 350x350 image which is used on
	 * Bandcamp's release pages to represent artwork.
	 * If image is not specified, returns null.
	 */
	public String getArtworkThumbLink() {
		return artworkThumbLink;
	}


	/**
	 * Returns an information about this release.
	 * If there's no information provided, returns empty string.
	 */
	public String getInformation() {
		return information;
	}

}
