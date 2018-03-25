package com.bandcamp.explorer.data;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Utility class containing some predefined predicate functions to filter
 * releases using different criteria.
 * Individual filters can be combined using default methods of 
 * {@link java.util.function.Predicate}.
 */
public class ReleaseFilters {

	private ReleaseFilters() {}


	/**
	 * Returns a filter that matches any release.
	 */
	public static Predicate<Release> any() {
		return release -> true;
	}


	/**
	 * Returns a filter that matches only releases whose artist name
	 * contains given string (case insensitive).
	 * 
	 * @param s a string that artist name must contain
	 * @throws NullPointerException if s is null
	 */
	public static Predicate<Release> artistContains(String s) {
		return release -> containsString(release.artist(), s);
	}


	/**
	 * Returns a filter that matches only releases whose title
	 * contains given string (case insensitive).
	 * 
	 * @param s a string that release title must contain
	 * @throws NullPointerException if s is null
	 */
	public static Predicate<Release> titleContains(String s) {
		return release -> containsString(release.title(), s);
	}


	/**
	 * Returns a filter that matches only releases whose URI
	 * contains given string (case insensitive).
	 * 
	 * @param s a string that URI must contain
	 * @throws NullPointerException if s is null
	 */
	public static Predicate<Release> uriContains(String s) {
		return release -> containsString(release.uri().toString(), s);
	}


	/**
	 * Helper method to check whether some release string contains another,
	 * not taking characters case into account.
	 * 
	 * @param releaseStr release string
	 * @param checkStr the string to search for within release string 
	 */
	private static boolean containsString(String releaseStr, String checkStr) {
		return releaseStr.toLowerCase(Locale.ENGLISH).contains(checkStr.toLowerCase(Locale.ENGLISH));
	}


	/**
	 * Returns a filter that matches only releases whose download type is
	 * contained by specified downloadTypes set. 
	 * 
	 * @param downloadTypes a set, containing download types to match
	 * @throws NullPointerException if downloadTypes is null
	 */
	public static Predicate<Release> byDownloadType(Set<Release.DownloadType> downloadTypes) {
		return release -> downloadTypes.contains(release.downloadType());
	}


	/**
	 * Returns a filter that matches only releases whose price satisfies
	 * a condition: min >= price <= max.
	 * If min is null, then only price <= to condition is checked. Likewise,
	 * if max is null, then only min >= price condition is checked.
	 * If both min and max values are null, then this filter will match any release.
	 * If min is greater than max, then this filter won't match anything.
	 * 
	 * @param min min price
	 * @param max max price
	 */
	public static Predicate<Release> byPrice(Price min, Price max) {
		return release -> isBetween(release.price(), min, max);
	}


	/**
	 * Returns a filter that matches only releases whose release date satisfies
	 * a condition: from >= releaseDate <= to.
	 * If from date is null, then only releaseDate <= to condition is checked. Likewise,
	 * if to date is null, then only from >= releaseDate condition is checked.
	 * If both from and to dates are null, then this filter will match any release.
	 * If from is greater than to, then this filter won't match anything.
	 * Note that some releases don't have release date. Such releases will be matched
	 * only if from = LocalDate.MIN.
	 * 
	 * @param from date value that release date must be less than or equal to
	 * @param to date value that release date must be greater than or equal to
	 */
	public static Predicate<Release> byReleaseDate(LocalDate from, LocalDate to) {
		return release -> isBetween(release.releaseDate().orElse(LocalDate.MIN), from, to);
	}


	/**
	 * Returns a filter that matches only releases whose publish date satisfies
	 * a condition: from >= publishDate <= to.
	 * If from date is null, then only publishDate <= to condition is checked. Likewise,
	 * if to date is null, then only from >= publishDate condition is checked.
	 * If both from and to dates are null, then this filter will match any release.
	 * If from is greater than to, then this filter won't match anything.
	 * 
	 * @param from date value that publish date must be less than or equal to
	 * @param to date value that publish date must be greater than or equal to
	 */
	public static Predicate<Release> byPublishDate(LocalDate from, LocalDate to) {
		return release -> isBetween(release.publishDate(), from, to);
	}


	/**
	 * Helper method to check that given comparable value is contained between two other
	 * comparable values (inclusively).
	 */
	private static <T extends Comparable<? super T>> boolean isBetween(T value, T from, T to) {
		assert value != null;
		int c1 = from != null ? from.compareTo(value) : 0;
		int c2 = to != null ? to.compareTo(value) : 0;
		return c1 <= 0 && c2 >= 0;
	}


	/**
	 * Returns a filter that matches only releases whose set of tags contains
	 * all unique strings from given collection.
	 * If collection is null, then this filter will match any release.
	 * Note that character case do matter during comparison. To ensure correct matching it
	 * is best to convert all strings in collection to lowercase
	 * using Locale.ENGLISH, before passing it to this method.
	 * 
	 * @param tags a collection of tags all of whom must be contained by release tags
	 */
	public static Predicate<Release> byTags(Collection<String> tags) {
		return release -> tags == null || release.tags().containsAll(tags);
	}

}