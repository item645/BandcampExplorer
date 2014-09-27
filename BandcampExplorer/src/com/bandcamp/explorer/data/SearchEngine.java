package com.bandcamp.explorer.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

/**
 * A SearchEngine class provides a means to create and manage search
 * tasks, cancel them on demand and obtain search results.
 * At the moment it allows to execute individual tasks on sequential basis only,
 * but this may be changed in future versions.
 */
public final class SearchEngine {

	/**
	 * An executor service to be passed to created search tasks
	 */
	private final ExecutorService executor;

	/**
	 * A set of search results
	 */
	private final Set<Release> releases = Collections.newSetFromMap(new ConcurrentHashMap<>());

	/**
	 * Simple mutex to serialize a running of search tasks
	 */
	private final Semaphore searchMutex = new Semaphore(1);


	/**
	 * A reference to currently running search task
	 */
	private volatile SearchTask runningTask;


	/**
	 * Creates a search engine with the specified executor service.
	 * 
	 * @param executor an executor service to be used by search tasks
	 * 		  for loading operations
	 * @throws NullPointerException if executor is null
	 */
	public SearchEngine(ExecutorService executor) {
		this.executor = Objects.requireNonNull(executor);
	}


	/**
	 * Creates a new search task for the specified parameters.
	 * 
	 * @param params search parameters
	 * @return a new search task
	 */
	public SearchTask newTaskFor(SearchParams params) {
		return new SearchTask(params, this, executor);
	}


	/**
	 * Cancels currently running task, if any.
	 */
	public void cancelTask() {
		SearchTask task = runningTask;
		if (task != null)
			task.cancel(false);
	}


	/**
	 * Checks if there is a task running now.
	 */
	public boolean isTaskRunning() {
		return runningTask != null;
	}


	/**
	 * Returns a list of unique releases comprising current search results,
	 * optionally allowing to sort them using specified comparator.
	 * Depending on presence of combineResults parameter for last executed
	 * search task, returned list can contain either the search result from that
	 * last task only (if combineResults is false) or combined results from last task  
	 * and all preceding tasks (provided they had combineResults set to true).
	 * 
	 * @param sortOrder a comparator to sort the results; if null, results are not sorted
	 * @return a list of releases current search results 
	 */
	public List<Release> getResults(Comparator<Release> sortOrder) {
		List<Release> results = new ArrayList<>(releases);
		if (sortOrder != null)
			results.sort(sortOrder);
		return results;
	}


	/**
	 * Returns a number of releases in current search results.
	 */
	public int getResultsCount() {
		return releases.size();
	}


	/**
	 * Clears current search results.
	 */
	public void clearResults() {
		releases.clear();
	}


	/**
	 * Adds new set of releases to the current results, eliminating duplicate
	 * entries, if any.
	 * 
	 * @param results a set of new search results
	 */
	void addResults(Set<Release> results) {
		releases.addAll(results);
	}


	/**
	 * Registers search task as running within context of this search engine.
	 * This method is called from search task just after it has started to execute.
	 * If there's a task currently running, the caller of this method will be
	 * blocked until running task will be unregistered.
	 * Note that after this method was called, it's mandatory for search task to 
	 * unregister itself by calling {@link SearchEngine#removeRunningTask()} when
	 * it completes.
	 * 
	 * @param task a search task to register
	 * @throws NullPointerException if task is null
	 */
	void registerRunningTask(SearchTask task) {
		Objects.requireNonNull(task);
		searchMutex.acquireUninterruptibly();
		runningTask = task;
	}


	/**
	 * Unregisters search task as running in this search engine.
	 * If {@link SearchEngine#setRunningTask()} was invoked, this method must
	 * be called with the same task parameter after task completes execution.
	 * 
	 * @param task a search task to unregister; the method does nothing if specified task
	 * 		  is null or not running right now in this search engine
	 */
	void unregisterRunningTask(SearchTask task) {
		if (task == runningTask) {
			runningTask = null;
			searchMutex.release();
		}
	}

}
