package com.bandcamp.explorer.data;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.bandcamp.explorer.util.ExceptionUnchecker;

/**
 * Represents a resource for obtaining links to Bandcamp releases.
 * The resource can be either a web page or a file on local or network drive.
 * Instance of this class loads the resource referenced by the specified URL
 * string and collects all unique links to Bandcamp releases found in that
 * resource, creating a {@link ReleaseLoader} task for each found release
 * link. A set of release loaders can be obtained via {@link #releaseLoaders()}.
 */
class Resource {

	private static final Logger LOGGER = Logger.getLogger(Resource.class.getName());

	private static final Pattern RELEASE_LINK = Pattern.compile(
			"((https?://)?[a-z0-9\\-\\.]+\\.[a-z0-9]+)?/(album|track)/[a-z0-9\\-]+",
			Pattern.CASE_INSENSITIVE);

	private final Set<ReleaseLoader> releaseLoaders = new HashSet<>();
	private final SearchTask parentTask;


	/**
	 * Constructs a resource object using specified URL string and loads it.
	 * 
	 * @param url URL string
	 * @param parentTask an instance of SearchTask that requests the resource
	 * @throws IOException if resource cannot be loaded for some reason 
	 * @throws IllegalArgumentException if supplied URL string is not valid
	 * @throws NullPointerException if URL string or parent task is null
	 */
	Resource(String url, SearchTask parentTask) throws IOException {
		this.parentTask = Objects.requireNonNull(parentTask);

		// Pushing URL string through multi-arg constructor and ASCII string 
		// conversion of URI to properly encode it before passing as URL object
		load(ExceptionUnchecker.uncheck(
				() -> new URL(new URI(null, Objects.requireNonNull(url), null).toASCIIString()),
				IllegalArgumentException::new));
	}


	/**
	 * Loads a resource using supplied URL and creates a loader for every unique release 
	 * link found in this resource.
	 */
	private void load(URL url) throws IOException {
		if (parentTask.isCancelled())
			return;

		URLConnection connection = URLConnectionHelper.getConnection(url);
		try (Scanner input = new Scanner(connection.getInputStream(),
				StandardCharsets.UTF_8.name())) {
			url = connection.getURL(); // effective url
			boolean isFile = url.getProtocol().toLowerCase(Locale.ROOT).equals("file");
			Set<String> links = new HashSet<>();

			for (String link; (link = input.findWithinHorizon(RELEASE_LINK, 0)) != null; ) {
				link = link.toLowerCase(Locale.ROOT);
				if (link.startsWith("/album") || link.startsWith("/track")) {
					if (isFile)
						// When reading from file, skip links with no host
						continue;
					else {
						String host = url.getHost();
						if ("bandcamp.com".equalsIgnoreCase(host))
							// Skip host-less links if page host is bandcamp.com: such links won't
							// be valid. This usually happens when loading fan pages.
							continue;
						else
							// Otherwise take missing protocol and host from the current page
							link = new StringBuilder(url.getProtocol())
							.append("://").append(host).append(link).toString();
					}
				}
				else if (!link.startsWith("http://") && !link.startsWith("https://"))
					link = "http://" + link;
				if (links.add(link)) { // ensure that we don't create a loader for same link more than once
					ReleaseLoader loader = createLoader(link);
					if (loader != null)
						releaseLoaders.add(loader);
				}
			}
		}
	}


	/**
	 * Creates a release loader for the specified link.
	 * 
	 * @param link release link
	 * @return instance of release loader or null, if release link is not valid
	 */
	private ReleaseLoader createLoader(String link) {
		try {
			return new ReleaseLoader(link, parentTask);
		}
		catch (URISyntaxException e) {
			LOGGER.log(Level.FINER, "Release URL is not valid: " + link, e);
			return null;
		}
	}


	/**
	 * Returns an unmodifiable set of release loaders, corresponding to every unique
	 * release link found in this resource.
	 */
	Set<ReleaseLoader> releaseLoaders() {
		return Collections.unmodifiableSet(releaseLoaders);
	}

}