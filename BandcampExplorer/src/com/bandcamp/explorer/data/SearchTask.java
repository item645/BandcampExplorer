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

import javafx.concurrent.Task;

/**
 * An implementation of JavaFX Task used to execute a search for releases
 * on Bandcamp using specified search parameters.
 * The get() method of this task returns a SearchResult object, containing all the
 * releases found during this search session.
 * The sort order of releases in a returned SearchResult is determined by
 * sortOrder option specified by search parameters.
 */
public final class SearchTask extends Task<SearchResult> {

	private final ExecutorService executor;
	private final SearchParams searchParams;
	private Instant startTime;
	private String requestingDataMsg;
	private String loadingReleasesMsg;


	/**
	 * Creates a search task.
	 * 
	 * @param params search parameters
	 * @param executor an instance of executor service that will be employed
	 *        to execute all page loading operations 
	 * @throws NullPointerException if params or executor is null 
	 */
	public SearchTask(SearchParams params, ExecutorService executor) {
		this.searchParams = Objects.requireNonNull(params);
		this.executor = Objects.requireNonNull(executor);
	}


	/**
	 * Sets a string to update a message property with when data requesting
	 * stage is performed.
	 * 
	 * @param msg a string; if null, then message property won't be updated
	 */
	public void setRequestingDataMessage(String msg) {
		requestingDataMsg = msg;
	}


	/**
	 * Sets a string to update a message property with when release loading
	 * stage is performed.
	 * 
	 * @param msg a string; if null, then message property won't be updated
	 */
	public void setLoadingReleasesMessage(String msg) {
		loadingReleasesMsg = msg;
	}


	/**
	 * Returns an instant of time when this task started executing (that is,
	 * when its call() method has been invoked).
	 * 
	 * @return an instant of time; null, if task is not started yet
	 */
	public Instant getStartTime() {
		return startTime;
	}


	/** 
	 * Implements the actual logic of search.
	 */
	@Override
	protected SearchResult call() throws Exception {
		startTime = Instant.now();
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
			updateMessage(loadingReleasesMsg);

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
			updateProgress(i, numTasks);
		}
		if (isCancelled())
			return new SearchResult(searchParams);

		SearchResult result = new SearchResult(releases, searchParams);
		result.sort(searchParams.sortOrder());
		return result;
	}

}
