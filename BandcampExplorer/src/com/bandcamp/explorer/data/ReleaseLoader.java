package com.bandcamp.explorer.data;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A Callable task used to obtain a Release object corresponding to the
 * specified URL string.
 */
class ReleaseLoader implements Callable<ReleaseLoader.Result> {

	private final URI uri;
	private final SearchTask parentTask;
	private final String releaseID;
	private final AtomicInteger attempts = new AtomicInteger();


	/**
	 * Represents a result returned by {@link ReleaseLoader#call()} method.
	 * The instance of Result provides access to a release object obtained by
	 * the loader or, in case the loading has failed, to a relevant exception object.
	 */
	final class Result {

		private final Release release;
		private final Exception exception;


		/**
		 * Constructs a result with the specified Release object.
		 * 
		 * @param release a release
		 */
		private Result(Release release) {
			assert release != null;
			this.release = release;
			this.exception = null;
		}


		/**
		 * Constructs a result with the specified exception that
		 * has been thrown during the attempt to load the release.
		 * 
		 * @param exception an exception
		 */
		private Result(Exception exception) {
			assert exception != null;
			this.exception = exception;
			this.release = null;
		}


		/**
		 * Returns the reference to a ReleaseLoader task that produced this result.
		 */
		ReleaseLoader loader() {
			return ReleaseLoader.this;
		}


		/**
		 * Returns a Release object obtained by the loader task.
		 * If release loading has failed, this method throws an exception that
		 * has caused this failure.
		 * 
		 * @return loaded release
		 * @throws Exception if release load failed with exception
		 */
		Release get() throws Exception {
			if (exception != null)
				throw exception;
			else
				return release;
		}

	}


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
		return this == other || other instanceof ReleaseLoader
				&& Objects.equals(this.releaseID, ((ReleaseLoader)other).releaseID);
	}


	/**
	 * Returns a URI of the release which this loader attempts to load.
	 */
	URI uri() {
		return uri;
	}


	/**
	 * Returns current number of attempts made to load the release
	 * using this loader.
	 */
	int attempts() {
		return attempts.get();
	}


	/** 
	 * Returns a {@link ReleaseLoader.Result} object encapsulating the release corresponding to 
	 * this loader's URI. If release loading has failed, the returned result
	 * contains a reference to relevant exception object.
	 * If this loader's parent task has been cancelled prior to attempting the release load,
	 * the method returns null.
	 * This implementation does not throw exceptions.
	 */
	@Override
	public ReleaseLoader.Result call() {
		parentTask.await();
		if (parentTask.isCancelled())
			return null;
		
		try {
			int i = attempts.incrementAndGet();
			return new Result(Release.forURI(uri, i > 1 ? i : 0, releaseID));
		}
		catch (Exception e) {
			return new Result(e);
		}
	}

}
