package com.bandcamp.explorer.data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
 * An implementation of JavaFX Task that is used to execute a search for releases
 * on Bandcamp using specified search parameters.
 * The get() method of this task returns an unordered set of Release objects, corresponding
 * to search parameters and found during this search session. However, the
 * preferred way to obtain search results is to use {@link SearchEngine#getResults()}
 * method since it allows for sorting the results and returns combined results
 * if combineResults option has been set in search parameters.
 */
public final class SearchTask extends Task<Set<Release>> {

	private final SearchEngine searchEngine;
	private final ExecutorService executor;
	private final SearchParams searchParams;
	private Instant startTime;
	private String requestingDataMsg;
	private String loadingReleasesMsg;
	
	
	/**
	 * Creates a search task.
	 * 
	 * @param params search parameters
	 * @param searchEngine a search engine that was used to create this task
	 * @param executor an instance of executor service that will be employed
	 * 		  to execute all loading operations 
	 * @throws NullPointerException if params, search engine or executor is null 
	 */
	SearchTask(SearchParams params, SearchEngine searchEngine, ExecutorService executor) {
		this.searchParams = Objects.requireNonNull(params);
		this.searchEngine = Objects.requireNonNull(searchEngine);
		this.executor = Objects.requireNonNull(executor);
	}
	
	
	/**
	 * Sets a string to update a message property with when data requesting
	 * operation is performed.
	 * 
	 * @param msg a string; if null, then message property won't be updated
	 */
	public void setRequestingDataMessage(String msg) {
		requestingDataMsg = msg;
	}
	
	
	/**
	 * Sets a string to update a message property with when release loading
	 * operation is performed.
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
	protected Set<Release> call() throws Exception {
		// Register this task as running (this prevents other tasks in the same search engine to intervene)
		searchEngine.registerRunningTask(this);
		startTime = Instant.now();
		try {
			if (requestingDataMsg != null)
				updateMessage(requestingDataMsg);
			
			// Loading and processing search pages
			List<Future<Page>> pages = new ArrayList<>();
			int numPages = searchParams.searchType.isMultiPage ? searchParams.pages : 1;
			for (int i = 1; i <= numPages; i++) {
				int pageNum = i;
				pages.add(executor.submit(
						() -> searchParams.searchType.loadPage(searchParams.searchQuery, pageNum, this)));
			}
			
			// Collecting loaders for all releases found during search
			List<Callable<Release>> releaseLoaders = new ArrayList<>();
			for (Future<Page> page : pages)
				releaseLoaders.addAll(page.get().getReleaseLoaders());
			
			if (isCancelled())
				return Collections.emptySet();
			
			int numTasks = releaseLoaders.size();
			updateProgress(0, numTasks);
			if (loadingReleasesMsg != null)
				updateMessage(loadingReleasesMsg);
			
			// Submitting loaders to completion service so that results can be
			// processed upon completion
			ExecutorCompletionService<Release> completionService = new ExecutorCompletionService<>(executor);
			releaseLoaders.forEach(completionService::submit);
			
			// Gathering loaded Release objects
			Set<Release> results = new HashSet<>();
			for (int i = 1; i <= numTasks; i++) {
				if (isCancelled())
					return Collections.emptySet();
				Release release = completionService.take().get();
				if (release != null)
					results.add(release);
				updateProgress(i, numTasks);
			}
			
			if (isCancelled())
				return Collections.emptySet();
			
			// Updating search engine with new results
			if (!searchParams.combineResults)
				searchEngine.clearResults();
			searchEngine.addResults(results);
			
			return results;
		}
		finally {
			// Unregister this task when we are done
			searchEngine.unregisterRunningTask(this);
		}
	}

}
