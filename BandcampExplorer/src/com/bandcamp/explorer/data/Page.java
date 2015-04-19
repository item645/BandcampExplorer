package com.bandcamp.explorer.data;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;

import com.bandcamp.explorer.util.ExceptionUnchecker;

/**
 * This class is used to load an arbitrary page using specified URL string
 * and collect all Bandcamp releases, encountered on that page, in a form
 * of Callable objects.
 */
class Page {

	private static final Pattern RELEASE_LINK = Pattern.compile(
			"(https?://[^/]+\\.[^/\\+\"]+)??/(album|track)/[^/\\+]+?(?=(\"|\\?|<|\\s))", Pattern.CASE_INSENSITIVE);

	private final List<ReleaseLoader> releaseLoaders = new ArrayList<>();
	private final SearchTask parentTask;


	/**
	 * Constructs a page object using specified URL string and loads it.
	 * 
	 * @param url URL string
	 * @param parentTask an instance of SearchTask that requests a page loading
	 * @throws IOException if page cannot be loaded for some reason 
	 * @throws IllegalArgumentException if supplied URL string is not valid
	 * @throws NullPointerException if URL string or parent task is null
	 */
	Page(String url, SearchTask parentTask) throws IOException {
		this.parentTask = Objects.requireNonNull(parentTask);

		// Pushing URL string through multi-arg constructor and ASCII string 
		// conversion of URI to properly encode it before passing as URL object
		load(ExceptionUnchecker.uncheck(
				() -> new URL(new URI(null, Objects.requireNonNull(url), null).toASCIIString()),
				IllegalArgumentException::new));
	}


	/**
	 * Loads a page using supplied URL and creates a loader for every unique release 
	 * link found on this page.
	 */
	private void load(URL url) throws IOException {
		if (parentTask.isCancelled())
			return;

		URLConnection connection = url.openConnection();
		// 60 seconds timeout for both connect and read
		connection.setConnectTimeout(60000);
		connection.setReadTimeout(60000);

		try (Scanner input = new Scanner(connection.getInputStream(), StandardCharsets.UTF_8.name())) {
			Set<String> links = new HashSet<>();
			String link = null;
			while ((link = input.findWithinHorizon(RELEASE_LINK, 0)) != null) {
				link = link.toLowerCase(Locale.ROOT);
				if (link.startsWith("/album") || link.startsWith("/track")) {
					link = new StringBuilder(url.getProtocol())
					.append("://").append(url.getHost()).append(link).toString();
				}
				if (links.add(link)) // ensure that we don't create a loader for same link more than once
					releaseLoaders.add(new ReleaseLoader(link, parentTask));
			}
		}
	}


	/**
	 * Returns an unmodifiable list of release loaders, corresponding to every unique
	 * release link found on this page.
	 */
	List<ReleaseLoader> getReleaseLoaders() {
		return Collections.unmodifiableList(releaseLoaders);
	}

}