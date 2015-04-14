package com.bandcamp.explorer.data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiFunction;

import javafx.concurrent.Task;

/**
 * An implementation of JavaFX Task used to execute a search for releases
 * on Bandcamp using specified search parameters.
 * The get() and getValue() methods of this task return a SearchResult object,
 * containing all the releases found during this search session.
 * The sort order of releases in a returned SearchResult is determined by
 * sortOrder option specified by search parameters.
 */
public final class SearchTask extends Task<SearchResult> {

	private final ExecutorService executor;
	private final SearchParams searchParams;
	private final String requestingDataMsg;
	private final BiFunction<Integer, Integer, String> loadingReleasesMsg;
	private Instant startTime;


	/**
	 * Creates a search task.
	 * 
	 * @param params search parameters
	 * @param executor an instance of executor service that will be employed
	 *        to execute all page and release loading operations 
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
	public Instant getStartTime() {
		return startTime;
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

		// Loading and processing search pages
		List<Future<Page>> pages = new ArrayList<>();
		int numPages = searchParams.searchType().isMultiPage ? searchParams.pages() : 1;
		for (int i = 1; i <= numPages; i++) {
			int pageNum = i;
			pages.add(executor.submit(
					() -> searchParams.searchType().loadPage(searchParams.searchQuery(), pageNum, this)));
		}

		// Collecting loaders for all releases found during search
		List<Callable<Release>> releaseLoaders = new ArrayList<>();
		for (Future<Page> page : pages)
			releaseLoaders.addAll(page.get().getReleaseLoaders());

		if (isCancelled())
			return new SearchResult(searchParams);

		int numTasks = releaseLoaders.size();
		updateProgress(0, numTasks);
		if (loadingReleasesMsg != null)
			updateMessage(loadingReleasesMsg.apply(0, numTasks));

		// Submitting loaders to completion service so that results can be
		// processed upon completion
		ExecutorCompletionService<Release> completionService = new ExecutorCompletionService<>(executor);
		releaseLoaders.forEach(completionService::submit);

		// Gathering loaded Release objects
		Set<Release> releases = new HashSet<>();
		for (int i = 1; i <= numTasks; i++) {
			if (isCancelled())
				return new SearchResult(searchParams);
			Release release = completionService.take().get();
			if (release != null)
				releases.add(release);
			if (loadingReleasesMsg != null)
				updateMessage(loadingReleasesMsg.apply(i, numTasks));
			updateProgress(i, numTasks);
		}

		SearchResult result = new SearchResult(releases, searchParams);
		result.sort(searchParams.sortOrder());
		return result;
	}

}
