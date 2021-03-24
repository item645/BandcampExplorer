package com.bandcamp.explorer.data;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javafx.concurrent.Task;

/**
 * An implementation of JavaFX Task used to execute a search for releases
 * on Bandcamp using specified search parameters.
 * The get() and getValue() methods of this task return a SearchResult object,
 * containing all the releases found and successfully loaded during this search session.
 * The sort order of releases in a returned SearchResult is determined by
 * sortOrder option specified by search parameters.
 */
public final class SearchTask extends Task<SearchResult> {

	private static final Logger LOGGER = Logger.getLogger(SearchTask.class.getName());

	private static final int HTTP_TOO_MANY_REQUESTS = 429;

	private static final int MAX_RELEASE_LOAD_ATTEMPTS = 4;
	private static final int PAUSE_MLS_ON_SERVER_OVERLOAD = 5000;

	private final Object pauseLock = new Object();
	private volatile boolean paused = false;

	private final ExecutorService executor;
	private final SearchParams searchParams;
	private final String requestingDataMsg;
	private final BiFunction<Integer, Integer, String> loadingReleasesMsg;
	private Instant startTime; // read and updated only from JavaFX thread



	/**
	 * A Callable task used to obtain a Release object corresponding to the
	 * specified URL string.
	 */
	private static class ReleaseLoader implements Callable<ReleaseLoader.Result> {

		private final URI uri;
		private final SearchTask parentTask;
		private final String releaseID;
		private final AtomicInteger attempts = new AtomicInteger();


		/**
		 * Represents a result returned by {@link ReleaseLoader#call()} method.
		 * The instance of Result provides access to a release object obtained by
		 * the loader or, in case the loading has failed, to a relevant exception object.
		 */
		class Result {

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
	

	/**
	 * Creates a search task.
	 * 
	 * @param params search parameters
	 * @param executor an instance of executor service that will be employed
	 *        to execute all resource and release loading operations 
	 * @param requestingDataMsg a text for updating message property when data
	 *        requesting stage is performed (if null, message is not updated)
	 * @param loadingReleasesMsg a callback function that accepts two int arguments (number of
	 *        processed releases and total number of releases to process) and returns a string
	 *        for updating message property when release loading stage is performed (if null,
	 *        message is not updated)
	 * @throws NullPointerException if params or executor is null 
	 */
	public SearchTask(SearchParams params, ExecutorService executor,
			String requestingDataMsg, BiFunction<Integer, Integer, String> loadingReleasesMsg) {
		this.searchParams = Objects.requireNonNull(params);
		this.executor = Objects.requireNonNull(executor);
		this.requestingDataMsg = requestingDataMsg;
		this.loadingReleasesMsg = loadingReleasesMsg;
	}


	/**
	 * Returns an instant of time when this task started executing (that is,
	 * when task has transitioned to the RUNNING state).
	 * 
	 * @return an instant of time; null, if task is not started yet
	 */
	public Instant startTime() {
		return startTime;
	}


	/**
	 * If this task is in paused state, calling this method causes the current
	 * thread to wait until this task's thread resumes execution.
	 * This method provides a means for search task to pause the execution of
	 * release loaders when it is put in paused state.
	 * If task is not paused, calling await() will have no effect.
	 */
	void await() {
		if (paused) {
			synchronized (pauseLock) {
				try {
					while (paused)
						pauseLock.wait();
				}
				catch (InterruptedException e) {
					Thread t = Thread.currentThread();
					t.interrupt();
					LOGGER.warning("Loader " + t + " interrupted");
				}
			}
		}
	}


	/**
	 * {@inheritDoc}
	 * This implementation sets a start time of this task.
	 */
	@Override
	protected void running() {
		startTime = Instant.now();
	}


	/**
	 * Performs a search for releases according to the search parameters
	 * and returns the result as SearchResult object. 
	 */
	@Override
	protected SearchResult call() throws Exception {
		Set<ReleaseLoader> releaseLoaders = createReleaseLoaders();
		return !isCancelled() ? loadReleases(releaseLoaders) : new SearchResult(searchParams);
	}


	/**
	 * Loads all resources determined by the search type, collects all unique release links
	 * from these resources and creates a {@link ReleaseLoader} task for each link.
	 * 
	 * @return a set of release loader tasks
	 * @throws ExecutionException if any of the resources has failed to load
	 */
	private Set<ReleaseLoader> createReleaseLoaders() throws InterruptedException, ExecutionException {
		if (requestingDataMsg != null)
			updateMessage(requestingDataMsg);

		ExecutorCompletionService<Set<String>> completionService = new ExecutorCompletionService<>(executor);
		int pages = searchParams.searchType().isMultiPage() ? searchParams.pages() : 1;

		for (int i = 1; i <= pages; i++) {
			int pageNum = i;
			completionService.submit(() -> collectReleaseLinks(pageNum));
		}

		Set<String> releaseLinks = new HashSet<>();
		for (int i = 1; i <= pages; i++)
			releaseLinks.addAll(completionService.take().get());

		return releaseLinks.stream().map(this::createReleaseLoader).collect(Collectors.toSet());
	}


	/**
	 * Collects all unique release links from the resource with a given page number.
	 * 
	 * @param pageNum a page number of the resource; the meaning of this parameter depends
	 *        on the type of resource being loaded
	 * @return a set of release links
	 * @throws IOException if resource has failed to load
	 */
	private Set<String> collectReleaseLinks(int pageNum) throws IOException {
		return searchParams.searchType().loadResource(searchParams.searchQuery(), pageNum, this).releaseLinks();
	}


	/**
	 * Creates a release loader for the specified link.
	 * 
	 * @param link release link
	 * @return instance of release loader or null, if release link is not valid
	 */
	private ReleaseLoader createReleaseLoader(String link) {
		try {
			return new ReleaseLoader(link, this);
		}
		catch (URISyntaxException e) {
			LOGGER.log(Level.FINER, "Release URL is not valid: " + link, e);
			return null;
		}
	}


	/**
	 * Loads all releases corresponding to the supplied set of loaders and returns
	 * a SearchResult object with all successfully loaded releases.
	 * 
	 * @param releaseLoaders a set of release loaders
	 * @return SearchResult instance
	 * @throws ExecutionException if there was an unrecoverable error that caused
	 *         this search session to halt
	 */
	private SearchResult loadReleases(Set<ReleaseLoader> releaseLoaders) 
			throws InterruptedException, ExecutionException {
		int numTasks = releaseLoaders.size();
		updateMessageAndProgress(0, numTasks);

		// Submitting loaders to completion service so that results can be
		// processed upon completion
		ExecutorCompletionService<ReleaseLoader.Result> completionService = new ExecutorCompletionService<>(executor);
		releaseLoaders.forEach(completionService::submit);

		List<Release> releases = new ArrayList<>();

		// Processing results and gathering loaded Release objects
		int failed = 0;
		for (int taskCounter = 1; taskCounter <= numTasks; ) {
			if (isCancelled())
				return new SearchResult(searchParams);

			boolean repeat = false;
			ReleaseLoader.Result result = completionService.take().get();
			if (result != null) {
				try {
					releases.add(result.get());
				}
				catch (Exception exception) {
					ReleaseLoader loader = result.loader();
					URI uri = loader.uri();
					String message = "Error loading release: %1$s (%2$s)";
					int responseCode = exception instanceof ReleaseLoadingException
							? ((ReleaseLoadingException)exception).getHttpResponseCode()
							: 0;
					switch (responseCode) {
					case HTTP_TOO_MANY_REQUESTS: case HTTP_UNAVAILABLE:
						// Here we do a special treatment for HTTP 429 (Too Many Requests) 
						// and HTTP 503 (Service Unavailable) errors which arise due to the 
						// high load that we have probably imposed on Bandcamp server.
						// At the moment Bandcamp does not return Retry-After header
						// so we use default timeout value to wait.
						LOGGER.warning(String.format(message, uri, getSpecialHttpStatus(responseCode)));
						pause(PAUSE_MLS_ON_SERVER_OVERLOAD); // let Bandcamp server cool down a bit
						if (loader.attempts() <= MAX_RELEASE_LOAD_ATTEMPTS) {
							// If we haven't yet exceeded max load attempts for this release loader,
							// try to run it again by re-submitting to completion service.
							repeat = true;
							completionService.submit(loader);
						}
						break;
					case HTTP_NOT_FOUND:
						LOGGER.warning(String.format(message, uri, getSpecialHttpStatus(responseCode)));
						break;
					default:
						// For other errors log both exception and message
						LOGGER.log(Level.WARNING, String.format(message, uri, exception.getMessage()), exception);
					}
					if (!repeat)
						failed++;
				}
			}

			if (!repeat)
				updateMessageAndProgress(taskCounter++, numTasks);
		}

		releases.sort(searchParams.sortOrder());
		return new SearchResult(releases, failed, searchParams);
	}


	/**
	 * Returns textual status description for given HTTP error code.
	 */
	private static String getSpecialHttpStatus(int statusCode) {
		switch (statusCode) {
		case HTTP_NOT_FOUND:
			return "HTTP 404: Not Found";
		case HTTP_TOO_MANY_REQUESTS:
			return "HTTP 429: Too Many Requests";
		case HTTP_UNAVAILABLE:
			return "HTTP 503: Service Unavailable";
		default:
			return Integer.toString(statusCode);
		}
	}


	/**
	 * Updates message, progress, workDone and totalWork properties with 
	 * current processed and total number of releases.
	 * 
	 * @param processed the number of releases processed already
	 * @param total total number of releases to process by this task
	 */
	private void updateMessageAndProgress(int processed, int total) {
		updateProgress(processed, total);
		if (loadingReleasesMsg != null)
			updateMessage(loadingReleasesMsg.apply(processed, total));
	}


	/**
	 * Pauses the execution of this task's thread for the specified number of milliseconds.
	 * 
	 * @param millis the length of time to pause in milliseconds
	 */
	private void pause(long millis) {
		assert millis >= 0;
		
		paused = true;
		try {
			LOGGER.fine("Search paused for " + millis + " milliseconds");
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOGGER.warning("Search task thread interrupted");
		}
		finally {
			synchronized (pauseLock) {
				paused = false;
				pauseLock.notifyAll();
			}
		}
	}

}
