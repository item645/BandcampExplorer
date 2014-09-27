package com.bandcamp.explorer.data;

import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
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
		return release -> containsString(release.getArtist(), s);
	}
	
	
	/**
	 * Returns a filter that matches only releases whose title
	 * contains given string (case insensitive).
	 * 
	 * @param s a string that release title must contain
	 * @throws NullPointerException if s is null
	 */
	public static Predicate<Release> titleContains(String s) {
		return release -> containsString(release.getTitle(), s);
	}
	
	
	/**
	 * Returns a filter that matches only releases whose URL property
	 * contains given string (case insensitive).
	 * 
	 * @param s a string that URL must contain
	 * @throws NullPointerException if s is null
	 */
	public static Predicate<Release> urlContains(String s) {
		return release -> containsString(release.getURI().toString(), s);
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
	public static Predicate<Release> byDownloadType(EnumSet<Release.DownloadType> downloadTypes) {
		return release -> downloadTypes.contains(release.getDownloadType());
	}
	
	
	/**
	 * Returns a filter that matches only releases whose release date satisfies
	 * a condition: from >= releaseDate <= to.
	 * If from date is null, then only releaseDate <= to condition is checked. Likewise,
	 * if to date is null, then only from >= releaseDate condition is checked.
	 * If both from and to dates are null, then this filter will match any release.
	 * If from is greater than to, then this filter won't match anything.
	 * Note that some releases don't have release date (in that case their release
	 * date is LocalDate.MIN). Such releases will be matched only if from = LocalDate.MIN.
	 * 
	 * @param from date value that release date must be less than or equal to
	 * @param to date value that release date must be greater than or equal to
	 */
	public static Predicate<Release> byReleaseDate(LocalDate from, LocalDate to) {
		return release -> isWithinDates(release.getReleaseDate(), from, to);
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
		return release -> isWithinDates(release.getPublishDate(), from, to);
	}
	
	
	/**
	 * Helper method to check that given date is contained within from and to date
	 * on time scale.
	 */
	private static boolean isWithinDates(LocalDate releaseDate, LocalDate from, LocalDate to) {
		int c1 = from != null ? from.compareTo(releaseDate) : 0;
		int c2 = to != null ? to.compareTo(releaseDate) : 0;
		return c1 <= 0 && c2 >= 0;
	}
	
	
	/**
	 * Returns a filter that matches only releases whose set of tags contains
	 * all strings from include collection and does not contain any string from
	 * exclude collection.
	 * If include is null, then this filter will match any release.
	 * If exclude is null, then only include strings are checked.
	 * Note that character case do matter during comparison. To ensure correct matching it
	 * is best to convert all strings in include/exclude collections to lowercase
	 * using Locale.ENGLISH, before passing them to this method.
	 * 
	 * @param include a collection of tags all of whom must be contained by release tags
	 * @param exclude a collection of tags that release must not contain any of
	 */
	public static Predicate<Release> byTags(Collection<String> include, Collection<String> exclude) {
		return release -> {
			if (include == null)
				return true;
			else {
				Set<String> tags = release.getTags();
				if (exclude != null)
					for (String ex : exclude)
						if (tags.contains(ex))
							return false;
				return tags.containsAll(include);
			}
		};
	}
	
}