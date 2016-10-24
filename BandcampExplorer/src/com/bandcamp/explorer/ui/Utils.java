package com.bandcamp.explorer.ui;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

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


	/**
	 * Installs a tooltip displaying textual content of the given labeled node.
	 * The tooltip will be shown only if text displayed in a node is clipped due to layout
	 * restrictions and does not equal to node's actual textual content.
	 * The tooltip is triggered by mouse entering the node and gets hidden on mouse exit.
	 * The anchorX and anchorY parameters constitute local coordinates where tooltip's
	 * upper left corner will be positioned.
	 * 
	 * @param labeledNode a labeled node to install tooltip on
	 * @param anchorX a callback function which returns the X position of the 
	 *        tooltip anchor in node's local coordinates
	 * @param anchorY a callback function which returns the Y position of the
	 *        tooltip anchor in node's local coordinates
	 */
	static void setContentTooltip(Labeled labeledNode, DoubleSupplier anchorX, DoubleSupplier anchorY) {
		assert anchorX != null;
		assert anchorY != null;
		
		labeledNode.setOnMouseEntered(enterEvent -> {
			// Little hack to determine whether the labeled node text is clipped.
			// Clipping operation does not change the actual text property of a labeled node,
			// what really gets changed instead is a labeled text (an instance of
			// com.sun.javafx.scene.control.skin.LabeledText) that is used internally 
			// by labeled skin implementation (com.sun.javafx.scene.control.skin.LabeledSkinBase)
			// to actually display its content as a styled text.
			// LabeledText has the style class "text" and is reachable via node lookup.			
			Text displayedText = (Text)labeledNode.lookup(".text");
			if (displayedText == null)
				return;
			String contentText = Objects.toString(labeledNode.getText(), "");

			// If displayed text doesn't equal actual textual content of a node,
			// then it is clipped and we need a tooltip
			if (!contentText.isEmpty() && !contentText.equals(displayedText.getText())) {
				// Get a screen point corresponding to given local coordinates X and Y
				Point2D anchorPoint = labeledNode.localToScreen(anchorX.getAsDouble(), anchorY.getAsDouble());
				// Show the tooltip
				Tooltip tooltip = new Tooltip(contentText);
				tooltip.show(labeledNode, anchorPoint.getX(), anchorPoint.getY());
				labeledNode.setOnMouseExited(exitEvent -> tooltip.hide());
			}
			else
				labeledNode.setOnMouseExited(null);
		});
	}

}
