package com.bandcamp.explorer.data;

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
	PUBLISH_DATE_ASC {
		@Override
		public int compare(Release r1, Release r2) {
			return r1.publishDate().compareTo(r2.publishDate());
		}
	},

	/**
	 * Sorts releases by publish date in descending order.
	 */
	PUBLISH_DATE_DESC {
		@Override
		public int compare(Release r1, Release r2) {
			return r2.publishDate().compareTo(r1.publishDate());
		}
	},

	/**
	 * Sorts releases by release date in ascending order.
	 */
	RELEASE_DATE_ASC {
		@Override
		public int compare(Release r1, Release r2) {
			return r1.releaseDate().compareTo(r2.releaseDate());
		}
	},

	/**
	 * Sorts releases by release date in descending order.
	 */
	RELEASE_DATE_DESC {
		@Override
		public int compare(Release r1, Release r2) {
			return r2.releaseDate().compareTo(r1.releaseDate());
		}
	},

	/**
	 * Sorts releases by artist name and title in ascending order.
	 */
	ARTIST_AND_TITLE {
		@Override
		public int compare(Release r1, Release r2) {
			int result = r1.artist().compareToIgnoreCase(r2.artist());
			return result != 0 ? result : r1.title().compareToIgnoreCase(r2.title());
		}
	};

}
