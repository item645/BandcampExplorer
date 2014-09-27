package com.bandcamp.explorer.data;

import java.io.IOException;
import java.util.Locale;

/**
 * Enumerates available search types, that is, a ways to find and load releases.
 */
public enum SearchType {
	
	/**
	 * This type employs a standard Bandcamp search feature to find releases,
	 * using http://bandcamp.com/search page to supply a query string
	 * and collect the results. The pages parameter indicates how many result
	 * pages should be processed.
	 * Note that it seems that Bandcamp uses Google cache for searching and does
	 * not have any internal search implementation, which means it is not guaranteed
	 * that <b>all</b> relevant results are returned.
	 */
	SEARCH(true) {
		@Override
		Page loadPage(String s, int pageNum, SearchTask parentTask) throws IOException {
			if (pageNum <= 0)
				throw new IllegalArgumentException("Page number must be > 0");
			String query = s.toLowerCase(Locale.ENGLISH).replaceAll(" ", "+");
			String url = String.format("http://bandcamp.com/search?q=%1$s&page=%2$d", query, pageNum);
			return new Page(url, parentTask);
		}
	},
	
	
	/**
	 * This search type works by browsing Bandcamp tag pages (http://bandcamp.com/tag/your-favorite-tag)
	 * and collecting all releases encountered on those pages.
	 * For this type query string parameter is interpreted as tag while pages
	 * parameter indicates how many subsequent pages should be processed to gather
	 * the releases. When loading tag pages, sort_field=date parameter is passed to URLs,
	 * which means that Bandcamp will return results sorted by publish date in 
	 * descending order.
	 * Note that it's not yet possible to search for multiple tags at once because
	 * Bandcamp tag page does not provide such functionality. Also worth noting that
	 * maximum number of tag pages Bandcamp will return is 10, so setting pages parameter
	 * to numbers > 10 makes no sense.
	 */
	TAG(true) {
		@Override
		Page loadPage(String s, int pageNum, SearchTask parentTask) throws IOException {
			if (pageNum < 1)
				throw new IllegalArgumentException("Page number must be > 0");
			String tag = s.toLowerCase(Locale.ENGLISH).replaceAll(" ", "-");
			String url = String.format("http://bandcamp.com/tag/%1$s?page=%2$d&sort_field=date", tag, pageNum);
			return new Page(url, parentTask);
		}
	},
	
	
	/**
	 * This type can be used to directly browse any page on Bandcamp to collect all releases 
	 * this page contains. This will work not only for Bandcamp, but also for
	 * page on any other website which has Bandcamp data and lists of releases embedded
	 * in it.
	 * The query string paramater should be a valid URL string, pointing to desired
	 * page. URL's protocol part is not mandatory and can be dropped, in this case
	 * browsing is done via http. The pages parameter is ignored for this search type.
	 */
	DIRECT(false) {
		@Override
		Page loadPage(String s, int pageNum, SearchTask parentTask) throws IOException {
			String url = s.toLowerCase(Locale.ROOT);
			if (!url.startsWith("http://") && !url.startsWith("https://"))
				url = "http://" + url;
			return new Page(url, parentTask);
		}
	};
	
	
	/**
	 * Indicates whether search type supports using multiple pages.
	 */
	boolean isMultiPage;
	
	
	private SearchType(boolean isMultiPage) {
		this.isMultiPage = isMultiPage;
	}
	
	
	/**
	 * Creates and loads Page object appropriate to the search type.
	 * Each enum constant has its own implementation of this method to load the page
	 * corresponding to search type. This way constants of SearchType enum work as
	 * factories for creating Page instances.
	 * 
	 * @param s a query string
	 * @param pageNum a number of pages
	 * @param parentTask an instance of SearchTask that requests a page loading
	 * @return an instantiated Page object
	 * @throws IOException if page cannot be loaded for some reason
	 * @throws NullPointerException if s or parentTask is null
	 * @throws IllegalArgumentException if pagesNum <= 0 (not thrown if search type is DIRECT)
	 */
	abstract Page loadPage(String s, int pageNum, SearchTask parentTask) throws IOException;
}
