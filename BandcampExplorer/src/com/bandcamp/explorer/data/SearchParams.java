package com.bandcamp.explorer.data;

import java.util.Objects;

/**
 * Simple container class to provide parameters for creating search tasks.
 */
public class SearchParams {

	final String searchQuery;
	final SearchType searchType;
	final int pages;
	final boolean combineResults;


	/**
	 * A builder to supply search parameters in convenient way.
	 */
	public static class Builder {
		private final String searchQuery;
		private final SearchType searchType;
		private int pages = 1;
		private boolean combineResults = false;


		/**
		 * Constructs a builer with mandatory parameters.
		 * 
		 * @param searchQuery a string representing search query; the actual meaning of
		 * 		  search query depends on specified search type
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
		 * Indicates whether current search results must be combined with preceding results.
		 * Default is false.
		 * 
		 * @param value if true, results will be combined, else - new search result
		 * 		  will replace any preceding one
		 * @return this builder
		 */
		public Builder combineResults(boolean value) {
			this.combineResults = value;
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
		this.combineResults = builder.combineResults;
	}

}
