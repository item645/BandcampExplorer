package com.bandcamp.explorer.data;

import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.DOTALL;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
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
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
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
	 * Pattern to locate a release data within HTML page. The data is defined in JSON format
	 * and assigned to TralbumData JavaScript variable.
	 */
	private static final Pattern RELEASE_DATA_PATTERN = Pattern.compile("var TralbumData = \\{.+?\\};", DOTALL);

	/**
	 * Pattern for locating the content of pagedata element.
	 */
	private static final Pattern PAGE_DATA_PATTERN = 
			Pattern.compile("<div id=\"pagedata\"\\sdata-blob=\"\\{.+?\">", DOTALL);

	/**
	 * Pattern for locating currency code defined in pagedata element.
	 */
	private static final Pattern PAGE_DATA_CURRENCY_PATTERN = 
			Pattern.compile("currency&quot;:&quot;([a-zA-Z]{3})&quot;");

	/**
	 * Pattern to locate a currency data within HTML page. The data is defined in JSON format
	 * and assigned to CurrencyData JavaScript variable.
	 */
	private static final Pattern CURRENCY_DATA_PATTERN = Pattern.compile("var CurrencyData = \\{.+?\\};", DOTALL);

	/**
	 * Pattern for locating currency setting for release in currency data JSON. 
	 */
	private static final Pattern CURRENCY_CODE_SETTING_PATTERN = Pattern.compile("\"setting\":\"([a-zA-Z]{3})\"");

	/**
	 * Pattern for locating tags.
	 */
	private static final Pattern TAG_DATA_PATTERN = Pattern.compile("<a class=\"tag\".+?>.+?(?=(</a>))", DOTALL);

	/**
	 * Pattern for matching and splitting the value of "title" JSON property when it contains
	 * both artist and title.
	 * The pattern checks for hyphens and dashes with different Unicode codes that can possibly 
	 * be used as artist/title separators.
	 */
	private static final Pattern ARTIST_TITLE_SEPARATOR;
	static {
		String separator = "(-{1,2}|[\u2010-\u2015\u2212])";
		ARTIST_TITLE_SEPARATOR = Pattern.compile(String.format("\\s+%s\\s*|\\s*%<s\\s+", separator));
	}

	/**
	 * Pattern capturing some frequently used artist names for V/A-releases (compilations).
	 */
	private static final Pattern VA_ARTIST_PATTERN;
	static {
		StringBuilder regex = new StringBuilder();
		// "Various", "Various Artist", "Various Artists"
		regex.append("various(\\sartists?)?|");
		// Start of expression: <...> <Pattern>
		regex.append(".+\\s(");
		// "Recs", "Records"
		regex.append("rec(ord)?s|");
		// "Recordings", "Rec.", "Rec"
		regex.append("rec(ordings|\\.)?|");
		// "Music"
		regex.append("music|");
		// "Productions", "Prod.", "Prod"
		regex.append("prod(uctions|\\.)?|");
		// "Netlabel", "Label", "Label Group"
		regex.append("(net)?label(\\sgroup)?|");
		// "Sounds"
		regex.append("sounds|");
		// "Compilation", "Comp.", "Comp"
		regex.append("comp(ilation|\\.)?|");
		// "Sampler"
		regex.append("sampler");
		// End of expression (with optional ending bracket)
		regex.append(")\\)?|");
		// "V/A", "V\A", "VA", "V-A", "V.A.", "VA.", "V.A" (either as full artist name or a part of it)
		regex.append(String.format("%s|\\(?%<s\\)?(:|\\s).*|.*\\s\\(?%<s\\)?|", "v(/|-|\\\\|\\.)?a\\.?"));
		// "beatspace" (starting or ending)
		regex.append("beatspace(-|\\.).+|.+(\\.|-)beatspace|");
		// "VV.AA" with optional dots
		regex.append("v\\.?v\\.?a\\.?a\\.?|");
		// "AA.VV" with optional dots
		regex.append("a\\.?a\\.?v\\.?v\\.?");

		VA_ARTIST_PATTERN = Pattern.compile(regex.toString(), CASE_INSENSITIVE);
	}

	/**
	 * Pattern capturing some possible title variations for V/A-releases (compilations).
	 */
	private static final Pattern VA_TITLE_PATTERN = 
			// Matches titles ending with "Split", "Compilation", "Comp.", "Comp" or "Sampler"
			Pattern.compile("(.+\\s)*+(split|comp(ilation|\\.)?|sampler)", CASE_INSENSITIVE);

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
	 * Thread-safe cache for storing and reusing instantiated Release objects.
	 */
	private static final ReleaseCache releaseCache = new ReleaseCache();

	/**
	 * Exchange rate data container.
	 * Used for converting release price to USD.
	 */
	private static final ExchangeRate exchangeRate = new ExchangeRate();


	private final ReadOnlyStringProperty artist;
	private final ReadOnlyStringProperty title;
	private final ReadOnlyObjectProperty<DownloadType> downloadType;
	private final ReadOnlyObjectProperty<Price> price;
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
										LOGGER.finer(() -> "Purged from cache: " + staleValue.id);
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
	 * A helper class wrapping the functionality of built-in JavaScript interpreter.
	 * It is used for processing JSON data obtained from release page source.
	 * 
	 * Instances of this class are not thread safe and must be confined to a single
	 * thread or (in case of multithreaded access) used with proper synchronization.
	 */
	private static class JavaScriptHelper {

		private final ScriptEngine JS = new ScriptEngineManager().getEngineByName("JavaScript");
		private Consumer<Exception> errorHandler = e -> {};


		/**
		 * Executes the specified script.
		 * 
		 * @param script a string containing the script
		 * @throws ScriptException if there was an error during script execution
		 */
		void execute(String script) throws ScriptException {
			JS.eval(script);
		}


		/**
		 * Executes passed scripts in the order they were specified in vararg param.
		 * 
		 * @param scripts strings, each one containing script to be executed 
		 * @throws ScriptException if there was an error during execution of any script
		 */
		void execute(String... scripts) throws ScriptException {
			for (String script : scripts)
				execute(script);
		}


		/**
		 * Executes script and returns resulting value converted to a target type.
		 * Returns null if there was an error during script execution or value convertion.  
		 * 
		 * @param script a string containing the script
		 * @return resulting value
		 */
		@SuppressWarnings("unchecked")
		<T> T evaluate(String script) {
			try {
				return (T)JS.eval(script);
			} 
			catch (ScriptException | ClassCastException e) {
				errorHandler.accept(e);
				return null;
			}
		}


		/**
		 * Executes script to obtain a date value in RFC-1123 string representation
		 * and converts the result to LocalDate.
		 * If obtained date value is null or has incompatible type (non-string),
		 * returns the default value.
		 * If string representation cannot be interpreted as date, this method   
		 * returns LocalDate.MIN.
		 * 
		 * @param script a string containing the script
		 * @param defaultVal default value to return if resulting date cannot be obtained
		 * @return a LocalDate instance containing the date value
		 */
		LocalDate evaluateDate(String script, LocalDate defaultVal) {
			String dateStr = evaluate(script);
			if (dateStr == null)
				return defaultVal;
			try {
				return LocalDate.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME);
			}
			catch (DateTimeParseException e) {
				errorHandler.accept(e);
				return LocalDate.MIN;
			}
		}


		/**
		 * Sets up a hanlder to process possible exceptions that can occur during
		 * script evaluations.
		 * By default error handler is not set and such handling is not performed,
		 * with script evaluation errors silently ignored.
		 * 
		 * @param errorHandler a callback function that handles the exception
		 */
		void setErrorHandler(Consumer<Exception> errorHandler) {
			assert errorHandler != null;
			this.errorHandler = errorHandler;
		}

	}



	/**
	 * Encapsulates raw release data values obtained from JSON source.
	 */
	private static class ReleaseData {

		private static final String DATA_VARIABLE_PATH = "TralbumData.";
		private static final JavaScriptHelper JS = new JavaScriptHelper();

		final String artist;
		final String title;
		final String about;
		final String credits;
		final String freeDownloadPage;
		final String albumURL;
		final String currency;
		final Number artId;
		final Number downloadPref;
		final Number minPrice;
		final Number setPrice;
		final Number isSetPrice;
		final LocalDate releaseDate;
		final LocalDate publishDate;
		final TrackInfo[] tracks;


		/**
		 * Performs the processing of input release data in JSON format and instantiates
		 * a ReleaseData object containing necessary values obtained from the source.
		 * 
		 * A single instance of JavaScript engine is used to process the data, with
		 * multiple threads access serialized by class intrinsic lock.
		 * Due to that reason processing does not involve any complex manipulation of
		 * source input other than reading necessary raw values and converting them to
		 * appropriate built-in data types. The goal is to execute the body of this
		 * constructor as quickly as possible to minimize thread contention.
		 * 
		 * @param jsonData source release data in JSON format
		 * @param releaseURI a URI of release page
		 * @throws IllegalArgumentException if source data is not valid and its processing
		 *         has resulted in unrecoverable error
		 */
		ReleaseData(String jsonData, URI releaseURI) {
			assert jsonData != null;
			assert releaseURI != null;

			synchronized (ReleaseData.class) {
				try {
					JS.execute(jsonData);
				}
				catch (ScriptException e) {
					throw new IllegalArgumentException(e);
				}
				JS.setErrorHandler(exception -> logReleaseDataError(exception, releaseURI));

				artist           = read("artist");
				title            = read("current.title");
				about            = read("current.about");
				credits          = read("current.credits");
				freeDownloadPage = read("freeDownloadPage");
				albumURL         = read("album_url");
				artId            = read("art_id");
				downloadPref     = read("current.download_pref");
				minPrice         = read("current.minimum_price");
				setPrice         = read("current.set_price");
				isSetPrice       = read("current.is_set_price");
				releaseDate      = readDate("album_release_date", null);
				publishDate      = readDate("current.publish_date", LocalDate.MIN);

				currency = hasPackages() ? read("packages[0].currency") : null;

				tracks = readTracks();
			}
		}


		/**
		 * Returns true if JSON contains non-empty packages array. 
		 */
		private boolean hasPackages() {
			return read("packages") != null && asPrimitiveInt(read("packages.length")) > 0;
		}


		/**
		 * Reads tracks data from trackinfo array in JSON.
		 */
		private TrackInfo[] readTracks() {
			int length = asPrimitiveInt(read("trackinfo.length"));
			TrackInfo[] tracks = new TrackInfo[length];
			for (int index = 0; index < length; index++) {
				String trackDataPath = "trackinfo[" + index + "].";
				tracks[index] = new TrackInfo(
						index, 
						read(trackDataPath + "title"),
						read(trackDataPath + "title_link"),
						read(trackDataPath + "duration"),
						read(trackDataPath + "file") != null
							? read(trackDataPath + "file['mp3-128']") : null
						);
			}
			return tracks;
		}


		/**
		 * Reads the value of named JSON property and converts it to a target type.
		 * Returns null if there was an error during attempt to read or convert a value.  
		 * 
		 * @param name property name
		 * @return a value
		 */
		private <T> T read(String name) {
			return JS.evaluate(DATA_VARIABLE_PATH + name);
		}


		/**
		 * Reads a date from the specified JSON property.
		 * If property value is absent, null, or has incompatible type (non-string),
		 * returns the default value.
		 * If date value obtained from JSON in string form cannot be interpreted
		 * as date, this method returns LocalDate.MIN.
		 * 
		 * @param name a name of JSON property to read from
		 * @param defaultVal default value to return if property value is absent
		 * @return a LocalDate instance containing the date value
		 */
		private LocalDate readDate(String name, LocalDate defaultVal) {
			return JS.evaluateDate(DATA_VARIABLE_PATH + name, defaultVal);
		}

	}



	/**
	 * Simple struct encapsulating raw data values for individual track.
	 */
	private static class TrackInfo {
		final int index;
		final String title;
		final String titleLink;
		final Number duration;
		final String fileLink;

		TrackInfo(int index, String title, String titleLink, Number duration, String fileLink) {
			this.index = index;
			this.title = title;
			this.titleLink = titleLink;
			this.duration = duration;
			this.fileLink = fileLink;
		}
	}



	/**
	 * Contains exchange rate data obtained from release page source on first release load
	 * and used for converting release price to USD.
	 * The data is initialized only once and then reused.
	 */
	private static class ExchangeRate {

		private final Map<String, BigDecimal> rates = new HashMap<>();
		private volatile boolean initialized;


		/**
		 * Returns true if exchange rate data has been already initialized.
		 */
		boolean isInitialized() {
			return initialized;
		}


		/**
		 * Initializes exchange rates using raw source data in JSON format. 
		 * 
		 * @param jsonData source exchange rate data in JSON format
		 * @param releaseURI a URI of release
		 * @throws IllegalArgumentException if source data is not valid and its processing
		 *         has resulted in unrecoverable error
		 */
		synchronized void initialize(String jsonData, URI releaseURI) {
			if (!initialized) {
				doInitialize(jsonData, releaseURI);
				initialized = true;
				LOGGER.fine("Loaded exchange rates for " + rates.size() + " currencies");
			}
		}


		/**
		 * Performs the initialization of exchange rates.
		 * 
		 * @param jsonData source exchange rate data in JSON format
		 * @param releaseURI a URI of release
		 * @throws IllegalArgumentException if source data is not valid and its processing
		 *         has resulted in unrecoverable error
		 */
		private void doInitialize(String jsonData, URI releaseURI) {
			assert jsonData != null;
			assert releaseURI != null;

			JavaScriptHelper JS = new JavaScriptHelper();
			JS.setErrorHandler(exception -> logDataError(exception, releaseURI));

			try {
				JS.execute(jsonData, "var codes = Object.keys(CurrencyData.rates)");
				int codesLength = JS.<Number>evaluate("codes.length").intValue();
				for (int i = 0; i < codesLength; i++) {
					String code = JS.evaluate("codes[" + i + "]");
					if (code != null) {						
						Number rate = JS.evaluate("CurrencyData.rates['" + code + "']");
						BigDecimal rateValue = BigDecimal.ONE;
						if (rate != null)
							rateValue = BigDecimal.valueOf(rate.doubleValue());
						else
							LOGGER.warning("Cannot obtain exchage rate value for currency code: " + code);
						rates.put(code.toUpperCase(Locale.ROOT), rateValue);
					}
				}
			}
			catch (ScriptException | NullPointerException e) {
				throw new IllegalArgumentException(e);
			}
		}


		/**
		 * Returns an Optional with the exchange rate value for a given currency code.
		 * If there is no rate value for the specified code, returns empty Optional.
		 * 
		 * @param currencyCode currency code
		 */
		Optional<BigDecimal> get(String currencyCode) {
			return Optional.ofNullable(rates.get(currencyCode));
		}


		/**
		 * Logs errors encountered during the processing of exchange rate data.
		 * 
		 * @param e relevant exception
		 * @param releaseURI release URI
		 */
		private static void logDataError(Exception e, URI releaseURI) {
			LOGGER.log(Level.WARNING, "Error processing exchange rate data for release: " 
					+ releaseURI + " (" + e.getMessage() + ")", e);
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
		return forURI(uri, 0, createID(uri));
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
	 * @param releaseID release identifier; must be derived from the URI
	 * @return a release object
	 * @throws ReleaseLoadingException if there was an error during instantiation
	 *         of Release object
	 * @throws NullPointerException if uri is null
	 */
	static Release forURI(URI uri, int attempt, String releaseID) throws ReleaseLoadingException {
		return releaseCache.getRelease(releaseID, () -> {
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
			String pageDataCurrencyCode = readPageDataCurrencyCode(input);
			ReleaseData releaseData = readReleaseData(input, uri);

			String currencyDataJSON = null;
			// One-time initialization of exchange rates using currency data obtained
			// from the page.
			// Exchange rate data is the same for all release pages.
			if (!exchangeRate.isInitialized()) {
				currencyDataJSON = readCurrencyDataJSON(input);
				try {
					exchangeRate.initialize(currencyDataJSON, uri);
				}
				catch (IllegalArgumentException e) {
					throw new ReleaseLoadingException("Failed to process exchange rate data", e);
				}
			}

			this.uri = createObjectProperty(uri);

			artist = createStringProperty(Objects.toString(releaseData.artist).trim());
			title = createStringProperty(Objects.toString(releaseData.title).trim());

			downloadType = createObjectProperty(readDownloadType(releaseData));

			String releaseCurrencyCode = readCurrencyCode(input, releaseData, currencyDataJSON,
					pageDataCurrencyCode, uri);
			price = createObjectProperty(readPrice(releaseData, downloadType.get(), releaseCurrencyCode));

			releaseDate = createObjectProperty(releaseData.releaseDate);
			publishDate = createObjectProperty(releaseData.publishDate);

			artworkLink = readArtworkLink(releaseData, input); // this must precede call to readTags()

			tags = readTags(input);
			tagsString = createStringProperty(tags.stream().collect(Collectors.joining(", ")));

			information = Objects.toString(releaseData.about, "").trim();
			credits = Objects.toString(releaseData.credits, "").trim();

			downloadLink = releaseData.freeDownloadPage;
			String domain = domainFromURI(uri);
			parentReleaseLink = releaseData.albumURL != null ? domain + releaseData.albumURL : null;

			String releaseArtist = artist.get();
			boolean isMultiArtist = isMultiArtist(releaseArtist, title.get(), tags);
			List<Track> trackList = new ArrayList<>();
			for (TrackInfo trackInfo : releaseData.tracks)
				trackList.add(createTrack(trackInfo, releaseArtist, domain, isMultiArtist, uri));
			tracks = Collections.unmodifiableList(trackList);

			time = createObjectProperty(Time.ofSeconds(
					trackList.stream().collect(Collectors.summingInt(track -> track.time().seconds()))));
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
	 * Logs errors encountered during the processing of release data.
	 * 
	 * @param e relevant exception
	 * @param uri release URI
	 */
	private static void logReleaseDataError(Exception e, URI uri) {
		LOGGER.log(Level.WARNING, "Error processing release data: " + uri + " (" + e.getMessage() + ")", e);
	}


	/**
	 * Performs reading and parsing of release data from JSON data cotained in page source.
	 * 
	 * @param input input source
	 * @param releaseURI a URI of this release
	 * @return ReleaseData instance, containing necessary data obtained from JSON
	 * @throws ReleaseLoadingException if source JSON data was not found or if there was
	 *         an unrecoverable error when reading and interpreting the data  
	 */
	private static ReleaseData readReleaseData(Scanner input, URI releaseURI) throws ReleaseLoadingException {
		String releaseJSON = input.findWithinHorizon(RELEASE_DATA_PATTERN, 0);
		if (releaseJSON == null)
			throw new ReleaseLoadingException("Release data not found");

		ReleaseData releaseData;
		try {
			releaseData = new ReleaseData(releaseJSON, releaseURI);
		}
		catch (IllegalArgumentException e) {
			throw new ReleaseLoadingException("Release data is not valid", e);
		}
		return releaseData;
	}


	/**
	 * Reads currency code from the content of pagedata element.
	 * 
	 * @param input input source
	 * @return currency code; null, if code (or pagedata element) was not found
	 */
	private static String readPageDataCurrencyCode(Scanner input) {
		String currency = null;
		String pageData = input.findWithinHorizon(PAGE_DATA_PATTERN, 0);
		if (pageData != null) {
			Matcher matcher = PAGE_DATA_CURRENCY_PATTERN.matcher(pageData);
			if (matcher.find()) {
				currency = matcher.group(1);
				if (currency != null)
					currency = currency.toUpperCase(Locale.ROOT);
			}
		}
		return currency;
	}


	/**
	 * Reads currency data in JSON format from input source.
	 * 
	 * @param input input source
	 * @return currency data in JSON format
	 * @throws ReleaseLoadingException if currency data was not found
	 */
	private static String readCurrencyDataJSON(Scanner input) throws ReleaseLoadingException {
		String currencyDataJSON = input.findWithinHorizon(CURRENCY_DATA_PATTERN, 0);
		if (currencyDataJSON == null)
			throw new ReleaseLoadingException("Currency data not found");
		return currencyDataJSON;
	}


	/**
	 * Determines the currency code for release using different sources.
	 * If currency code was defined in pagedata element, this code is used. Otherwise, currency
	 * code is taken from packages list in release data or (if there are no packages) from
	 * currency data JSON
	 * 
	 * @param input input source
	 * @param releaseData raw release data
	 * @param currencyDataJSON currency data in JSON format
	 * @param pageDataCurrencyCode currency code obtained from pagedata element
	 * @param releaseURI a URI of this release
	 * @return currency code for the release or "USD", if currency code was not found
	 * @throws ReleaseLoadingException if currency data JSON parameter was null and on attempt
	 *         to read the data from input source it was not found
	 */
	private static String readCurrencyCode(Scanner input, ReleaseData releaseData, String currencyDataJSON,
			String pageDataCurrencyCode, URI releaseURI) throws ReleaseLoadingException {
		String code = pageDataCurrencyCode;
		if (code == null) {
			code = Objects.toString(releaseData.currency, "");
			if (code.isEmpty()) {
				if (currencyDataJSON == null)
					currencyDataJSON = readCurrencyDataJSON(input);
				Matcher matcher = CURRENCY_CODE_SETTING_PATTERN.matcher(currencyDataJSON);
				code = matcher.find() ? Objects.toString(matcher.group(1), "") : "";
			}

			if (code.isEmpty()) {
				LOGGER.warning("Cannot determine currency setting for release: " + releaseURI);
				code = "USD";
			}
			else {
				code = code.toUpperCase(Locale.ROOT);
			}
		}
		return code;
	}


	/**
	 * Determines the download type by interpreting relevant values in
	 * raw release data.
	 * 
	 * @param releaseData raw release data
	 */
	private static DownloadType readDownloadType(ReleaseData releaseData) {
		switch (asPrimitiveInt(releaseData.downloadPref)) {
		case 1:
			return DownloadType.FREE;
		case 2:
			if (asPrimitiveDouble(releaseData.minPrice) > 0.0)
				return DownloadType.PAID;
			else
				return asPrimitiveInt(releaseData.isSetPrice) == 1 
					? DownloadType.PAID : DownloadType.NAME_YOUR_PRICE;
		default:
			return DownloadType.UNAVAILABLE;
		}
	}


	/**
	 * Reads release price from the release data in original currency and converts it to USD
	 * using exchange rate provided by Bandcamp.
	 * For download types other than {@link DownloadType#PAID} the price is $0.00.
	 * 
	 * @param releaseData raw release data
	 * @param downloadType release download type
	 * @param currencyCode a code for currency that is used for release
	 * @return release price in USD
	 */
	private static Price readPrice(ReleaseData releaseData, DownloadType downloadType, String currencyCode) {
		Price price = Price.ZERO;
		if (downloadType == DownloadType.PAID) {
			double priceValue = asPrimitiveInt(releaseData.isSetPrice) == 1
					? asPrimitiveDouble(releaseData.setPrice)
					: asPrimitiveDouble(releaseData.minPrice);
			if (!Double.isFinite(priceValue) || priceValue < 0.0)
				priceValue = 0.0;

			BigDecimal rate = exchangeRate.get(currencyCode).orElseGet(() -> {
				LOGGER.warning("Cannot obtain exchange rate value for currency code: " + currencyCode);
				return BigDecimal.ONE;
			});

			price = Price.of(BigDecimal.valueOf(priceValue).multiply(rate));
		}
		return price;
	}


	/**
	 * Converts given number to int, returning 0 if number is null.
	 * 
	 * @param number a number
	 * @return value as int
	 */
	private static int asPrimitiveInt(Number number) {
		return number != null ? number.intValue() : 0;
	}


	/**
	 * Converts given number to double, returning 0.0 if number is null.
	 * 
	 * @param number a number
	 * @return value as double
	 */
	private static double asPrimitiveDouble(Number number) {
		return number != null ? number.doubleValue() : 0.0;
	}


	/**
	 * Converts given number to float, returning 0.0f if number is null.
	 * 
	 * @param number a number
	 * @return value as float
	 */
	private static float asPrimitiveFloat(Number number) {
		return number != null ? number.floatValue() : 0.0f;
	}


	/**
	 * Reads all tags for release from the input source and returns them
	 * as unmodifiable set of strings.
	 * 
	 * @param input input source
	 */
	private static Set<String> readTags(Scanner input) {
		// Tags are not included in JSON data so we need to fetch them from raw HTML.
		// Resetting to 0 position is not required here because tags are located
		// after TralbumData in HTML source.
		// While it ain't a good idea to extract data from HTML like this, using some
		// third party soup library for such a simple one-time task would be an overkill.
		Set<String> tags = new LinkedHashSet<>();
		String tagData = null;
		while ((tagData = input.findWithinHorizon(TAG_DATA_PATTERN, 0)) != null) {
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
					// This won't work for anything beyond 0xFFFF, but still suitable
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
	 * Creates a Track object for the specified raw data values. 
	 * 
	 * @param trackInfo a structure containing raw data values for the track
	 * @param releaseArtist the artist of this release
	 * @param releaseDomain domain URL string of this release
	 * @param isMultiArtist indicates whether this release is (probably) a multi-artist
	 *        release (compilation); this serves mostly as a heuristic hint for trackinfo 
	 *        processing code to help with determining correct artist and title for the track
	 * @param releaseURI a URI of this release
	 * @return a Track instance
	 */
	private static Track createTrack(TrackInfo trackInfo, String releaseArtist,
			String releaseDomain, boolean isMultiArtist, URI releaseURI) {
		assert trackInfo != null;
		assert releaseArtist != null;
		assert releaseDomain != null;
		assert releaseURI != null;

		// Trying to figure out correct artist and title
		String artistTitle = trackInfo.title;
		String titleLink = trackInfo.titleLink;
		String artist = releaseArtist;
		String title = artistTitle;
		if (artistTitle != null && ARTIST_TITLE_SEPARATOR.matcher(artistTitle).find()) {
			String[] values = ARTIST_TITLE_SEPARATOR.split(artistTitle, 2);
			if (isMultiArtist) {
				artist = values[0];
				title = values[1];
			}
			else {
				// Additional checks in case the heuristic hint is wrong.
				// Split to artist/title if either is true:
				// a) the part before separator is also a part of release artist (in that case it is
				//    likely that we deal with a split release and release artist is something like
				//    "Artist 1 & Artist 2")
				// b) the part before separator gets completely erased by minimization, which probably
				//    means that artist name consists solely of illegal characters (this approach may
				//    produce wrong results on some specific single-artist releases, but this is 
				//    expected to happen very rarely)
				if (containsIgnoreCase(releaseArtist, values[0]) || minimizeTitle(values[0]).equals("-")) {
					artist = values[0];
					title = values[1];
				}
				else if (titleLink != null) {
					// Examine the value of "title_link" which, in case the heuristic failed and
					// release is nevertheless a V/A, contains minimized version of title without
					// artist (while "title" property has both artist and title).
					// Note that this approach still won't work if release owner did not add correct
					// multi-artist tags by specifiying artist separately for each track. But in such
					// case there's really nothing more we can do.
					String linkToken = removeTrailingIndex(
							titleLink.substring(titleLink.lastIndexOf('/') + 1)
							.toLowerCase(Locale.ENGLISH));
					String minArtistTitle = removeTrailingIndex(minimizeTitle(artistTitle));
					if (!linkToken.equals(minArtistTitle)) {
						artist = values[0];
						title = values[1];
					}
				}
			}
		}

		// Track time
		float duration = asPrimitiveFloat(trackInfo.duration);
		if (!Float.isFinite(duration) || duration < 0.0f)
			duration = 0.0f;

		// Link to audio file
		String fileLink = trackInfo.fileLink;
		if (fileLink != null) {
			fileLink = fileLink.toLowerCase(Locale.ROOT);
			// Enforcing the use of HTTP for file link in case the protocol is HTTPS or absent
			// to ensure the playback is working for Java versions below 8u72.
			// See: https://bugs.openjdk.java.net/browse/JDK-8091132
			if (!fileLink.startsWith("http:")) {
				if (fileLink.startsWith("https:"))
					fileLink = new StringBuilder(fileLink).replace(0, 5, "http").toString();					
				else if (fileLink.startsWith("//"))
					fileLink = "http:" + fileLink;
				else 
					fileLink = "http://" + fileLink;
			}
			try {
				// Additional sanity check
				new URL(fileLink);
			}
			catch (MalformedURLException e) {
				logReleaseDataError(e, releaseURI);
				fileLink = null;
			}
		}

		// Create a track
		return new Track(
				trackInfo.index + 1,
				Objects.toString(artist).trim(),
				Objects.toString(title).trim(),
				duration,
				titleLink != null ? releaseDomain + titleLink : null,
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
		// and return everything from the beginning to found position
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
	 * Checks if given string contains another, ignoring case differences.
	 * 
	 * @param s1 string to search in
	 * @param s2 string to search for
	 * @return true, if s1 contains s2 regardless of the case
	 */
	private static boolean containsIgnoreCase(String s1, String s2) {
		return s1.toLowerCase(Locale.ENGLISH).contains(s2.toLowerCase(Locale.ENGLISH));
	}


	/**
	 * Reads a link to release artwork 350x350 image from the input source.
	 * Returns null if there's no artwork for this release or link cannot be located.
	 * 
	 * @param releaseData raw release data
	 * @param input input source
	 */
	private static String readArtworkLink(ReleaseData releaseData, Scanner input) {
		String artId = Objects.toString(releaseData.artId, "");
		if (artId.isEmpty())
			return null;

		String link = input.findWithinHorizon(
				Pattern.compile("https?://.+/a0*" + artId + "_\\d{1,2}\\.jpe?g", CASE_INSENSITIVE), 0);
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
		Price price_ = price.get();
		String tagsString_ = tagsString.get();
		URI uri_ = uri.get();

		return new StringBuilder()
			.append(artist_).append(" - ").append(title_)
			.append(" (").append(time_).append(") [")
			.append(Objects.toString(releaseDate_, "-"))
			.append(", ").append(publishDate_)
			.append(", ").append(downloadType_)
			.append(", ").append(price_)
			.append("] [").append(tagsString_).append("] (")
			.append(uri_).append(")")
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
	 * Returns a price of this release in USD.
	 * If original release price is in different currency, then it is converted
	 * to USD using exchange rate provided by Bandcamp. The price value is always
	 * scaled to 2 decimal places using "half up" rounding mode.
	 * For download types other than {@link DownloadType#PAID} the price is $0.00.
	 */
	public Price price() {
		return price.get();
	}


	/**
	 * Returns release price as read-only JavaFX property.
	 */
	public ReadOnlyObjectProperty<Price> priceProperty() {
		return price;
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
	 * Returns an Optional with the release date.
	 * On some releases the date is not specified, in that case empty Optional is returned.
	 */
	public Optional<LocalDate> releaseDate() {
		return Optional.ofNullable(releaseDate.get());
	}


	/**
	 * Returns a release date as read-only JavaFX property.
	 * On some releases the date is not specified, in that case property wrapper
	 * returns null on attempt to get the value.
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
	 * Returns an Optional with the URL string of album artwork 350x350 image.
	 * If this release has no artwork, returns empty Optional.
	 */
	public Optional<String> artworkLink() {
		return Optional.ofNullable(artworkLink);
	}


	/**
	 * Returns an Optional with the information about this release.
	 * If there's no information provided, returns empty Optional.
	 */
	public Optional<String> information() {
		return information.isEmpty() ? Optional.empty() : Optional.of(information);
	}


	/**
	 * Returns an Optional with the credits for this release.
	 * If there are no credits provided, returns empty Optional.
	 */
	public Optional<String> credits() {
		return credits.isEmpty() ? Optional.empty() : Optional.of(credits);
	}


	/**
	 * Returns an Optional with the download link for this release, if available.
	 * If there's no download link, returns empty Optional.
	 * Usually download link is available for releases whose download type is {@link DownloadType#FREE}
	 * or {@link DownloadType#NAME_YOUR_PRICE} where users are not required to enter their
	 * email for receiving the link.
	 */
	public Optional<String> downloadLink() {
		return Optional.ofNullable(downloadLink);
	}


	/**
	 * Returns an Optional with the URL string of Bandcamp release that contains
	 * this release. The link is available only if this release is a single track which is a
	 * part of album, otherwise this method returns empty Optional.
	 */
	public Optional<String> parentReleaseLink() {
		return Optional.ofNullable(parentReleaseLink);
	}

}
