package com.bandcamp.explorer.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a search result returned by SearchTask after conducting a search.
 */
public final class SearchResult implements Iterable<Release> {

	private final List<Release> result = new ArrayList<>();
	private final SearchParams searchParams;
	private final int failed;
	private final String asString;


	/**
	 * Constructs an empty search result.
	 * 
	 * @param searchParams parameters that were passed to SearchTask
	 * @throws NullPointerException if searchParams is null
	 */
	SearchResult(SearchParams searchParams) {
		this(null, 0, searchParams);
	}


	/**
	 * Constructs a search result object for the specified collection
	 * of releases found during search.
	 * 
	 * @param result a collection of releases found during search
	 * @param failed the number of releases that failed to load
	 * @param searchParams parameters that were passed to SearchTask
	 */
	SearchResult(Collection<Release> result, int failed, SearchParams searchParams) {
		assert failed >= 0;
		assert searchParams != null;

		this.searchParams = searchParams;
		this.failed = failed;
		if (result != null)
			this.result.addAll(result);

		this.asString = toString(this.result, this.failed, this.searchParams);
	}


	/**
	 * Creates string representation for the search result.
	 * 
	 * @param result a collection of releases found during search
	 * @param failed the number of releases that failed to load
	 * @param searchParams parameters that were passed to SearchTask
	 * @return string representation of the search result
	 */
	private static String toString(List<Release> result, int failed, SearchParams searchParams) {
		return String.format("%1$s: %2$s (%3$d %4$s, %5$d found, %6$d loaded, %7$d failed)",
				searchParams.searchType(),
				searchParams.searchQuery(),
				searchParams.pages(),
				searchParams.pages() > 1 ? "pages" : "page",
				result.size() + failed,
				result.size(),
				failed
		);
	}


	/**
	 * Returns search parameters that were passed to a SearchTask which
	 * produced this result.
	 */
	public SearchParams getSearchParams() {
		return searchParams;
	}


	/**
	 * Returns the number of releases in this search result.
	 */
	public int size() {
		return result.size();
	}


	/**
	 * Returns the number of releases that failed to load.
	 */
	public int getFailedCount() {
		return failed;
	}


	/**
	 * Returns an iterator over the releases contained by this search result.
	 * The returned iterator does not permit removal of releases.
	 */
	@Override
	public Iterator<Release> iterator() {
		return Collections.unmodifiableCollection(result).iterator();
	}


	/** 
	 * Converts this search result to its string representation.
	 */
	@Override
	public String toString() {
		return asString;
	}

}
