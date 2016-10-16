package com.bandcamp.explorer.ui;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javafx.fxml.FXMLLoader;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Region;

import com.bandcamp.explorer.BandcampExplorer;
import com.bandcamp.explorer.util.ExceptionUnchecker;

/**
 * Contains some general utility stuff for UI.
 */
class Utils {

	private Utils() {}


	/**
	 * Loads a custom UI component from FXML at specified location
	 * using supplied instantiation function.
	 * The component object returned by instantiator is assigned as both
	 * controller and root of the object hierarchy defined by FXML, so
	 * its class must match the class specified by fx:root type FXML attribute
	 * and also must be a direct subclass of {@link javafx.scene.layout.Region} or one of its
	 * subclasses.
	 * 
	 * @param location an URL to load FXML from
	 * @param instantiator a function that returns instantiated component object,
	 *        which is then gets injected by controls hierarchy from FXML and set up
	 *        as a root of that hierarchy
	 * @return a component object with all of its associated controls as defined
	 *         by FXML
	 * @throws NullPointerException if location is null or instantiator is null
	 *         or if instantiator returns null when invoked
	 * @throws RuntimeException wraps any checked exceptions that take place during
	 *         the load of FXML
	 */
	static <T extends Region> T loadFXMLComponent(URL location, Supplier<T> instantiator) {
		return ExceptionUnchecker.uncheck(() -> {
			try (InputStream input = location.openStream()) {
				return loadFXMLComponent(input, instantiator);
			}
		});
	}


	/**
	 * Loads a custom UI component from FXML contained in a specified input stream
	 * using supplied instantiation function.
	 * The component object returned by instantiator is assigned as both
	 * controller and root of the object hierarchy defined by FXML, so
	 * its class must match the class specified by fx:root type FXML attribute
	 * and also must be a direct subclass of {@link javafx.scene.layout.Region} or one of its
	 * subclasses.
	 * 
	 * @param input an input stream to read FXML from
	 * @param instantiator a function that returns instantiated component object,
	 *        which is then gets injected by controls hierarchy from FXML and set up
	 *        as a root of that hierarchy
	 * @return a component object with all of its associated controls as defined
	 *         by FXML
	 * @throws NullPointerException if input is null or instantiator is null
	 *         or if instantiator returns null when invoked
	 * @throws RuntimeException wraps any checked exceptions that take place during
	 *         the load of FXML
	 */
	static <T extends Region> T loadFXMLComponent(InputStream input, Supplier<T> instantiator) {
		Objects.requireNonNull(input);
		T component = Objects.requireNonNull(instantiator.get());

		FXMLLoader loader = new FXMLLoader();
		loader.setRoot(component);
		loader.setController(component);
		ExceptionUnchecker.uncheck(() -> loader.load(input));

		return component;
	}


	/**
	 * Launches the default browser and navigates to the specified URI.
	 * 
	 * @param uri URI to open in a browser
	 * @throws NullPointerException if uri is null
	 */
	static void browse(URI uri) {
		browse(uri.toString());
	}


	/**
	 * Launches the default browser and navigates to the specified URL string.
	 * 
	 * @param urlStr an URL string to open in a browser
	 * @throws NullPointerException if urlStr is null
	 */
	static void browse(String urlStr) {
		BandcampExplorer.hostServices().showDocument(Objects.requireNonNull(urlStr));
	}


	/**
	 * Converts specified object to its string representation and
	 * puts result string into a system clipboard.
	 * Does nothing if object is null.
	 * 
	 * @param obj an object to put into a clipboard
	 */
	static void toClipboardAsString(Object obj) {
		if (obj != null) {
			ClipboardContent content = new ClipboardContent();
			content.putString(obj.toString());
			Clipboard.getSystemClipboard().setContent(content);
		}
	}


	/**
	 * Converts the specified data taken from each element in collection to string,
	 * concatenates resulting strings using delimiter and puts into a system clipboard. 
	 * 
	 * @param items a collection of elements
	 * @param mapper a mapping function that produces textual content to be copied,
	 *        deriving it from the collection element
	 * @param delimiter the delimiter to be used to separate strings created from
	 *        individual elements
	 */
	static <T> void toClipboardAsString(Collection<T> items, Function<T, String> mapper, CharSequence delimiter) {
		toClipboardAsString(items.stream().map(mapper).collect(Collectors.joining(delimiter)));
	}
	
}
