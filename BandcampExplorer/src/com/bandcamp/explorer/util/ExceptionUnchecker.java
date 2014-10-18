package com.bandcamp.explorer.util;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * This class provides a handy way to convert checked exceptions in arbitrary
 * blocks of code into unchecked exceptions.
 * To perform a conversion the block of code must be wrapped into an instance of
 * Callable or VoidCallable, ideally using lambda expression, and passed to one
 * of uncheck() methods of this class.
 */
public class ExceptionUnchecker {

	private ExceptionUnchecker() {}


	/**
	 * A functional interface that can be used to wrap a block of code,
	 * not returning a value, to be passed into appropriate uncheck() method.
	 */
	@FunctionalInterface
	public interface VoidCallable {

		/**
		 * Executes a wrapped code not returning any result.
		 */
		void call() throws Exception;


		/**
		 * Converts this VoidCallable into a Callable with return type of Void.
		 */
		default Callable<Void> toCallable() {
			return () -> {call(); return null;};
		}
	}


	/**
	 * Executes a code block passed as instance of VoidCallable and converts
	 * any checked exception thrown from this code into a RuntimeException.
	 * The original checked exception can be retrieved using getCause() method
	 * of RuntimeException.
	 * 
	 * @param vc a block of code wrapped in VoidCallable
	 * @throws NullPointerException if passed VoidCallable is null
	 */
	public static void uncheck(VoidCallable vc) {
		uncheck(vc.toCallable(), RuntimeException::new);
	}


	/**
	 * Executes a code block passed as instance of VoidCallable and converts
	 * any checked exception thrown from this code into unchecked exception
	 * using supplied conversion function.
	 * 
	 * @param vc a block of code wrapped in VoidCallable
	 * @param converter conversion function that takes checked exception
	 *        as its argument and produces unchecked exception as a result
	 * @throws NullPointerException if passed VoidCallable or conversion
	 *         function is null
	 */
	public static <E extends RuntimeException> void uncheck(VoidCallable vc,
			Function<Exception, E> converter) {
		uncheck(vc.toCallable(), converter);
	}


	/**
	 * Executes a code block passed as instance of Callable and converts
	 * any checked exception thrown from this code into a RuntimeException.
	 * The original checked exception can be retrieved using getCause() method
	 * of RuntimeException.
	 * If code block completes normally, returns the result of its execution.
	 * 
	 * @param c a block of code wrapped in Callable
	 * @throws NullPointerException if passed Callable is null
	 */
	public static <T> T uncheck(Callable<T> c) {
		return uncheck(c, RuntimeException::new);
	}


	/**
	 * Executes a code block passed as instance of Callable and converts
	 * any checked exception thrown from this code into unchecked exception
	 * using supplied conversion function.
	 * If code block completes normally, returns the result of its execution.
	 * 
	 * @param c a block of code wrapped in Callable
	 * @param converter conversion function that takes checked exception
	 *        as its argument and produces unchecked exception as a result
	 * @throws NullPointerException if passed Callable or conversion
	 *         function is null
	 */
	public static <T, E extends RuntimeException> T uncheck(
			Callable<T> c, Function<Exception, E> converter) {
		Objects.requireNonNull(converter);
		try {
			return c.call();
		}
		catch (RuntimeException e) {
			throw e;
		}
		catch (Exception e) {
			throw converter.apply(e);
		}
	}

}

