package com.bandcamp.explorer.data;

import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * A helper class for creating URL connections.
 * Unlike the regular approach this class handles HTTP redirects automatically,
 * including cross-protocol redirects (HTTP -> HTTPS) which standard Java implementation
 * cannot do due to some security limitations (http://bugs.java.com/bugdatabase/view_bug.do?bug_id=4620571).
 * Also, this class sets some default properties for created connections.
 */
class URLConnectionHelper {

	private URLConnectionHelper() {}

	private static final Logger LOGGER = Logger.getLogger(URLConnectionHelper.class.getName());

	private static final int HTTP_CONNECT_TIMEOUT_MLS = 60000;
	private static final int HTTP_READ_TIMEOUT_MLS    = 60000;

	private static final int HTTP_MAX_REDIRECTS = 10;


	/**
	 * Creates a connection for the specified URI.
	 * If connecting to this URI results in redirect to a new location, then another
	 * connection will be created automatically for given location, and so on until
	 * a non-redirecting response is received. The returned connection instance corresponds
	 * to the first non-redirecting URI in this sequence.
	 * 
	 * @param uri a URI to connect to
	 * @return a connection instance
	 * @throws IOException if I/O-, network- or protocol-related error occurs while
	 *         instantiating a connection, or if specified URI results in malformed URL
	 * @throws ClassCastException if created connection cannot be cast to the appropriate
	 *         return type
	 * @throws NullPointerException if URI is null
	 */
	static <T extends URLConnection> T getConnection(URI uri) throws IOException {
		return getConnection(uri.toURL());
	}


	/**
	 * Creates a connection for the specified URL.
	 * If connecting to this URL results in redirect to a new location, then another
	 * connection will be created automatically for given location, and so on until
	 * a non-redirecting response is received. The returned connection instance corresponds
	 * to the first non-redirecting URL in this sequence.
	 * 
	 * @param url a URL to connect to
	 * @return a connection instance
	 * @throws IOException if I/O-, network- or protocol-related error occurs while
	 *         instantiating a connection
	 * @throws ClassCastException if created connection cannot be cast to the appropriate
	 *         return type
	 * @throws NullPointerException if URL is null
	 */
	static <T extends URLConnection> T getConnection(URL url) throws IOException {
		String protocol = url.getProtocol().toLowerCase(Locale.ROOT);
		if (protocol.equals("file"))
			return cast(url.openConnection());
		else if (protocol.equals("http") || protocol.equals("https")) {
			HttpURLConnection connection;
			int redirects = 0;
			while (true) {
				connection = (HttpURLConnection)url.openConnection();
				connection.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MLS);
				connection.setReadTimeout(HTTP_READ_TIMEOUT_MLS);
				// Disable auto redirects within the same protocol because we will be
				// handling all redirects manually
				connection.setInstanceFollowRedirects(false);

				int responseCode = connection.getResponseCode();
				switch (responseCode) {
				case HTTP_MOVED_PERM: case HTTP_MOVED_TEMP: case HTTP_SEE_OTHER:
					if (++redirects <= HTTP_MAX_REDIRECTS) {
						// If we are redirected, open connection for a new URL
						URL oldUrl = url;
						URL newUrl = new URL(oldUrl, connection.getHeaderField("Location"));
						LOGGER.finest(() -> {
							return new StringBuilder("Redirect: ").append(oldUrl).append(" -> ")
									.append(newUrl).append(" (code: ").append(responseCode).append(')')
									.toString();
						});
						url = newUrl;
						continue;
					}
					else
						throw new ProtocolException("Max redirects limit exceeded (" + HTTP_MAX_REDIRECTS + ')');
				}
				break;
			}
			return cast(connection);
		}
		else
			throw new IOException('"' + protocol + "\" protocol is not supported");
	}


	/**
	 * Casts URLConnection to a target type.
	 */
	@SuppressWarnings("unchecked")
	private static <T> T cast(URLConnection connection) {
		return (T)connection;
	}

}
