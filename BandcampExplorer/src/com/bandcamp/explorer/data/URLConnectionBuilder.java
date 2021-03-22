package com.bandcamp.explorer.data;

import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
 * A helper base class for creating URL connections through the use of builders
 * for specialized connection types.
 */
abstract class URLConnectionBuilder {

	private static final Logger LOGGER = Logger.getLogger(URLConnectionBuilder.class.getName());

	private final URL url;


	/**
	 * Creates an instance of connection builder for the specified URL.
	 */
	private URLConnectionBuilder(URL url) {
		this.url = checkProtocol(Objects.requireNonNull(url));
	}


	/**
	 * Creates an instance of connection builder for the specified URI.
	 */
	private URLConnectionBuilder(URI uri) {
		this(ExceptionUnchecker.uncheck(() -> uri.toURL(), IllegalArgumentException::new));
	}


	/**
	 * Returns an URL for this builder.
	 */
	URL url() {
		return url;
	}


	/**
	 * Helper method to check the supported protocol type for each implementation.
	 */
	abstract URL checkProtocol(URL url);


	/**
	 * Builds the connection.
	 * Default implementation simply returns a connection instance for the URL
	 * with no additional configuration.
	 * 
	 * @return a connection instance
	 * @throws IOException if I/O-, network- or protocol-related error occurs while
	 *         instantiating a connection
	 */
	URLConnection build() throws IOException {
		return url.openConnection();
	}



	/**
	 * Builds the connection for connecting to a file using a "file" protocol.
	 */
	static class FileURLConnectionBuilder extends URLConnectionBuilder {

		/**
		 * Creates an instance of connection builder for the specified file URL.
		 */
		private FileURLConnectionBuilder(URL url) {
			super(url);
		}


		/**
		 * Creates an instance of connection builder for the specified file URI.
		 */
		private FileURLConnectionBuilder(URI uri) {
			super(uri);
		}


		/**
		 * {@inheritDoc}
		 * This implementation only allows "file" protocol.
		 */
		@Override
		URL checkProtocol(URL url) {
			String protocol = getProtocol(url);
			if (!protocol.equals("file"))
				throw new IllegalArgumentException(String.format("Invalid protocol for file connection: %s", protocol));
			return url;
		}
	}



	/**
	 * Builds the connection for connecting to a HTTP resource using "http" and "https" protocols.
	 * Unlike the regular approach this builder internally handles HTTP redirects,
	 * including cross-protocol redirects (HTTP -> HTTPS) which standard Java implementation
	 * cannot do due to some security limitations (http://bugs.java.com/bugdatabase/view_bug.do?bug_id=4620571).
	 * Also, this class retries HTTPS connection with SSL certificate checks disabled in case of SSL certificate 
	 * validation failure.
	 */
	static class HttpURLConnectionBuilder extends URLConnectionBuilder {

		/**
		 * Custom SSL socket factory that skips SSL certificate validation by trusting
		 * any certificate.
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
		 * Supported request methods.
		 */
		enum Method {GET, POST}


		private int connectTimeout = 60000;
		private int readTimeout = 60000;
		private int maxRedirects = 10;
		private Method method = Method.GET;
		private String postData;
		private Map<String, String> headers;


		/**
		 * Creates an instance of connection builder for the specified HTTP or HTTPS URL.
		 */
		private HttpURLConnectionBuilder(URL url) {
			super(url);
		}


		/**
		 * Creates an instance of connection builder for the specified HTTP or HTTPS URI.
		 */
		private HttpURLConnectionBuilder(URI uri) {
			super(uri);
		}


		/**
		 * Sets a connect timeout value for connection.
		 * Default is 60000 mls.
		 * 
		 * @param connectTimeout connect timeout, cannot be negative
		 */
		HttpURLConnectionBuilder connectTimeout(int connectTimeout) {
			if (connectTimeout < 0)
				throw new IllegalArgumentException(String.format("connectTimeout is negative: %d", connectTimeout));
			this.connectTimeout = connectTimeout;
			return this;
		}


		/**
		 * Sets a read timeout value for connection.
		 * Default is 60000 mls.
		 * 
		 * @param readTimeout read timeout, cannot be negative
		 */
		HttpURLConnectionBuilder readTimeout(int readTimeout) {
			if (readTimeout < 0)
				throw new IllegalArgumentException(String.format("readTimeout is negative: %d", readTimeout));
			this.readTimeout = readTimeout;
			return this;
		}


		/**
		 * Sets max number of redirects allowed for connection.
		 * Default is 10.
		 * 
		 * @param maxRedirects max number of redirects, cannot be negative
		 */
		HttpURLConnectionBuilder maxRedirects(int maxRedirects) {
			if (maxRedirects < 0)
				throw new IllegalArgumentException(String.format("maxRedirects is negative: %d", maxRedirects));
			this.maxRedirects = maxRedirects;
			return this;
		}


		/**
		 * Sets a request method for connection.
		 * 
		 * @param method request method, cannot be null
		 */
		HttpURLConnectionBuilder method(Method method) {
			this.method = Objects.requireNonNull(method);
			return this;
		}


		/**
		 * Sets a body for POST request.
		 */
		HttpURLConnectionBuilder postData(String postData) {
			this.postData = postData;
			return this;
		}


		/**
		 * Add request header with specified key and value.
		 * If header with the key already exists, overwrites its value with the new value.
		 * 
		 * @param key header key, cannot be null
		 * @param value header value for the key
		 */
		HttpURLConnectionBuilder header(String key, String value) {
			Objects.requireNonNull(key, "Header key is null");
			if (headers == null)
				headers = new HashMap<>();
			headers.put(key, value);
			return this;
		}


		/**
		 * {@inheritDoc}
		 * This implementation only allows "http" and "https" protocols.
		 */
		@Override
		URL checkProtocol(URL url) {
			String protocol = getProtocol(url);
			if (!protocol.equals("https") && !protocol.equals("http"))
				throw new IllegalArgumentException(String.format("Invalid protocol for HTTP connection: %s", protocol));
			return url;
		}


		/**
		 * Builds the connection for HTTP or HTTPS URL.
		 * If connecting to the URL results in redirect to a new location, then another
		 * connection will be created automatically for given location, and so on until
		 * a non-redirecting response is received. The returned connection instance corresponds
		 * to the first non-redirecting URL in this sequence.
		 * Successfully instantiated connection is always in connected state.
		 * 
		 * @return an HTTP connection instance
		 * @throws IOException if I/O-, network- or protocol-related error occurs while
		 *         instantiating a connection
		 */
		@Override
		HttpURLConnection build() throws IOException {
			HttpURLConnection httpConnection;
			URL currentURL = url();
			int redirects = 0;

			while (true) {
				httpConnection = createHttpConnection(currentURL);
				String protocol = getProtocol(currentURL);
				int responseCode;

				try {
					responseCode = httpConnection.getResponseCode();
				}
				catch (SSLHandshakeException sslEx) {
					assert protocol.equals("https");
					if (Objects.toString(sslEx.getMessage()).contains("SunCertPathBuilderException")) {
						logSSLFailRetry(currentURL);
						// Retry with disabled SSL validation
						HttpsURLConnection httpsConnection = (HttpsURLConnection)createHttpConnection(currentURL);
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
					if (++redirects <= maxRedirects) {
						// If we are redirected, open connection for a new URL
						URL newUrl = new URL(currentURL, httpConnection.getHeaderField("Location"));
						logRedirect(currentURL, newUrl, responseCode);
						currentURL = newUrl;
						continue;
					}
					else {
						throw new ProtocolException("Max redirects limit exceeded (" + maxRedirects + ')');
					}
				}
				break;
			}

			return httpConnection;

		}


		/**
		 * Creates new HTTP connection and sets some properties for it.
		 * 
		 * @param url a URL to connect to
		 * @return an HTTP connection
		 * @throws IOException if I/O-, network- or protocol-related error occurs while
		 *         instantiating a connection
		 */
		private HttpURLConnection createHttpConnection(URL url) throws IOException {
			HttpURLConnection httpConnection = (HttpURLConnection)url.openConnection();
			httpConnection.setConnectTimeout(connectTimeout);
			httpConnection.setReadTimeout(readTimeout);
			httpConnection.setRequestMethod(method.toString());
			if (headers != null)
				headers.forEach(httpConnection::setRequestProperty);

			// Disable auto redirects within the same protocol because we will be
			// handling all redirects manually
			httpConnection.setInstanceFollowRedirects(false);

			if (method == Method.POST) {
				httpConnection.setDoOutput(true);
				try (OutputStream out = httpConnection.getOutputStream()) {
					out.write(Objects.toString(postData, "").getBytes(StandardCharsets.UTF_8));
				}
			}

			return httpConnection;
		}


		/**
		 * Logs a redirect from oldURL to newURL with specified response code.
		 */
		private static void logRedirect(URL oldURL, URL newURL, int responseCode) {
			LOGGER.finest(() -> {
				return new StringBuilder("Redirect: ")
						.append(oldURL).append(" -> ")
						.append(newURL).append(" (code: ")
						.append(responseCode).append(')')
						.toString();
			});
		}


		/**
		 * Logs an attempt to retry connection with SSL certificate checking disabled.
		 */
		private static void logSSLFailRetry(URL url) {
			String msg = "SSL certificate validation failed for %s. Retrying with SSL certificate checking disabled...";
			LOGGER.fine(String.format(msg, url));
		}

	}


	/**
	 * Gets protocol string from the URL.
	 * 
	 * @param url a URL
	 * @return protocol string (lowercased)
	 */
	static String getProtocol(URL url) {
		return url.getProtocol().toLowerCase(Locale.ROOT);
	}


	/**
	 * Returns a builder for creating a connection to connect to a file with a given URL.
	 */
	static FileURLConnectionBuilder newFileURLConnection(URL url) {
		return new FileURLConnectionBuilder(url);
	}


	/**
	 * Returns a builder for creating a connection to connect to a file with a given URI.
	 */
	static FileURLConnectionBuilder newFileURLConnection(URI uri) {
		return new FileURLConnectionBuilder(uri);
	}


	/**
	 * Returns a builder for creating a connection to connect to a HTTP resource with a given URL.
	 */
	static HttpURLConnectionBuilder newHttpURLConnection(URL url) {
		return new HttpURLConnectionBuilder(url);
	}


	/**
	 * Returns a builder for creating a connection to connect to a HTTP resource with a given URI.
	 */
	static HttpURLConnectionBuilder newHttpURLConnection(URI uri) {
		return new HttpURLConnectionBuilder(uri);
	}

}
