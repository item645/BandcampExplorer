package com.bandcamp.explorer.data;

import java.util.Comparator;

/**
 * Convenience enum to provide some useful options for sorting releases.
 * At the moment this enum is used only to set a default sort order for
 * release table view, but there will likely be other uses in future versions. 
 */
public enum ReleaseSortOrder implements Comparator<Release> {

	/**
	 * Sorts releases by publish date in ascending order.
	 */
	PUBLISH_DATE_ASC {
		@Override
		public int compare(Release r1, Release r2) {
			return r1.getPublishDate().compareTo(r2.getPublishDate());
		}
	},

	/**
	 * Sorts releases by publish date in descending order.
	 */
	PUBLISH_DATE_DESC {
		@Override
		public int compare(Release r1, Release r2) {
			return r2.getPublishDate().compareTo(r1.getPublishDate());
		}
	},

	/**
	 * Sorts releases by release date in ascending order.
	 */
	RELEASE_DATE_ASC {
		@Override
		public int compare(Release r1, Release r2) {
			return r1.getReleaseDate().compareTo(r2.getReleaseDate());
		}
	},

	/**
	 * Sorts releases by release date in descending order.
	 */
	RELEASE_DATE_DESC {
		@Override
		public int compare(Release r1, Release r2) {
			return r2.getReleaseDate().compareTo(r1.getReleaseDate());
		}
	},

	/**
	 * Sorts releases by artist name and title in ascending order.
	 */
	ARTIST_AND_TITLE {
		@Override
		public int compare(Release r1, Release r2) {
			int result = r1.getArtist().compareToIgnoreCase(r2.getArtist());
			return result != 0 ? result : r1.getTitle().compareToIgnoreCase(r2.getTitle());
		}
	};


	/**
	 * Implements comparison operation. Each sort option has its own implementation.
	 */
	@Override
	public abstract int compare(Release r1, Release r2);
}
