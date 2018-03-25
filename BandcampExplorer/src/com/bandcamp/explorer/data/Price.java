package com.bandcamp.explorer.data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents release price in USD.
 */
public final class Price implements Comparable<Price> {

	private static final Pattern PRICE_PATTERN = Pattern.compile("\\$?\\d+(\\.\\d+)?");
	private static final String CURRENCY_SYMBOL = "$";
	private static final int SCALE = 2;

	private static final BigDecimal ZERO_VALUE = new BigDecimal("0.00");
	static final Price ZERO = new Price(ZERO_VALUE);

	private final BigDecimal value;


	/**
	 * Constructs new instance of Price for the specified value.
	 */
	private Price(BigDecimal value) {
		this.value = value;
	}


	/**
	 * Creates a Price object from valid string representation.
	 * Resulting price value is scaled to 2 decimal places using provided rounding mode.
	 * 
	 * @param text a string representation of price
	 * @param roundingMode rounding mode for value scaling
	 * @return a price object
	 * @throws NullPointerException if text or rounding mode is null
	 * @throws IllegalArgumentException if string representation or price value is not valid
	 *         or if RoundingMode.UNNECESSARY has been specified and the result of rounding
	 *         is not exact
	 */
	public static Price parse(String text, RoundingMode roundingMode) {
		Objects.requireNonNull(text);
		Objects.requireNonNull(roundingMode);

		if (!PRICE_PATTERN.matcher(text).matches())
			throw new IllegalArgumentException("Invalid string representation of price: " + text);

		if (text.startsWith(CURRENCY_SYMBOL))
			text = text.substring(CURRENCY_SYMBOL.length());
		try {
			return of(new BigDecimal(text), roundingMode);
		}
		catch (ArithmeticException e) {
			throw new IllegalArgumentException(e);
		}
	}


	/**
	 * Creates a Price object for the specified value.
	 * The value is always scaled to 2 decimal places using "half up" rounding mode.
	 * 
	 * @param value price value
	 * @return a price object
	 * @throws NullPointerException if price value is null
	 * @throws IllegalArgumentException if price value is not valid
	 */
	static Price of(BigDecimal value) {
		return of(value, RoundingMode.HALF_UP);
	}


	/**
	 * Creates a Price object for the specified value.
	 * The value is scaled to 2 decimal places using provided rounding mode.
	 * 
	 * @param value price value
	 * @param roundingMode rounding mode for value scaling
	 * @return a price object
	 * @throws NullPointerException if price value or rounding mode is null
	 * @throws IllegalArgumentException if price value is not valid
	 * @throws ArithmeticException if RoundingMode.UNNECESSARY has been specified
	 *         and the result of rounding is not exact
	 */
	private static Price of(BigDecimal value, RoundingMode roundingMode) {
		if (value.signum() == -1)
			throw new IllegalArgumentException("Price value is negative: " + value);

		value = value.setScale(SCALE, roundingMode);
		return value.equals(ZERO_VALUE) ? ZERO : new Price(value);
	}


	/**
	 * Returns the value of this price.
	 */
	public BigDecimal value() {
		return value;
	}


	/**
	 * Returns a hash code for this price.
	 */
	@Override
	public int hashCode() {
		return value.hashCode();
	}


	/**
	 * Tests this price for equality with other, using value.
	 */
	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof Price 
				&& Objects.equals(value, ((Price)other).value);
	}


	/**
	 * Compares this price to another by value.
	 */
	@Override
	public int compareTo(Price other) {
		return value.compareTo(other.value);
	}


	/**
	 * Returns a string representation of this price.
	 */
	@Override
	public String toString() {
		return CURRENCY_SYMBOL + value;
	}

}
