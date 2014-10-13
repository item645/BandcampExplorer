package com.bandcamp.explorer.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Represents a search result returned by SearchTask after conducting a search.
 */
public final class SearchResult implements Iterable<Release> {

	private final List<Release> result = new ArrayList<>();
	private final SearchParams searchParams;


	/**
	 * Constructs an empty search result.
	 * 
	 * @param searchParams parameters that were passed to SearchTask
	 * @throws NullPointerException if searchParams is null
	 */
	SearchResult(SearchParams searchParams) {
		this(null, searchParams);
	}


	/**
	 * Constructs a search result object for the specified collection
	 * of releases found during search.
	 * 
	 * @param result a collection of releases found during search
	 * @param searchParams parameters that were passed to SearchTask
	 * @throws NullPointerException if searchParams is null
	 */
	SearchResult(Collection<Release> result, SearchParams searchParams) {
		if (result != null)
			this.result.addAll(result);
		this.searchParams = Objects.requireNonNull(searchParams);
	}


	/**
	 * Returns search parameters that were passed to a SearchTask which
	 * produced this result.
	 */
	public SearchParams getSearchParams() {
		return searchParams;
	}


	/**
	 * Sorts the releases contained in this result object using
	 * supplied comparator.
	 * 
	 * @param cmp a comparator used to compare releases
	 */
	void sort(Comparator<Release> cmp) {
		result.sort(cmp);
	}


	/**
	 * Returns the number of releases in this search result.
	 */
	public int size() {
		return result.size();
	}


	/**
	 * Returns an iterator over the releases contained by this search result.
	 * The returned iterator does not permit removal of releases.
	 */
	@Override
	public Iterator<Release> iterator() {
		return Collections.unmodifiableCollection(result).iterator();
	}

}
