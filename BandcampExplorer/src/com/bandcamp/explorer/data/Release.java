package com.bandcamp.explorer.data;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

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
	 * Thread-safe cache for storing and reusing instantiated Release objects.
	 */
	private static final ReleaseCache RELEASE_CACHE = new ReleaseCache();

	/**
	 * Pattern to locate a release data within HTML page. The data is defined in JSON format
	 * and assigned to JavaScript variable.
	 */
	private static final Pattern RELEASE_DATA = Pattern.compile("var TralbumData = \\{.+?\\};", Pattern.DOTALL);

	/**
	 * Pattern for locating tags.
	 */
	private static final Pattern TAG_DATA = Pattern.compile("<a class=\"tag\".+?>.+?(?=(</a>))", Pattern.DOTALL);

	/**
	 * Pattern for splitting the value of "title" JSON property when it contains
	 * both artist and title.
	 */
	private static final Pattern TRACK_TITLE_SPLITTER = Pattern.compile(" - ");

	/**
	 * Pattern capturing some frequently used artist names for V/A-releases (compilations).
	 */
	private static final Pattern VA_ARTIST_PATTERN;
	static {
		StringBuilder regex = new StringBuilder();
		regex.append("various(\\sartists?)?|");                 // "Various", "Various Artist", "Various Artists"
		regex.append(".+\\s(");                                 // Start of expression: <...> <Pattern>
		regex.append("rec(ord)?s|");                            // "Recs", "Records"
		regex.append("rec(ordings|\\.)?|");                     // "Recordings", "Rec.", "Rec"
		regex.append("music|");                                 // "Music"
		regex.append("prod(uctions|\\.)?|");                    // "Productions", "Prod.", "Prod"
		regex.append("(net)?label(\\sgroup)?|");                // "Netlabel", "Label", "Label Group"
		regex.append("sounds|");                                // "Sounds"
		regex.append("comp(ilation|\\.)?|");                    // "Compilation", "Comp.", "Comp" 
		regex.append("sampler");                                // "Sampler"
		regex.append(")\\)?|");                                 // End of expression (with optional ending bracket) 
		regex.append("v(/|-|\\\\|\\.)?a\\.?|");                 // "V/A", "V\A", "VA", "V-A", "V.A.", "VA.", "V.A" 
		regex.append("beatspace(-|\\.).+|.+(\\.|-)beatspace|"); // "beatspace" (starting or ending)
		regex.append("vv\\.?aa\\.?|aa\\.?vv\\.?");              // "VV.AA.", "AA.VV." and dotless variants 

		VA_ARTIST_PATTERN = Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
	}

	/**
	 * Pattern capturing some possible title variations for V/A-releases (compilations).
	 */
	private static final Pattern VA_TITLE_PATTERN = 
			// Matches titles ending with "Split", "Compilation", "Comp.", "Comp" or "Sampler"
			Pattern.compile("(.+\\s)*+(split|comp(ilation|\\.)?|sampler)", Pattern.CASE_INSENSITIVE);

	/**
	 * Pattern for numeric HTML escape codes.
	 */
	private static final Pattern HTML_ESCAPE_CODE = Pattern.compile("&#\\d+;");

	/**
	 * Some mappings for HTML escape chars to use in tags unescaping operation.
	 */
	private static final Map<String, String> HTML_SPECIALS;
	static {
		Map<String, String> specials = new HashMap<>();
		specials.put("&amp;", "&");
		specials.put("&quot;", "\"");
		specials.put("&lt;", "<");
		specials.put("&gt;", ">");
		HTML_SPECIALS = Collections.unmodifiableMap(specials);
	}

	/**
	 * A factory for JavaScript engines allowing to reuse instantiated engines on per-thread basis.
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
	private final ReadOnlyStringProperty tagsString;
	private final ReadOnlyObjectProperty<URI> uri;
	private final String artworkLink;
	private final Set<String> tags;
	private final List<Track> tracks;
	private final String information;
	private final String credits;
	private final String downloadLink;
	private final String parentReleaseLink;



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
	 * Implements a thread-safe caching mechanism for storing and reusing
	 * instantiated Release objects.
	 */
	private static class ReleaseCache {
		/*
		 * The implementation is loosely based on "Memoizer" example from
		 * "Java Concurrency in Practice" book (Section 5.6.) with added support
		 * for weak reference values and automatic cleanup of stale entries.
		 */

		/**
		 * An instance of concurrent hash map used for actual storage
		 */
		private final ConcurrentMap<String, FutureTask<CacheValue>> cache = new ConcurrentHashMap<>(500, 0.75f, 7);

		/**
		 * Reference queue used for tracking down GC'ed releases
		 */
		private final ReferenceQueue<Release> refQueue = new ReferenceQueue<>();


		/**
		 * Instantiates a release cache.
		 */
		ReleaseCache() {
			// Dedicated thread to perform automatic removal of
			// stale entries from the cache
			Thread cleanupThread = new Thread(() -> {
				while (true) {
					try {
						while (true) {
							// Once release is marked by GC and its reference appended to
							// queue, we need to remove corresponding cache entry.
							// Before removal we must check that reference obtained from
							// the queue is actually the same as the one stored in cache
							// at the moment. Thus we ensure that, in case some other
							// thread has concurrently updated the entry to a new value,
							// we won't remove the updated value, but only stale one.
							CacheValue staleValue = (CacheValue)refQueue.remove();
							FutureTask<CacheValue> valueTask = cache.get(staleValue.id);
							if (valueTask != null && valueTask.isDone()) {
								try {
									if (valueTask.get() == staleValue) {
										cache.remove(staleValue.id, valueTask);
										LOGGER.finer("Purged from cache: " + staleValue.id);
									}
								}
								catch (ExecutionException ignored) {}
							}
						}
					}
					catch (InterruptedException whoCares) {
						// We don't need to restore interruption status here because
						// there is no code higher up on the call stack that would be
						// dealing with it.
						// The only thing we want to do in case of possible interruption
						// is to continue our work until program's exit.
					}
				}
			}, "Release Cache Cleanup");
			cleanupThread.setDaemon(true);
			cleanupThread.start();
		}


		/**
		 * Returns the release with the specified identifier.
		 * If Release object with such ID is available in cache, it is returned.
		 * Otherwise, instantiator function is used to instantiate the release.
		 * Newly created object is stored in cache and returned as a result.
		 * 
		 * @param id release identifier
		 * @param instantiator a callback function used to load the release if
		 *        it is not in cache yet; the function must not return null when invoked
		 *        and must not involve direct or indirect call to {@link ReleaseCache#getRelease}
		 * @return a release
		 * @throws ReleaseLoadingException if there was an error during instantiation
		 *         of Release object
		 * @throws RuntimeException wraps any other exception (checked or unchecked)
		 *         that might occur during release instantiation or internal operations
		 *         within a cache
		 */
		Release getRelease(String id, Callable<Release> instantiator) throws ReleaseLoadingException {
			assert id != null;
			assert instantiator != null;

			while (true) {
				FutureTask<CacheValue> task = cache.get(id);
				if (task == null) {
					FutureTask<CacheValue> task1 = new FutureTask<>(
							() -> new CacheValue(id, instantiator.call(), refQueue));
					task = cache.putIfAbsent(id, task1); // make sure that second "check-then-act" sequence is atomic
					if (task == null)
						(task = task1).run();
				}
				try {
					Release release = task.get().get();
					if (release == null)
						// Result being null means that weak reference, previously available in cache,
						// has been cleared by GC and we obtained cleared value before cleanup thread had
						// a chance to remove stale entry.
						// In this case we need to remove the entry (only if it still holds the same completed
						// future task with cleared result) and fall through to the beginning of the loop to
						// repeat the whole sequence once again.
						cache.remove(id, task);
					else
						return release;
				}
				catch (CancellationException | InterruptedException | ExecutionException e) {
					cache.remove(id, task);
					if (e instanceof InterruptedException)
						Thread.currentThread().interrupt();
					else if (e instanceof ExecutionException) {
						Throwable cause = e.getCause();
						if (cause instanceof ReleaseLoadingException)
							throw (ReleaseLoadingException)cause;
					}
					throw new RuntimeException(e);
				}
			}
		}

	}



	/**
	 * Weak reference wrapper for Release object together with its identifier,
	 * representing a cache value.
	 */
	private static class CacheValue extends WeakReference<Release> {
		/**
		 * Release ID
		 */
		final String id;

		/**
		 * Constructs new cache value.
		 * 
		 * @param id release ID
		 * @param release release object
		 * @param refQueue reference queue
		 * @throws NullPointerException if release object is null
		 */
		CacheValue(String id, Release release, ReferenceQueue<Release> refQueue) {
			super(Objects.requireNonNull(release), refQueue);
			this.id = id;
		}

	}



	/**
	 * Returns the release corresponding to the specified Bandcamp URI.
	 * If release object for this URI is available in cache,
	 * then cached version is returned. Otherwise, new release object is
	 * created, using data loaded from the URI, added to cache and then returned.
	 * 
	 * @param uri a URI representing a location to load release from
	 * @return a release object
	 * @throws ReleaseLoadingException if there was an error during instantiation
	 *         of Release object
	 * @throws NullPointerException if uri is null
	 */
	public static Release forURI(URI uri) throws ReleaseLoadingException {
		return forURI(uri, 0);
	}


	/**
	 * Returns the release corresponding to the specified Bandcamp URI.
	 * If release object for this URI is available in cache,
	 * then cached version is returned. Otherwise, new release object is
	 * created, using data loaded from the URI, added to cache and then returned.
	 * 
	 * @param uri a URI representing a location to load release from
	 * @param attempt the number of attempt to load the release; if > 0 then
	 *        this number will be included in a log message; this parameter is
	 *        intended to be used by ReleaseLoader tasks when they perform repeated
	 *        attempts to load the release in case the server was unavailable
	 *        on the first try
	 * @return a release object
	 * @throws ReleaseLoadingException if there was an error during instantiation
	 *         of Release object
	 * @throws NullPointerException if uri is null
	 */
	static Release forURI(URI uri, int attempt) throws ReleaseLoadingException {
		return RELEASE_CACHE.getRelease(createID(uri), () -> {
			String message;
			if (attempt > 0)
				message = new StringBuilder("Loading release: ").append(uri).append(" (")
				.append("attempt #").append(attempt).append(')').toString();
			else
				message = "Loading release: " + uri;
			LOGGER.info(message);
			return new Release(uri);
		});
	}


	/**
	 * Creates unique identifier for release, derived from its URI in a way
	 * that such ID represents release location at Bandcamp website. The resulting ID
	 * includes host and path to a release and omits any other insignificant information,
	 * such as protocol, port, any possible query and fragment part, and character case.
	 * 
	 * @param uri a URI
	 * @return unique ID
	 */
	static String createID(URI uri) {
		return (uri.getHost() + uri.getPath()).toLowerCase(Locale.ROOT);
	}


	/** 
	 * Loads the release using specified URI.
	 * 
	 * @param uri a URI to load release from
	 * @throws ReleaseLoadingException if there was an error during instantiation
	 *         of Release object
	 */
	private Release(URI uri) throws ReleaseLoadingException {
		HttpURLConnection connection;
		try {
			connection = URLConnectionHelper.getConnection(uri);
		}
		catch (Exception e) {
			throw new ReleaseLoadingException(e);
		}

		try (Scanner input = new Scanner(connection.getInputStream(), StandardCharsets.UTF_8.name())) {
			// Finding a variable containing release data and feeding it to JS engine so that we could
			// then handily read any property values we need.
			String releaseData = input.findWithinHorizon(RELEASE_DATA, 0);
			if (releaseData == null)
				throw new ReleaseLoadingException("Release data not found");
			try {
				JS.eval(releaseData);
			}
			catch (ScriptException e) {
				throw new ReleaseLoadingException("Release data is not valid", e);
			}

			this.uri = createObjectProperty(uri);
			artist = createStringProperty(Objects.toString(property("artist")).trim());
			title = createStringProperty(Objects.toString(property("current.title")).trim());
			downloadType = createObjectProperty(readDownloadType());
			releaseDate = createObjectProperty(propertyDate("album_release_date"));
			publishDate = createObjectProperty(propertyDate("current.publish_date"));
			artworkLink = readArtworkLink(input); // this must precede call to readTags()
			tags = readTags(input);
			tagsString = createStringProperty(tags.stream().collect(Collectors.joining(", ")));
			information = Objects.toString(property("current.about"), "");
			credits = Objects.toString(property("current.credits"), "");
			downloadLink = property("freeDownloadPage");
			String domain = domainFromURI(uri);
			String parent = property("album_url");
			parentReleaseLink = parent != null ? domain + parent : null;
			tracks = readTracks(artist.get(), domain, isMultiArtist(artist.get(), title.get(), tags));
			time = createObjectProperty(Time.ofSeconds(
					tracks.stream().collect(Collectors.summingInt(track -> track.time().seconds()))));
		}
		catch (IOException e) {
			int responseCode = 0;
			try {
				responseCode = connection.getResponseCode();
			}
			catch (IOException e1) {
				LOGGER.finer("Unable to get server response code for " + uri);
			}
			throw new ReleaseLoadingException(e, responseCode);
		}
	}


	/**
	 * Creates a read-only JavaFX object property for the given value of 
	 * arbitrary type.
	 * 
	 * @param value initial value to wrap in a property
	 * @return read-only object property
	 */
	private static <T> ReadOnlyObjectProperty<T> createObjectProperty(T value) {
		return new ReadOnlyObjectWrapper<>(value).getReadOnlyProperty();
	}


	/**
	 * Creates a read-only JavaFX string property for the given string value.
	 * 
	 * @param value initial value to wrap in a property
	 * @return read-only string property
	 */
	private static ReadOnlyStringProperty createStringProperty(String value) {
		return new ReadOnlyStringWrapper(value).getReadOnlyProperty();
	}


	/**
	 * Returns the domain URL string from the specified URI (that is, protocol and host).
	 */
	private static String domainFromURI(URI uri) {
		return uri.getScheme() + "://" + uri.getAuthority();
	}


	/**
	 * Determines the download type by reading and interpreting relevant
	 * values in JSON data.
	 */
	private DownloadType readDownloadType() {
		DownloadType result = DownloadType.UNAVAILABLE;
		Number dlPref = property("current.download_pref");
		if (dlPref != null) {
			switch (dlPref.intValue()) {
			case 1:
				result = DownloadType.FREE;
				break;
			case 2:
				Number minPrice = property("current.minimum_price");
				if (minPrice != null && minPrice.doubleValue() > 0.0)
					result = DownloadType.PAID;
				else {
					Number isSet = property("current.is_set_price");
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
	 * Reads all tags for release from the input source and returns them
	 * as unmodifiable set of strings.
	 */
	private static Set<String> readTags(Scanner input) {
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
		return Collections.unmodifiableSet(tags);
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
	 * Checks if the release is (probably) a multi-artist compilation by testing its
	 * artist name, title and tags against some known naming patterns frequently used 
	 * for V/A-releases.
	 * 
	 * @param releaseArtist release artist
	 * @param releaseTitle release title
	 * @param releaseTags release tags
	 * @return true, if the release is probably a multi-artist compilation
	 */
	private static boolean isMultiArtist(String releaseArtist, String releaseTitle, Set<String> releaseTags) {
		return VA_ARTIST_PATTERN.matcher(releaseArtist).matches() 
				|| VA_TITLE_PATTERN.matcher(releaseTitle).matches()
				|| releaseTags.contains("compilation")
				|| releaseTags.contains("various artists")
				|| releaseTags.contains("va")
				|| releaseTags.contains("various")
				|| releaseTags.contains("split")
				|| releaseTags.contains("sampler")
				|| releaseTags.contains("various artist");
	}


	/**
	 * Reads the tracklist information from JSON data and returns tracks as unmodifiable
	 * list of Track objects.
	 * 
	 * @param releaseArtist the artist of this release
	 * @param releaseDomain domain URL string of this release
	 * @param isMultiArtist indicates whether this release is (probably) a multi-artist
	 *        release (compilation)
	 */
	private List<Track> readTracks(String releaseArtist, String releaseDomain, boolean isMultiArtist) {
		List<Track> result = new ArrayList<>();

		Number numTracks = property("trackinfo.length");
		if (numTracks != null && numTracks.intValue() > 0) {
			for (int i = 0; i < numTracks.intValue(); i++)
				result.add(createTrack(releaseArtist, releaseDomain, i, isMultiArtist));
		}

		return Collections.unmodifiableList(result);
	}


	/**
	 * Creates a Track object for the entry with specified index in JSON data. 
	 * 
	 * @param releaseArtist the artist of this release
	 * @param releaseDomain domain URL string of this release
	 * @param trackIndex index corresponding to the current element of trackinfo
	 *        array in JSON data
	 * @param isMultiArtist indicates whether this release is (probably) a multi-artist
	 *        release (compilation); this serves mostly as a heuristic hint for trackinfo 
	 *        parsing code to help with determining correct artist and title for each track
	 * @return a Track instance
	 */
	private Track createTrack(String releaseArtist, String releaseDomain, 
			int trackIndex, boolean isMultiArtist) {
		assert releaseArtist != null;
		assert releaseDomain != null;
		assert trackIndex >= 0;

		String trackDataID = "trackinfo[" + trackIndex + "].";

		// Trying to figure out correct artist and title
		String artistTitle = property(trackDataID + "title");
		String titleLink = property(trackDataID + "title_link");
		String artist = releaseArtist;
		String title = artistTitle;
		if (artistTitle != null && artistTitle.contains(" - ")) {
			if (isMultiArtist) {
				String[] vals = TRACK_TITLE_SPLITTER.split(artistTitle, 2);
				artist = vals[0];
				title = vals[1];
			}
			else {
				// If this release is most likely not a multi-artist release, we are doing
				// additional check by examining the value of "title_link" which, in case the
				// heuristic failed and release is nevertheless a V/A, contains
				// minimized version of title without artist (while "title" property has both
				// artist and title).
				// Note that this approach still won't work if release owner did not add correct
				// multi-artist tags by specifiying artist separately for each track. But in such
				// case there's really nothing more we can do.
				if (titleLink != null) {
					String linkToken = removeTrailingIndex(
							titleLink.substring(titleLink.lastIndexOf('/') + 1)
							.toLowerCase(Locale.ENGLISH));
					String minArtistTitle = removeTrailingIndex(minimizeTitle(artistTitle));
					if (!linkToken.equals(minArtistTitle)) {
						String[] vals = TRACK_TITLE_SPLITTER.split(artistTitle, 2);
						artist = vals[0];
						title = vals[1];
					}
				}
			}
		}

		// Track time
		Number duration = property(trackDataID + "duration");
		float durationValue = duration != null ? duration.floatValue() : 0.0f;
		if (durationValue < 0.0f || Float.isNaN(durationValue))
			durationValue = 0.0f;

		// Link to audio file
		String fileLink = null;
		if (property(trackDataID + "file") != null) {
			fileLink = property(trackDataID + "file['mp3-128']");
			if (fileLink != null) {
				// occasionally, for some unknown reason, file link is read without protocol from JSON data 
				if (!fileLink.startsWith("http:") && !fileLink.startsWith("https:"))
					fileLink = "http:" + fileLink;
				try {
					// additional sanity check
					new URL(fileLink);
				}
				catch (MalformedURLException e) {
					logError(e);
					fileLink = null;
				}
			}
		}

		// Create a track
		return new Track(
				trackIndex + 1,
				Objects.toString(artist).trim(),
				Objects.toString(title).trim(),
				durationValue,
				releaseDomain + titleLink,
				fileLink);
	}


	/**
	 * Performs a "minimization" of track title, replacing and removing unnecessary
	 * characters from it. This method tries to mimic the minimization algorithm used by
	 * Bandcamp for converting their release and track titles into URL paths. 
	 * 
	 * @param title a track title
	 * @return minimized title
	 */
	private static String minimizeTitle(String title) {
		if (title.isEmpty())
			return "-";

		StringBuilder result = new StringBuilder(title.length());
		for (int i = 0; i < title.length(); i++) {
			char c = title.charAt(i);
			if (c >= 'a' && c <= 'z' || isAsciiDigit(c))
				result.append(c);
			else if (c >= 'A' && c <= 'Z')
				result.append((char)(c + 32)); // convert to lowercase
			else if (result.length() == 0 || result.charAt(result.length() - 1) != '-') {
				if (c == '.') {
					// Special case: if '.' is surrounded by digits, it must be skipped,
					// otherwise replaced with '-'
					if ((i == 0 || !isAsciiDigit(title.charAt(i-1)))
							|| (i == title.length() - 1 || !isAsciiDigit(title.charAt(i+1))))
						result.append('-');
				}
				else if (c != '\'')
					result.append('-');
			}
		}

		int len = result.length();
		if (len == 0 || len == 1 && result.charAt(0) == '-')
			return "-";
		else {
			int start = 0;
			if (result.charAt(0) == '-')
				start++;
			if (result.charAt(len-1) == '-')
				len--;
			return result.substring(start, len);
		}
	}


	/**
	 * Removes trailing index from minimized title or link token, if any.
	 * More specifically, removes a sequence of "-N" substrings from the end of string,
	 * where "N" can be any number of characters in 0-9 range, until first non-numeric
	 * character is encountered.
	 * Examples: "some-track-title-10" -> "some-track-title", "title123-1" -> "title123",
	 * "other-title-4-3-16" -> "other-title".
	 * 
	 * If there is an index at the end of link token and this index does not
	 * belong to track title, it must be removed to ensure the correctness of
	 * link token vs minimized title comparison.
	 * Such index sometimes added by Bandcamp to make track URLs unique when there
	 * are different tracks on same domain whose titles yield identical link tokens.
	 * 
	 * Sometimes non-minimized track title contains a series of characters that will
	 * be turned into index-like sequence during title minimization. In that case
	 * such sequence also gets removed despite it being a legitimate part of track title.
	 * Since same is made for link token too, this should not affect the result of 
	 * link token vs minimized title comparison.
	 * 
	 * @param title minimized track title or title link token
	 * @return title without trailing index
	 */
	private static String removeTrailingIndex(String title) {
		if (title.length() < 2)
			return title;

		// Check if title actually contains index sequence;
		// if not, return unaltered title
		int h = title.lastIndexOf('-');
		if (h == -1 || h == title.length() - 1)
			return title;
		for (int i = h + 1; i < title.length(); i++)
			if (!isAsciiDigit(title.charAt(i)))
				return title;

		// Search for first non-numeric character before index sequence
		// and return everyting from the beginning to found position
		for (int i = title.length() - 1; i > 0; i--)
			if (title.charAt(i) == '-' && !isAsciiDigit(title.charAt(i-1)))
				return title.substring(0, i);

		// We reach here if title contains only digits and hyphens.
		int h1 = title.indexOf('-');
		return h1 == 0 ? "-" : title.substring(0, h1);
	}


	/**
	 * Returns true if character is a US-ASCII digit (0-9).
	 * Unlike {@link Character#isDigit(char)} this is faster and
	 * less complicated version that checks for ASCII digits only.
	 */
	private static boolean isAsciiDigit(char c) {
		return c >= '0' && c <= '9';
	}


	/**
	 * Reads a link to release artwork 350x350 image from the input source.
	 * Returns null if there's no artwork for this release or link cannot be located.
	 */
	private String readArtworkLink(Scanner input) {
		String artId = Objects.toString(property("art_id"), "");
		if (artId.isEmpty())
			return null;

		String link = input.findWithinHorizon(
				Pattern.compile("https?://.+/a0*" + artId + "_\\d{1,2}\\.jpe?g"), 0);
		if (link == null)
			return null;

		// Here we modify found artwork URL by replacing format specifier (after '_')
		// to '2' to make it point to a 350x350 image.
		// Replacement is done via StringBuilder to avoid using regex-based String.replace
		return new StringBuilder(link)
		.replace(link.lastIndexOf('_') + 1, link.lastIndexOf('.'), "2")
		.toString();
	}


	/**
	 * Helper method for reading dates from JSON data.
	 * Returns LocalDate.MIN if date is invalid or absent.
	 */
	private LocalDate propertyDate(String name) {
		LocalDate result = LocalDate.MIN;
		String dateStr = property(name);
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
	 */
	@SuppressWarnings("unchecked")
	private <T> T property(String name) {
		try {
			return (T)JS.eval("TralbumData." + name);
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
		LOGGER.log(Level.WARNING, "Error processing release data: " + uri.get() + " (" + e.getMessage() + ")", e);
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
	 * URIs contain same host and path (ignoring protocols, ports, any possible query
	 * and fragment parts, and character case).
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
	public String artist() {
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
	public String title() {
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
	public DownloadType downloadType() {
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
	public Time time() {
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
	public LocalDate releaseDate() {
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
	public LocalDate publishDate() {
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
	public URI uri() {
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
	public URI discographyURI() {
		return URI.create(domainFromURI(uri.get()) + "/music");
	}


	/**
	 * Returns an unmodifiable set of tags describing this release.
	 */
	public Set<String> tags() {
		return tags;
	}


	/**
	 * Returns a set of tags as string where individual tags
	 * are separated by comma and space.
	 */
	public String tagsString() {
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
	public List<Track> tracks() {
		return tracks;
	}


	/**
	 * Returns a URL string of album artwork 350x350 image.
	 * If this release has no artwork, returns null.
	 */
	public String artworkLink() {
		return artworkLink;
	}


	/**
	 * Returns an information about this release.
	 * If there's no information provided, returns empty string.
	 */
	public String information() {
		return information;
	}


	/**
	 * Returns the credits for this release.
	 * If there are no credits provided, returns empty string.
	 */
	public String credits() {
		return credits;
	}


	/**
	 * Returns the download link for this release, if available.
	 * If there's no download link, returns null.
	 * Usually download link is available for releases whose download type is {@link DownloadType#FREE}
	 * or {@link DownloadType#NAME_YOUR_PRICE} where users are not required to enter their
	 * email for receiving download link.
	 */
	public String downloadLink() {
		return downloadLink;
	}


	/**
	 * Returns a URL string of Bandcamp release that contains this release.
	 * The link is available only if this release is a single track which is a
	 * part of album, otherwise this method returns null.
	 */
	public String parentReleaseLink() {
		return parentReleaseLink;
	}

}
