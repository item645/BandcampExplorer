package com.bandcamp.explorer.data;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Callable task used to obtain a Release object corresponding to the
 * specified URL string.
 */
class ReleaseLoader implements Callable<Release> {

	private static final Logger LOGGER = Logger.getLogger(ReleaseLoader.class.getName());

	private final URI uri;
	private final SearchTask parentTask;
	private final String releaseID;


	/**
	 * Creates a release loader.
	 * 
	 * @param url URL string to load release from
	 * @param parentTask an instance of SearchTask that requests a release load
	 * @throws URISyntaxException if supplied URL string is not valid
	 * @throws NullPointerException if parent task is null
	 */
	ReleaseLoader(String url, SearchTask parentTask) throws URISyntaxException {
		this.parentTask = Objects.requireNonNull(parentTask);
		this.uri = new URI(url);
		this.releaseID = Release.createID(this.uri);
	}


	/**
	 * Returns a hash code for this release loader.
	 */
	@Override
	public int hashCode() {
		return releaseID.hashCode();
	}


	/**
	 * Tests this release loader for equality with another.
	 * Loaders are equal if they were created for the same release location,
	 * as defined by {@link Release#createID(URI)}.
	 */
	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		return other instanceof ReleaseLoader 
				? Objects.equals(this.releaseID, ((ReleaseLoader)other).releaseID)
				: false;
	}


	/** 
	 * Returns the Release object corresponding to this loader's URL string.
	 * This implementation does not throw exceptions. If release load results in exception
	 * or if this loader's parent task has been cancelled, it simply returns null.
	 */
	@Override
	public Release call() {
		try {
			return !parentTask.isCancelled() ? Release.forURI(uri) : null;
		} 
		catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error loading release: " + uri + " (" + e.getMessage() + ")", e);
			return null;
		}
	}

}
