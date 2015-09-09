package com.bandcamp.explorer.data;

/**
 * Indicates that some error has occurred during instantiation of Release object.
 * Typically the exception is caused by I/O error while loading release web page
 * or by the absence of valid data on loaded page. In that case the original exception
 * can be retrieved using {@link #getCause()} method.
 * Additionally this exception provides a means to inspect a response code from the
 * server in case the underlying exception is caused by HTTP server error. 
 */
@SuppressWarnings("serial")
public class ReleaseLoadingException extends Exception {

	private final int httpResponseCode;


	/**
	 * Constructs new ReleaseLoadingException with the specified detail message
	 * and cause.
	 * 
	 * @param message the detail message
	 * @param cause the cause of this exception
	 */
	public ReleaseLoadingException(String message, Throwable cause) {
		super(message, cause);
		this.httpResponseCode = 0;
	}


	/**
	 * Constructs new ReleaseLoadingException with the specified cause.
	 * 
	 * @param cause the cause of this exception
	 */
	public ReleaseLoadingException(Throwable cause) {
		super(cause);
		this.httpResponseCode = 0;
	}


	/**
	 * Constructs new ReleaseLoadingException with the specified cause
	 * and HTTP server response code.
	 * 
	 * @param cause the cause of this exception
	 * @param httpResponseCode the response code returned by HTTP server
	 *        in case there was an error on the server side
	 */
	public ReleaseLoadingException(Throwable cause, int httpResponseCode) {
		super(cause);
		this.httpResponseCode = httpResponseCode;
	}


	/**
	 * Returns a HTTP response code if this exception was caused by an
	 * error on the server side. If no code could be discerned from the response,
	 * returns -1.
	 * For other causes the response code is not specified and return value is 0.
	 */
	public int getHttpResponseCode() {
		return httpResponseCode;
	}

}
