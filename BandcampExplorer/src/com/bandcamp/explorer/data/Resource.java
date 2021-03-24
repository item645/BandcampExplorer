package com.bandcamp.explorer.data;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
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
						else if (!link.startsWith("http://") && !link.startsWith("https://")) {
							link = "http://" + link;
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

}