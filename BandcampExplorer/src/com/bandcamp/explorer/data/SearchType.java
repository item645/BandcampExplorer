package com.bandcamp.explorer.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;

/**
 * Enumerates supported search types, providing different ways to find and load releases.
 */
public enum SearchType {

	/**
	 * This type employs a standard Bandcamp search feature to find releases, using 
	 * https://bandcamp.com/search page to supply a query string and collect the results.
	 * The pageNum parameter indicates the number of page to process in the returned result.
	 * Note that for this search type it is not guaranteed that <b>all</b> relevant
	 * results will be returned.
	 */
	SEARCH (true) {
		@Override
		Resource loadResource(String query, int pageNum, SearchTask searchTask) throws IOException {
			if (pageNum < 1)
				throw new IllegalArgumentException("Page number must be > 0");
			String query1 = query.toLowerCase(Locale.ENGLISH).replaceAll(" ", "+");
			String url = String.format("https://bandcamp.com/search?q=%1$s&page=%2$d", query1, pageNum);
			return Resource.url(url, searchTask);
		}
	},


	/**
	 * This search type provides a means to access Bandcamp's undocumented API endpoint that allows 
	 * to search releases by tags.
	 * For this type query string parameter is interpreted as comma-separated list of tags,
	 * while pageNum parameter indicates the number of page to process in the result returned 
	 * from API.
	 * A search result obtained using this type will contain releases that match all valid entries 
	 * in supplied list of tags and corresponding to a given page number, sorted by publish date in 
	 * descending order.
	 * Tags, which Bandcamp's API considers invalid, will be ignored, and returned result will be 
	 * relevant only to the remaining valid tags. In case all of the tags were invalid, the result 
	 * will be empty.
	 */
	TAGS (true) {
		@Override
		Resource loadResource(String query, int pageNum, SearchTask searchTask) throws IOException {
			return Resource.tags(query, pageNum, searchTask);
		}
	},


	/**
	 * This type can be used to directly load any page on Bandcamp to collect all releases 
	 * this page contains. This will work not only for Bandcamp, but also for arbitrary
	 * page on any other website which has Bandcamp data and lists of releases embedded
	 * in it.
	 * Also, this search type allows for opening of text files from local or network drive
	 * and loading the releases correspondning to every unique link encountered in those
	 * files.
	 * The query string paramater should be a valid URL string, pointing to desired
	 * page or file. For web page URL protocol part is not mandatory and can be dropped
	 * (in this case loading is done via http), but for loading of files the "file" protocol
	 * must be specified. 
	 * The pageNum parameter is ignored for this search type.
	 */
	DIRECT (false) {
		@Override
		Resource loadResource(String query, int pageNum, SearchTask searchTask) throws IOException {
			int i = query.indexOf(':');
			String protocol = i > -1 ? query.substring(0, i).toLowerCase(Locale.ROOT) : "";

			String url;
			if (protocol.equals("https") || protocol.equals("http")) {
				url = query.toLowerCase(Locale.ROOT);
			}
			else if (protocol.equals("file")) {
				checkFilePath(new URL(query).getPath());
				url = query;
			}
			else if (protocol.isEmpty()) {
				url = "http://" + query;
			}
			else {
				throw new IllegalArgumentException('"' + protocol + "\" protocol is not supported");
			}

			return Resource.url(url, searchTask);
		}

		/**
		 * Checks file path for validity. 
		 * 
		 * @param filePath file path to check
		 * @throws IllegalArgumentException if file path is empty or points to a directory
		 * @throws FileNotFoundException if such file does not exist 
		 */
		private void checkFilePath(String filePath) throws FileNotFoundException {
			if (filePath.isEmpty())
				throw new IllegalArgumentException("File path is empty");
			File file = new File(filePath);
			if (!file.exists())
				throw new FileNotFoundException("File does not exist: " + filePath);
			if (file.isDirectory())
				throw new IllegalArgumentException("File path is a directory: " + filePath);
		}		
	};


	private final boolean isMultiPage;


	private SearchType(boolean isMultiPage) {
		this.isMultiPage = isMultiPage;
	}


	/**
	 * Returns true if this search type supports using multiple pages.
	 */
	public boolean isMultiPage() {
		return isMultiPage;
	}


	/**
	 * Creates and loads Resource object appropriate to the search type.
	 * Each enum constant has its own implementation of this method to load the resource
	 * corresponding to search type. This way constants of SearchType enum work as
	 * factories for creating Resource instances.
	 * 
	 * @param query a query string
	 * @param pageNum a number of page to process
	 * @param searchTask an instance of SearchTask that requests resource loading
	 * @return an instantiated Resource object
	 * @throws IOException if resource cannot be loaded for some reason
	 * @throws NullPointerException if query or parentTask is null
	 * @throws IllegalArgumentException generally thrown if some parameters are not
	 *         valid (the validity is implementation-dependent)
	 */
	abstract Resource loadResource(String query, int pageNum, SearchTask searchTask) throws IOException;

}
