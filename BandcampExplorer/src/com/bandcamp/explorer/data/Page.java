package com.bandcamp.explorer.data;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.bandcamp.explorer.util.ExceptionUnchecker;

/**
 * This class is used to load an arbitrary page using specified URL string
 * and collect all Bandcamp releases, encountered on that page, in a form
 * of Callable objects.
 */
class Page {

	private static final Logger LOGGER = Logger.getLogger(Page.class.getName());
	private static final Pattern RELEASE_LINK = Pattern.compile("(https?://[^/]+\\.[^/\\+\"]+)??/(album|track)/[^/\\+]+?(?=(\"|\\?|<))", Pattern.CASE_INSENSITIVE);

	private final List<Callable<Release>> releaseLoaders = new ArrayList<>();
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
		try (Scanner input = new Scanner(url.openStream(), StandardCharsets.UTF_8.name())) {
			Set<String> links = new HashSet<>();
			String link = null;
			while ((link = input.findWithinHorizon(RELEASE_LINK, 0)) != null) {
				link = link.toLowerCase(Locale.ROOT);
				if (link.startsWith("/album") || link.startsWith("/track"))
					link = url.getProtocol() + "://" + url.getHost() + link;
				if (links.add(link)) // ensure that we don't create a loader for same link more than once
					releaseLoaders.add(createReleaseLoader(link));
			}
		}
	}


	/**
	 * Creates a loader for the specified release link.
	 * 
	 * @param url URL string representing a location to load release from
	 * @return a Callable object, containing code to load the release
	 */
	private Callable<Release> createReleaseLoader(String url) {	
		return () -> {
			try {
				return !parentTask.isCancelled() ? Release.forURL(url) : null;
			} 
			catch (Exception e) {
				LOGGER.log(Level.WARNING, "Error loading release: " + url + " (" + e.getMessage() + ")", e);
				return null;
			}
		};
	}


	/**
	 * Returns an unmodifiable list of loaders, each corresponding to every unique
	 * release link found on this page. Loader is an instance of Callable which, 
	 * when invoked, loads and returns the Release object. If parent search task
	 * was cancelled or error occured on release loading, the loader returns null.
	 */
	List<Callable<Release>> getReleaseLoaders() {
		return Collections.unmodifiableList(releaseLoaders);
	}

}