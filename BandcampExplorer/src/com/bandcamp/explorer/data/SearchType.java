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
	 * This type employs a standard Bandcamp search feature to find releases,
	 * using https://bandcamp.com/search page to supply a query string
	 * and collect the results. The pageNum parameter indicates how many result
	 * pages should be processed.
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
	 * This search type works by scraping data from Bandcamp tag pages 
	 * (https://bandcamp.com/tag/your-favorite-tag) and collecting all releases 
	 * encountered on those pages.
	 * For this type query string parameter is interpreted as tag while pageNum
	 * parameter indicates how many subsequent pages should be processed to gather
	 * the releases. When loading tag pages, sort_field=date parameter is passed to URLs,
	 * which means that Bandcamp will return results sorted by publish date in 
	 * descending order.
	 * Note that it's not yet possible to search for multiple tags at once because
	 * Bandcamp tag page does not provide such functionality. Also worth noting that
	 * maximum number of tag pages Bandcamp will return is 10, so setting pages parameter
	 * to numbers > 10 makes no sense.
	 */
	TAG (true) {
		@Override
		Resource loadResource(String query, int pageNum, SearchTask searchTask) throws IOException {
			if (pageNum < 1)
				throw new IllegalArgumentException("Page number must be > 0");
			String tag = query.toLowerCase(Locale.ENGLISH).replaceAll(" ", "-");
			String url = String.format("https://bandcamp.com/tag/%1$s?page=%2$d&sort_field=date", tag, pageNum);
			return Resource.url(url, searchTask);
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
			if (protocol.equals("https") || protocol.equals("http"))
				url = query.toLowerCase(Locale.ROOT);
			else if (protocol.equals("file")) {
				checkFilePath(new URL(query).getPath());
				url = query;
			}
			else if (protocol.isEmpty())
				url = "http://" + query;
			else
				throw new IllegalArgumentException('"' + protocol + "\" protocol is not supported");

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
	 * @param pageNum a number of pages
	 * @param searchTask an instance of SearchTask that requests resource loading
	 * @return an instantiated Resource object
	 * @throws IOException if resource cannot be loaded for some reason
	 * @throws NullPointerException if query or parentTask is null
	 * @throws IllegalArgumentException generally thrown if some parameters are not
	 *         valid (the validity is implementation-dependent)
	 */
	abstract Resource loadResource(String query, int pageNum, SearchTask searchTask) throws IOException;

}
