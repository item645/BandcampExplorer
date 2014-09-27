package com.bandcamp.explorer.ui;

import java.awt.Desktop;
import java.net.URI;

/**
 * Contains some general utility stuff for UI.
 */
class Utils {

	private Utils() {}

	
	/**
	 * Launches the default browser and navigates to specified URI.
	 * If java.awt.Desktop is not supported, does nothing.
	 * 
	 * @param uri URI to open in a browser
	 */
	static void browse(URI uri) {
		if (Desktop.isDesktopSupported()) {
			try {
				Desktop.getDesktop().browse(uri);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
}
