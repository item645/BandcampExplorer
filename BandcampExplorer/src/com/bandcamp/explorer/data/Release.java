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
import java.util.logging.Level;
import java.util.logging.Logger;
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

	private static final Logger LOGGER = Logger.getLogger(Release.class.getName());

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
	private final ReadOnlyObjectProperty<Time> time;
	private final ReadOnlyObjectProperty<LocalDate> releaseDate;
	private final ReadOnlyObjectProperty<LocalDate> publishDate;
	private final ReadOnlySetProperty<String> tags;
	private final ReadOnlyStringProperty tagsString;
	private final ReadOnlyObjectProperty<URI> uri;
	private final String artworkThumbLink;
	private final List<Track> tracks;
	private final String information;
	private final String credits;


	/**
	 * Defines possible download types for release.
	 */
	public enum DownloadType {

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
	 * Returns the release corresponding to the specified Bandcamp URL string.
	 * If release object for this URL string is available in cache,
	 * then cached version is returned. Otherwise, new release object is
	 * created, using data loaded from the URL, added to cache and then returned.
	 * 
	 * @param url URL string representing a location to load release from
	 * @throws IOException if there was an IO error while loading release web page
	 *         or if release page does not contain valid data
	 * @throws NullPointerException if url is null
	 * @throws IllegalArgumentException if supplied URL string is not valid
	 */
	public static Release forURL(String url) throws IOException {
		URI uri = URI.create(url);
		return ReleaseCache.INSTANCE.getRelease(createID(uri), () -> {
			LOGGER.info("Loading release: " + uri);
			return new Release(uri);
		});
	}


	/**
	 * Creates unique identifier for release, derived from the URI in a way
	 * that such ID represents a release location at Bandcamp website.
	 * 
	 * @param uri a URI
	 * @return unique ID
	 */
	private static String createID(URI uri) {
		return (uri.getHost() + uri.getPath()).toLowerCase(Locale.ROOT);
	}


	/** 
	 * Loads the release using specified URI.
	 * 
	 * @param uri a URI to load release from
	 * @throws IOException if there was an IO error while loading release web page
	 *         or if release page does not contain valid data
	 */
	private Release(URI uri) throws IOException {
		try (Scanner input = new Scanner(uri.toURL().openStream(), StandardCharsets.UTF_8.name())) {
			String artist_, title_;
			DownloadType downloadType_;
			LocalDate releaseDate_, publishDate_;

			// Finding a variable containing release data and feeding it to JS engine so that we could
			// then handily read any property values we need.
			try {
				JS.eval(input.findWithinHorizon(RELEASE_DATA, 0));
			}
			catch (Exception e) {
				throw new IOException("Release data is not valid or cannot be located", e);
			}

			artist_ = Objects.toString(property("artist", String.class));
			title_ = Objects.toString(property("current.title", String.class));
			releaseDate_ = propertyDate("album_release_date");
			publishDate_ = propertyDate("current.publish_date");
			downloadType_ = determineDownloadType();
			Set<String> tags_ = loadTags(input);
			tracks = loadTracks(artist_);
			artworkThumbLink = loadArtworkThumbLink();
			information = Objects.toString(property("current.about", String.class), "");
			credits = Objects.toString(property("current.credits", String.class), "");
			int time_ = tracks.stream().collect(Collectors.summingInt(track -> track.getTime().getSeconds()));

			// Wrapping all necessary stuff in JFX-compliant properties
			this.uri = new ReadOnlyObjectWrapper<>(uri).getReadOnlyProperty();
			artist = new ReadOnlyStringWrapper(artist_).getReadOnlyProperty();
			title = new ReadOnlyStringWrapper(title_).getReadOnlyProperty();
			downloadType = new ReadOnlyObjectWrapper<>(downloadType_).getReadOnlyProperty();
			time = new ReadOnlyObjectWrapper<Time>(new Time(time_)).getReadOnlyProperty();
			releaseDate = new ReadOnlyObjectWrapper<>(releaseDate_).getReadOnlyProperty();
			publishDate = new ReadOnlyObjectWrapper<>(publishDate_).getReadOnlyProperty();
			tags = new ReadOnlySetWrapper<>(FXCollections.unmodifiableObservableSet(FXCollections.observableSet(tags_)));
			tagsString = new ReadOnlyStringWrapper(tags_.stream().collect(Collectors.joining(", "))).getReadOnlyProperty();
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
	 * 
	 * @param releaseArtist the artist of this release
	 */
	private List<Track> loadTracks(String releaseArtist) {
		List<Track> result = new ArrayList<>();

		Pattern splitter = null;
		Number numTracks = property("trackinfo.length", Number.class);
		if (numTracks != null) {
			for (int i = 0; i < numTracks.intValue(); i++) {
				String trackDataID = "trackinfo[" + i + "].";

				String artistTitle = property(trackDataID + "title", String.class);
				String artist = releaseArtist;
				String title = artistTitle;
				if (artistTitle != null && artistTitle.contains(" - ")) {

					// Here we are trying to figure out whether the title property contains not
					// only the title of a track, but also its artist name, that is different
					// from the release artist (this usually happens for various artists type
					// compilations and splits).
					// In that case artist and title are separated by " - ", however, additional
					// check should be made to correctly handle situations where title property
					// doesn't specify track artist and " - " is actually a part of track title.
					// Since there is no JSON property that explicitly defines track artist,
					// the only reliable way to do such check is to use title_link property from
					// which the actual title (without artist) can be extracted, although in
					// stripped (escaped) form. After we have both versions (taken from title and
					// title_link), we can decide whether the title property value should be split
					// (if it contains track artist) or left intact (if it contains only track title).
					String titleLink = property(trackDataID + "title_link", String.class);
					if (titleLink != null) {

						// Get the first token (word) from title_link
						String linkToken = titleLink.substring(titleLink.lastIndexOf('/') + 1);
						int h1 = linkToken.indexOf('-');
						if (h1 != -1) 
							linkToken = linkToken.substring(0, h1);
						linkToken = linkToken.toLowerCase(Locale.ENGLISH);

						// Get the first token (word) from title 
						int s = artistTitle.indexOf(' ');
						int h2 = artistTitle.indexOf('-');
						String titleToken = artistTitle.substring(0, Math.min(s, h2)).toLowerCase(Locale.ENGLISH);

						// Compare the tokens. If they are different, it means that title property
						// contains track artist and thus should be split.
						// Still, this won't work in some corner cases. For example, when on
						// some single-artist release some track has " - " in its title and
						// the first word in that title contains some illegal chars which were dropped
						// from it in title_link.
						// However, such cases are expected to be quite rare and won't be a good reason
						// to throw in additional checks and complicating the logic even more.
						if (!linkToken.equals(titleToken)) {
							if (splitter == null)
								splitter = Pattern.compile(" - ");
							String[] vals = splitter.split(artistTitle, 2);
							artist = vals[0];
							title = vals[1];
						}
					}
				}

				Number duration = property(trackDataID + "duration", Number.class);
				float durationValue = duration != null ? duration.floatValue() : 0.0f;
				if (durationValue < 0.0f || Float.isNaN(durationValue))
					durationValue = 0.0f;

				String fileLink = null;
				if (property(trackDataID + "file", Object.class) != null)
					fileLink = property(trackDataID + "file['mp3-128']", String.class);

				result.add(new Track(
						i + 1,
						Objects.toString(artist),
						Objects.toString(title),
						durationValue,
						fileLink)
				);
			}
		}

		return Collections.unmodifiableList(result);
	}


	/**
	 * Gets a link to release artwork thumbnail image.
	 * Returns null if there's not artwork for this release.
	 */
	private String loadArtworkThumbLink() {
		// We get an URL to small 100x100 thumbnail and modify that URL
		// so it will point to a bigger 350x350 image (a standard image used
		// to display artwork on release page). The URL is changed by replacing 
		// format specifier (after '_') from 3 to 2.
		// Also, if URL's protocol is https, we must replace that with http, because
		// as of present JavaFX is unable to load images from bcbits.com (Bandcamp's
		// image storage) using https URLs due to a problem with SSL certificates.
		// Replacement is done at char array level to avoid using slow regex-based methods of String.
		String s = property("artThumbURL", String.class);
		if (s != null) {
			int u = s.lastIndexOf('_');
			char[] chars;

			if (s.startsWith("https")) {
				chars = new char[s.length() - 1];
				s.getChars(5, s.length(), chars, 4);
				for (int i = 0; i < 4; i++)
					chars[i] = s.charAt(i);
				if (u != -1)
					u--;
			}
			else
				chars = s.toCharArray();

			if (u != -1 && u < chars.length - 1 && chars[u+1] == '3')
				chars[u+1] = '2';

			s = String.valueOf(chars);
		}
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
	 * 
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
		LOGGER.log(Level.SEVERE, "Error processing release data: " + uri.get() + " (" + e.getMessage() + ")", e);
	}


	/**
	 * Returns a hash code for this release.
	 */
	@Override
	public int hashCode() {
		return super.hashCode();
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
		// Release caching ensures that there exists at most one instance of Release
		// with a given ID at a time, thus identity comparison is enough (same goes for hash code)
		return super.equals(other);
	}


	/**
	 * Converts this release to its string representation.
	 */
	@Override
	public String toString() {
		String artist_ = artist.get();
		String title_ = title.get();
		Time time_ = time.get();
		LocalDate releaseDate_ = releaseDate.get();
		LocalDate publishDate_ = publishDate.get();
		DownloadType downloadType_ = downloadType.get();
		String tagsString_ = tagsString.get();
		URI uri_ = uri.get();
		return new StringBuilder(artist_).append(" - ").append(title_).append(" (")
				.append(time_).append(") [").append(releaseDate_.equals(LocalDate.MIN) ? "-" : releaseDate_)
				.append(", ").append(publishDate_).append(", ").append(downloadType_)
				.append("] [").append(tagsString_).append("] (").append(uri_).append(")")
				.toString();
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
	 * Returns a total time of this release. The returned instance of time represents
	 * a total duration of all tracks on this release.
	 */
	public Time getTime() {
		return time.get();
	}


	/**
	 * Returns a release time as read-only JavaFX property.
	 */
	public ReadOnlyObjectProperty<Time> timeProperty() {
		return time;
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
	 * Returns a URI of a discography page on this release's parent domain.
	 */
	public URI getDiscographyURI() {
		URI u = uri.get();
		return URI.create(new StringBuilder(u.getScheme())
		.append("://").append(u.getAuthority()).append("/music").toString());
	}


	/**
	 * Returns an unmodifiable set of tags describing this release.
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


	/**
	 * Returns the credits for this release.
	 * If there are no credits provided, returns empty string.
	 */
	public String getCredits() {
		return credits;
	}

}
