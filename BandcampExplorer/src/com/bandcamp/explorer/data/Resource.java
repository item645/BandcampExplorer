package com.bandcamp.explorer.data;

import static com.bandcamp.explorer.data.URLConnectionBuilder.HttpURLConnectionBuilder.Method.POST;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.bandcamp.explorer.util.ExceptionUnchecker;

/**
 * Represents a resource for obtaining links to Bandcamp releases.
 */
abstract class Resource {

	private static final Logger LOGGER = Logger.getLogger(Resource.class.getName());

	private static final Pattern RELEASE_LINK = Pattern.compile(
			"((https?://)?[a-z0-9\\-\\.]+\\.[a-z0-9]+)?/(album|track)/[a-z0-9\\-]+",
			Pattern.CASE_INSENSITIVE);

	private final SearchTask searchTask;


	/**
	 * Private constructor for subclasses.
	 * 
	 * @param searchTask an instance of SearchTask that requests the resource
	 * @throws NullPointerException if search task is null
	 */
	private Resource(SearchTask searchTask) {
		this.searchTask = Objects.requireNonNull(searchTask);
	}


	/**
	 * Returns search task that requsted this resource. 
	 */
	SearchTask searchTask() {
		return searchTask;
	}


	/**
	 * Returns a set of unique release links found on this resource. 
	 * If search task has been cancelled before this resource has
	 * finished loading, returns empty set.
	 */
	abstract Set<String> releaseLinks();


	/**
	 * Checks the link for missing protocol and adds "http" if there's none.
	 */
	private static String addProtocolIfMissing(String link) {
		if (!link.startsWith("http://") && !link.startsWith("https://"))
			link = "http://" + link;
		return link;
	}



	/**
	 * Generic resource corresponding to an URL.
	 * The content referenced by the URL is loaded, interpreted as text and scanned for release links.
	 * Supports "http", "https" and "file" protocols.
	 */
	private static class URLResource extends Resource {

		private final Set<String> releaseLinks = new HashSet<>();


		/**
		 * Constructs an URLResource using specified URL string and loads it.
		 * 
		 * @param url URL string
		 * @param searchTask an instance of SearchTask that requests the resource
		 * @throws IOException if resource cannot be loaded for some reason 
		 * @throws IllegalArgumentException if supplied URL string is not valid
		 * @throws NullPointerException if URL string or search task is null
		 */
		URLResource(String url, SearchTask searchTask) throws IOException {
			super(searchTask);

			// Pushing URL string through multi-arg constructor and ASCII string 
			// conversion of URI to properly encode it before passing as URL object
			load(ExceptionUnchecker.uncheck(
					() -> new URL(new URI(null, Objects.requireNonNull(url), null).toASCIIString()),
					IllegalArgumentException::new));
		}


		/**
		 * {@inheritDoc}
		 * This implementation returns an unmodifiable set of links.
		 */
		@Override
		Set<String> releaseLinks() {
			return Collections.unmodifiableSet(releaseLinks);
		}


		/**
		 * Loads the resource using supplied URL and collects every unique release 
		 * link found in there.
		 */
		private void load(URL url) throws IOException {
			if (searchTask().isCancelled())
				return;

			LOGGER.fine("Loading resource: " + url);
			URLConnection connection = getConnection(url);

			try (Scanner input = new Scanner(connection.getInputStream(), StandardCharsets.UTF_8.name())) {
				url = connection.getURL(); // effective url
				boolean isFile = URLConnectionBuilder.getProtocol(url).equals("file");

				for (String link; (link = input.findWithinHorizon(RELEASE_LINK, 0)) != null; ) {
					if (!searchTask().isCancelled()) {
						link = link.toLowerCase(Locale.ROOT);
						if (link.startsWith("/album") || link.startsWith("/track")) {
							if (isFile) {
								// When reading from file, skip links with no host
								continue;
							}
							else {
								String host = url.getHost();
								if ("bandcamp.com".equalsIgnoreCase(host)) {
									// Skip host-less links if page host is bandcamp.com: such links won't
									// be valid. This usually happens when loading fan pages.
									continue;
								}
								else {
									// Otherwise take missing protocol and host from the current page
									link = new StringBuilder(url.getProtocol())
									.append("://").append(host).append(link).toString();
								}
							}
						}
						else {
							link = addProtocolIfMissing(link);
						}
						releaseLinks.add(link);
					}
					else {
						// Discard everything if search task has been cancelled 
						releaseLinks.clear();
						break;
					}
				}
			}
		}


		/**
		 * Obtains a specialized type of connection for the given URL depending
		 * on its protocol.
		 * 
		 * @throws IOException if I/O-, network- or protocol-related error occurs while
		 *         instantiating a connection or if protocol is not supported
		 */
		private static URLConnection getConnection(URL url) throws IOException {
			String protocol = URLConnectionBuilder.getProtocol(url);
			if (protocol.equals("file"))
				return URLConnectionBuilder.newFileURLConnection(url).build();
			else if (protocol.equals("http") || protocol.equals("https"))
				return URLConnectionBuilder.newHttpURLConnection(url).build();
			else
				throw new IOException('"' + protocol + "\" protocol is not supported");
		}

	}



	/**
	 * A resource representing Bandcamp's undocumented API endpoint that allows to search
	 * releases by tags.
	 */
	private static class TagAPIResource extends Resource {

		private static final URI TAG_API_URI = URI.create("https://bandcamp.com/api/hub/2/dig_deeper");
		private static final String REQUEST_BODY_TEMPLATE = 
				"{\"filters\":{\"format\":\"all\",\"location\":0,\"sort\":\"date\",\"tags\":[%s]},\"page\":%d}";

		private static final Pattern EXCESSIVE_WHITESPACE = Pattern.compile("\\s+");
		private static final Pattern AMPERSAND_OR_WHITESPACE = Pattern.compile("\\s?[&\\s]\\s?");

		private final Set<String> releaseLinks = new HashSet<>();


		/**
		 * Constructs a TagAPIResource for obtaining releases with specified tags
		 * and result page, and loads it.
		 * 
		 * @param tagsString comma-separated string of tags
		 * @param pageNum the number of page in the result returned from API
		 * @param searchTask searchTask an instance of SearchTask that requests the resource
		 * @throws IOException if there was an I/O-related error while loading the resource
		 * @throws IllegalArgumentException if page number is < 1
		 * @throws NullPointerException if tags string or search task is null
		 */
		TagAPIResource(String tagsString, int pageNum, SearchTask searchTask) throws IOException {
			super(searchTask);

			Objects.requireNonNull(tagsString);
			if (pageNum < 1)
				throw new IllegalArgumentException("Page number must be > 0");

			load(prepareTags(tagsString), pageNum);
		}


		/**
		 * Converts comma-separated string of tags to a set, performing some validation and 
		 * sanitization, as well as removing duplicates and empty entries in the process.
		 * 
		 * @param tagsString comma-separated string of tags
		 * @return tags set
		 */
		private static Set<String> prepareTags(String tagsString) {
			return Arrays.stream(tagsString.split(","))
					.map(String::trim)
					.filter(tag -> !tag.isEmpty())
					.map(TagAPIResource::sanitizeTag)
					.collect(toCollection(LinkedHashSet::new));
		}


		/**
		 * Sanitizes the given tag.
		 */
		private static String sanitizeTag(String tag) {
			tag = tag.toLowerCase(Locale.ENGLISH);
			tag = EXCESSIVE_WHITESPACE.matcher(tag).replaceAll(" ");
			tag = AMPERSAND_OR_WHITESPACE.matcher(tag).replaceAll("-");
			return tag;	
		}


		/**
		 * {@inheritDoc}
		 * This implementation returns an unmodifiable set of links.
		 */
		@Override
		Set<String> releaseLinks() {
			return Collections.unmodifiableSet(releaseLinks);
		}


		/**
		 * Queries the API, loads the response and collects every unique release found there.
		 */
		private void load(Set<String> tags, int pageNum) throws IOException {
			if (tags.isEmpty() || searchTask().isCancelled())
				return;

			String requestBody = createRequestBody(tags, pageNum);
			LOGGER.fine("Loading resource: " + String.format("%s (params: %s)", TAG_API_URI, requestBody));

			HttpURLConnection connection = URLConnectionBuilder.newHttpURLConnection(TAG_API_URI)
					.method(POST)
					.header("Content-Type", "application/json")
					.postData(requestBody)
					.build();

			String response;
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(),
					StandardCharsets.UTF_8))) {
				response = reader.lines().collect(joining());
			}
			// Some of the tags submitted in request to the API can be discarded as invalid by Bandcamp.
			// The exact reason for this is unknown, but it's possibly due to the absence of releases 
			// with such tags in their DB.
			// In that case the returned result is relevant to the remaining "valid" tags. However, if ALL
			// tags in the request were invalid, then Bandcamp simply ignores tag filter and returns all
			// recently added releases regardless of tags.
			// To avoid confusion we examine the value of tag_id property in response and return empty result
			// if it is 0. If request had any valid tags, the tag_id property will contain the id of the first
			// valid tag in supplied tag set.
			if (!response.contains("\"tag_id\":0")) {
				try (Scanner input = new Scanner(response)) {
					for (String link; (link = input.findWithinHorizon(RELEASE_LINK, 0)) != null; ) {
						if (!searchTask().isCancelled()) {
							releaseLinks.add(addProtocolIfMissing(link.toLowerCase(Locale.ROOT)));
						}
						else {
							releaseLinks.clear();
							break;
						}
					}
				}
			}
			else {
				LOGGER.warning("Server returned no relevant results because none of the supplied tags were valid: " 
						+ tags.stream().collect(joining(", ")));
			}
		}


		/**
		 * Creates a body for POST request to the API.
		 */
		private static String createRequestBody(Set<String> tags, int pageNum) {
			String tagsArrayString = tags.stream()
					.map(tag -> "\"" + tag.replace("\"", "\\\"") + "\"")
					.collect(joining(","));
			return String.format(REQUEST_BODY_TEMPLATE, tagsArrayString, pageNum);
		}

	}


	/**
	 * Returns a generic resource corresponding to the supplied URL.
	 * The resource can be either an arbitrary web page or a file on local or network drive,
	 * which is loaded, interpreted as text and scanned for release links.
	 * 
	 * @param url URL string of the resource; only "http", "https" and "file" protocols are supported
	 * @param searchTask an instance of SearchTask that requests the resource
	 * @return instantiated resource
	 * @throws IOException if I/O-, network- or protocol-related error occurs while
	 *         instantiating a resource or if URL protocol is not supported
	 */
	static Resource url(String url, SearchTask searchTask) throws IOException {
		return new URLResource(url, searchTask);
	}

	
	/**
	 * Returns a resource for accessing Bandcamp's undocumented API endpoint that allows to search
	 * releases by tags.
	 * The resource will contain links to releases matched by all valid entries in supplied list of tags
	 * and corresponding to a given page number in the result returned from API, where results are sorted
	 * by release publish date (newest first).
	 * If no valid tags were provided, the resource will contain no links.
	 * 
	 * @param tagsString comma-separated string of tags
	 * @param pageNum the number of page in the result returned from API
	 * @param searchTask searchTask an instance of SearchTask that requests the resource
	 * @return an instance of TagAPIResource
	 * @throws IOException if there was an I/O-related error while loading the resource
	 * @throws IllegalArgumentException if page number is < 1
	 * @throws NullPointerException if tags string or search task is null
	 */
	static Resource tags(String tagsString, int pageNum, SearchTask searchTask) throws IOException {
		return new TagAPIResource(tagsString, pageNum, searchTask);
	}
	
}