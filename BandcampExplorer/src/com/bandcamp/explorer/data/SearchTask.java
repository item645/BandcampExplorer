package com.bandcamp.explorer.data;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

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

	private static final Map<Integer, String> HTTP_SPECIAL_STATUS;
	static {
		Map<Integer, String> specials = new HashMap<>();
		specials.put(HTTP_NOT_FOUND,         "HTTP 404: Not Found");
		specials.put(HTTP_TOO_MANY_REQUESTS, "HTTP 429: Too Many Requests");
		specials.put(HTTP_UNAVAILABLE,       "HTTP 503: Service Unavailable");
		HTTP_SPECIAL_STATUS = Collections.unmodifiableMap(specials);
	}

	private final Object pauseLock = new Object();
	private volatile boolean paused = false;

	private final ExecutorService executor;
	private final SearchParams searchParams;
	private final String requestingDataMsg;
	private final BiFunction<Integer, Integer, String> loadingReleasesMsg;
	private Instant startTime; // read and updated only from JavaFX thread


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
	 * Implements the actual logic of search.
	 */
	@Override
	protected SearchResult call() throws Exception {
		if (requestingDataMsg != null)
			updateMessage(requestingDataMsg);

		// Loading and processing resources
		List<Future<Resource>> resources = new ArrayList<>();
		int numPages = searchParams.searchType().isMultiPage() ? searchParams.pages() : 1;
		for (int i = 1; i <= numPages; i++) {
			int pageNum = i;
			resources.add(executor.submit(
					() -> searchParams.searchType().loadResource(searchParams.searchQuery(), pageNum, this)));
		}

		// Collecting loaders for all release URLs found during search.
		// We use a hash set here to make sure that we won't have more than one
		// loader for each unique release URL string, because Bandcamp search and tag
		// pages with different numbers can duplicate same releases.
		Set<ReleaseLoader> releaseLoaders = new HashSet<>();
		for (Future<Resource> resource : resources)
			releaseLoaders.addAll(resource.get().releaseLoaders());

		if (isCancelled())
			return new SearchResult(searchParams);

		int numTasks = releaseLoaders.size();
		updateMessageAndProgress(0, numTasks);

		// Submitting loaders to completion service so that results can be
		// processed upon completion
		ExecutorCompletionService<ReleaseLoader.Result> completionService = new ExecutorCompletionService<>(executor);
		releaseLoaders.forEach(completionService::submit);

		// Processing results and gathering loaded Release objects
		List<Release> releases = new ArrayList<>();
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
						LOGGER.warning(String.format(message, uri, HTTP_SPECIAL_STATUS.get(responseCode)));
						pause(PAUSE_MLS_ON_SERVER_OVERLOAD); // let Bandcamp server cool down a bit
						if (loader.attempts() <= MAX_RELEASE_LOAD_ATTEMPTS) {
							// If we haven't yet exceeded max load attempts for this release loader,
							// try to run it again by re-submitting to completion service.
							repeat = true;
							completionService.submit(loader);
						}
						break;
					case HTTP_NOT_FOUND:
						LOGGER.warning(String.format(message, uri, HTTP_SPECIAL_STATUS.get(responseCode)));
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
