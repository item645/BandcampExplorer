package com.bandcamp.explorer.data;

import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Comparator.comparing;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * Convenience enum to provide some useful options for sorting releases.
 * At the moment this enum is used only to set a default sort order for
 * search result, but there will likely be other uses in future versions. 
 */
public enum ReleaseSortOrder implements Comparator<Release> {

	/**
	 * Sorts releases by publish date in ascending order.
	 */
	PUBLISH_DATE_ASC(comparing(Release::publishDate)),


	/**
	 * Sorts releases by publish date in descending order.
	 */
	PUBLISH_DATE_DESC(PUBLISH_DATE_ASC.reversed()),


	/**
	 * Sorts releases by release date in ascending order.
	 */
	RELEASE_DATE_ASC(comparing(release -> release.releaseDate().orElse(LocalDate.MIN))),


	/**
	 * Sorts releases by release date in descending order.
	 */
	RELEASE_DATE_DESC(RELEASE_DATE_ASC.reversed()),


	/**
	 * Sorts releases by artist and title in ascending order, ignoring case differences.
	 */
	ARTIST_AND_TITLE(comparing(Release::artist, CASE_INSENSITIVE_ORDER)
			.thenComparing(comparing(Release::title, CASE_INSENSITIVE_ORDER)));


	private final Comparator<Release> comparator;


	ReleaseSortOrder(Comparator<Release> comparator) {
		this.comparator = comparator;
	}


	/** 
	 * Compares two releases for order.
	 * Returns a negative integer, zero, or a positive integer as the first release
	 * is less than, equal to, or greater than the second.
	 * Each enum constant uses its own way for comparison.
	 */
	@Override
	public int compare(Release r1, Release r2) {
		return comparator.compare(r1, r2);
	}

}
