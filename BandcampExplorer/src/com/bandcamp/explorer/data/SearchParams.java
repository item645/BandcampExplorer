package com.bandcamp.explorer.data;

import java.util.Comparator;
import java.util.Objects;

/**
 * Simple container class to provide parameters for search tasks.
 */
public class SearchParams {

	private final String searchQuery;
	private final SearchType searchType;
	private final int pages;
	private final Comparator<Release> sortOrder;


	/**
	 * A builder to supply search parameters in convenient way.
	 */
	public static final class Builder {
		private final String searchQuery;
		private final SearchType searchType;
		private int pages = 1;
		private Comparator<Release> sortOrder = 
				ReleaseSortOrder.PUBLISH_DATE_DESC.thenComparing(ReleaseSortOrder.ARTIST_AND_TITLE);


		/**
		 * Constructs a builder with mandatory parameters.
		 * 
		 * @param searchQuery a string representing search query; the actual meaning of
		 *        search query depends on specified search type
		 * @param searchType a search type option as provided by SearchType enum
		 * @throws NullPointerException of search query or search type is null
		 */
		public Builder(String searchQuery, SearchType searchType) {
			this.searchQuery = Objects.requireNonNull(searchQuery);
			this.searchType = Objects.requireNonNull(searchType);
		}


		/**
		 * A number of pages to search.
		 * Default is 1.
		 * 
		 * @param value number of pages
		 * @return this builder
		 */
		public Builder pages(int value) {
			this.pages = value;
			return this;
		}


		/**
		 * Sort order for search result.
		 * By default releases in search result will be sorted by publish date
		 * in descending order.
		 * 
		 * @param value comparator that sets a sort order for results
		 * @return this builder
		 * @throws NullPointerException if supplied comparator is null
		 */
		public Builder sortOrder(Comparator<Release> value) {
			this.sortOrder = Objects.requireNonNull(value);
			return this;
		}


		/**
		 * Creates an instance of SearchParams using parameters passed to this builder.
		 */
		public SearchParams build() {
			return new SearchParams(this);
		}

	}


	/**
	 * Creates an instance of SearchParams.
	 */
	private SearchParams(Builder builder) {
		this.searchQuery = builder.searchQuery;
		this.searchType = builder.searchType;
		this.pages = builder.pages;
		this.sortOrder = builder.sortOrder;
	}


	/**
	 * Returns a string representing search query.
	 */
	public String searchQuery() {
		return searchQuery;
	}


	/**
	 * Returns a search type.
	 */
	public SearchType searchType() {
		return searchType;
	}


	/**
	 * Returns the number of pages to search.
	 */
	public int pages() {
		return pages;
	}


	/**
	 * Returns a comparator that sets a sort order for results.
	 */
	public Comparator<Release> sortOrder() {
		return sortOrder;
	}

}
