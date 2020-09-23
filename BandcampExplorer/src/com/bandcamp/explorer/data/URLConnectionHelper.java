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
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.bandcamp.explorer.util.ExceptionUnchecker;

/**
 * A helper class for creating URL connections.
 * Unlike the regular approach this class handles HTTP redirects automatically,
 * including cross-protocol redirects (HTTP -> HTTPS) which standard Java implementation
 * cannot do due to some security limitations (http://bugs.java.com/bugdatabase/view_bug.do?bug_id=4620571).
 * Also, this class sets some default properties for created connections and allows to retry 
 * HTTPS connection with SSL certificate checks disabled in case of SSL certificate 
 * validation failure.
 */
class URLConnectionHelper {

	private URLConnectionHelper() {}

	private static final Logger LOGGER = Logger.getLogger(URLConnectionHelper.class.getName());

	private static final int HTTP_CONNECT_TIMEOUT_MLS = 60000;
	private static final int HTTP_READ_TIMEOUT_MLS    = 60000;

	private static final int HTTP_MAX_REDIRECTS = 10;

	/**
	 * Custom SSL socket factory that skips SSL certificate validation by trusting
	 * any certificate automatically.
	 */
	private static final SSLSocketFactory allTrustingSSLSocketFactory;
	static {
		TrustManager allTrustingTrustManager = new X509TrustManager() {
			@Override public X509Certificate[] getAcceptedIssuers() {return null;}
			@Override public void checkServerTrusted(X509Certificate[] chain, String authType) 
					throws CertificateException {}
			@Override public void checkClientTrusted(X509Certificate[] chain, String authType) 
					throws CertificateException {}
		};
		allTrustingSSLSocketFactory = ExceptionUnchecker.uncheck(() -> {
			SSLContext sslCtx = SSLContext.getInstance("SSL");
			sslCtx.init(null, new TrustManager[] {allTrustingTrustManager}, new SecureRandom());
			return sslCtx.getSocketFactory();
		});
	}


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
		return getConnection(uri.toURL(), false);
	}


	/**
	 * Creates a connection for the specified URI.
	 * If connecting to this URI results in redirect to a new location, then another
	 * connection will be created automatically for given location, and so on until
	 * a non-redirecting response is received. The returned connection instance corresponds
	 * to the first non-redirecting URI in this sequence.
	 * 
	 * @param uri a URI to connect to
	 * @param retryWithNoCheckOnCertFail if true, performs second attempt to create a connection
	 *        with SSL certificate checks disabled in case of SSL certificate validation failure;
	 *        this parameter is used only for HTTPS connections
	 * @return a connection instance
	 * @throws IOException if I/O-, network- or protocol-related error occurs while
	 *         instantiating a connection, or if specified URI results in malformed URL
	 * @throws ClassCastException if created connection cannot be cast to the appropriate
	 *         return type
	 * @throws NullPointerException if URI is null
	 */
	static <T extends URLConnection> T getConnection(URI uri, boolean retryWithNoCheckOnCertFail) 
			throws IOException {
		return getConnection(uri.toURL(), retryWithNoCheckOnCertFail);
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
		return getConnection(url, false);
	}


	/**
	 * Creates a connection for the specified URL.
	 * If connecting to this URL results in redirect to a new location, then another
	 * connection will be created automatically for given location, and so on until
	 * a non-redirecting response is received. The returned connection instance corresponds
	 * to the first non-redirecting URL in this sequence.
	 * 
	 * @param url a URL to connect to
	 * @param retryWithNoCheckOnCertFail if true, performs second attempt to create a connection
	 *        with SSL certificate checks disabled in case of SSL certificate validation failure;
	 *        this parameter is used only for HTTPS connections
	 * @return a connection instance
	 * @throws IOException if I/O-, network- or protocol-related error occurs while
	 *         instantiating a connection
	 * @throws ClassCastException if created connection cannot be cast to the appropriate
	 *         return type
	 * @throws NullPointerException if URL is null
	 */
	static <T extends URLConnection> T getConnection(URL url, boolean retryWithNoCheckOnCertFail)
			throws IOException {
		String protocol = getProtocol(url);
		if (protocol.equals("file"))
			return cast(url.openConnection());
		else if (protocol.equals("http") || protocol.equals("https"))
			return cast(getHttpConnection(url, retryWithNoCheckOnCertFail));
		else
			throw new IOException('"' + protocol + "\" protocol is not supported");
	}


	/**
	 * Creates an HTTP connection for the specified URL.
	 * 
	 * @param url a URL to connect to
	 * @param retryWithNoCheckOnCertFail if true, performs second attempt to create a connection
	 *        with SSL certificate checks disabled in case of SSL certificate validation failure;
	 *        this parameter is used only for HTTPS connections
	 * @return an HTTP connection instance
	 * @throws IOException if I/O-, network- or protocol-related error occurs while
	 *         instantiating a connection
	 */
	private static HttpURLConnection getHttpConnection(URL url,  boolean retryWithNoCheckOnCertFail) 
			throws IOException{
		HttpURLConnection httpConnection;
		int redirects = 0;

		while (true) {
			httpConnection = createHttpConnection(url);
			String protocol = getProtocol(url);
			int responseCode;

			try {
				responseCode = httpConnection.getResponseCode();
			}
			catch (SSLHandshakeException sslEx) {
				assert protocol.equals("https");
				if (Objects.toString(sslEx.getMessage()).contains("SunCertPathBuilderException") 
						&& retryWithNoCheckOnCertFail) {
					String msg = String.format(
							"SSL certificate validation failed for %s. Retrying with SSL certificate checking disabled...",
							url);
					LOGGER.fine(msg);
					// Retry with disabled SSL validation
					HttpsURLConnection httpsConnection = (HttpsURLConnection)createHttpConnection(url);
					httpsConnection.setSSLSocketFactory(allTrustingSSLSocketFactory);
					httpsConnection.setHostnameVerifier((hostname, session) -> true);
					httpConnection = httpsConnection;
					responseCode = httpConnection.getResponseCode();
				}
				else {
					throw sslEx;
				}
			}

			switch (responseCode) {
			case HTTP_MOVED_PERM: case HTTP_MOVED_TEMP: case HTTP_SEE_OTHER:
				if (++redirects <= HTTP_MAX_REDIRECTS) {
					// If we are redirected, open connection for a new URL
					URL oldUrl = url;
					URL newUrl = new URL(oldUrl, httpConnection.getHeaderField("Location"));
					int respCode = responseCode;
					LOGGER.finest(() -> {
						return new StringBuilder("Redirect: ").append(oldUrl).append(" -> ")
								.append(newUrl).append(" (code: ").append(respCode).append(')')
								.toString();
					});
					url = newUrl;
					continue;
				}
				else {
					throw new ProtocolException("Max redirects limit exceeded (" + HTTP_MAX_REDIRECTS + ')');
				}
			}
			break;
		}

		return httpConnection;
	}


	/**
	 * Gets protocol string from the URL.
	 * 
	 * @param url a URL
	 * @return protocol string (lowercased)
	 */
	private static String getProtocol(URL url) {
		return url.getProtocol().toLowerCase(Locale.ROOT);
	}


	/**
	 * Creates new HTTP connection and sets some default properties for it.
	 * 
	 * @param url a URL to connect to
	 * @return an HTTP connection
	 * @throws IOException if I/O-, network- or protocol-related error occurs while
	 *         instantiating a connection
	 */
	private static HttpURLConnection createHttpConnection(URL url) throws IOException {
		HttpURLConnection httpConnection = (HttpURLConnection)url.openConnection();
		httpConnection.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MLS);
		httpConnection.setReadTimeout(HTTP_READ_TIMEOUT_MLS);
		// Disable auto redirects within the same protocol because we will be
		// handling all redirects manually
		httpConnection.setInstanceFollowRedirects(false);
		return httpConnection;
	}


	/**
	 * Casts URLConnection to a target type.
	 */
	@SuppressWarnings("unchecked")
	private static <T> T cast(URLConnection connection) {
		return (T)connection;
	}

}
