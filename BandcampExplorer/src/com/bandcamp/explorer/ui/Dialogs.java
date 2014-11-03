package com.bandcamp.explorer.ui;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * "Quick and dirty" implementation of some necessary message dialogs due to the
 * lack of this functionality in JavaFX (and deprecation of ControlsFX Dialogs).
 * This must be replaced with standard JavaFX implementation which will be available 
 * in JDK version 8u40 (scheduled for March 2015).
 */
class Dialogs {

	private Dialogs() {}


	/**
	 * Displays a modal input box with the specified prompt and title and returns the
	 * text which user has entered in a text field on input box form.
	 * 
	 * @param promptText a prompt text
	 * @param title input box title
	 * @param owner the owner of input box window
	 * @return An instance of Optional. If "OK" button was pressed then it contains the
	 *         string entered by a user into input box text field. Otherwise, if "Cancel"
	 *         button was pressed or input box is closed, then returned optional is empty.
	 * @throws NullPointerException if prompt text, title or owner is null
	 */
	static Optional<String> inputBox(String promptText, String title, Window owner) {
		Objects.requireNonNull(promptText);

		Stage dialog = createDialog(title, owner);

		Label promptLabel = new Label(promptText);
		promptLabel.setPrefWidth(400);
		promptLabel.setWrapText(true);

		TextField inputField = new TextField();
		inputField.setPrefWidth(400);

		@SuppressWarnings("unchecked")
		Optional<String>[] result = (Optional<String>[]) new Optional[] {Optional.empty()};

		Consumer<Event> okAction = event -> {
			result[0] = Optional.of(inputField.getText());
			dialog.close();
		};
		Consumer<Event> cancelAction = event -> dialog.close();

		dialog.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
			switch (event.getCode()) {
			case ENTER:
				okAction.accept(event);
				break;
			case ESCAPE:
				cancelAction.accept(event);
				break;
			default:
			}
		});

		Button ok = new Button("OK");
		ok.setPrefWidth(80);
		ok.setOnAction(okAction::accept);
		Button cancel = new Button("Cancel");
		cancel.setPrefWidth(80);
		cancel.setOnAction(cancelAction::accept);

		VBox left = new VBox(promptLabel, inputField);
		left.setPadding(new Insets(5, 5, 5, 5));
		left.setSpacing(5);
		VBox right = new VBox(ok, cancel);
		right.setPadding(new Insets(5, 5, 5, 5));
		right.setSpacing(5);
		HBox hbox = new HBox(left, right);
		hbox.setPadding(new Insets(10, 10, 10, 10));
		hbox.setSpacing(10);

		dialog.setScene(new Scene(hbox));
		showDialog(dialog);

		return result[0];
	}


	/**
	 * Displays a modal message box with the specified message text and title.
	 * 
	 * @param message a text to display
	 * @param title message box title
	 * @param owner the owner of message box window
	 * @throws NullPointerException if message text, title or owner is null
	 */
	static void messageBox(String message, String title, Window owner) {
		Objects.requireNonNull(message);

		Stage dialog = createDialog(title, owner);

		Label messageLabel = new Label(message);
		messageLabel.setAlignment(Pos.CENTER);
		messageLabel.setPrefWidth(300);
		messageLabel.setWrapText(true);

		Consumer<Event> okAction = event -> dialog.close();

		dialog.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
			switch (event.getCode()) {
			case ENTER: case ESCAPE:
				okAction.accept(event);
				break;
			default:
			}
		});

		Button ok = new Button("OK");
		ok.setPrefWidth(80);
		ok.setOnAction(okAction::accept);

		VBox vbox = new VBox(messageLabel, ok);
		vbox.setPadding(new Insets(10, 10, 10, 10));
		vbox.setSpacing(5);
		vbox.setAlignment(Pos.CENTER);

		dialog.setScene(new Scene(vbox));
		showDialog(dialog);
	}


	/**
	 * Creates the dialog box stage with the specified title and owner.
	 * 
	 * @param title dialog box title
	 * @param owner the owner of dialog box window
	 * @return dialog box stage
	 * @throws NullPointerException if title or owner is null
	 */
	private static Stage createDialog(String title, Window owner) {
		Objects.requireNonNull(title);
		Objects.requireNonNull(owner);

		Stage dialog = new Stage();
		dialog.initOwner(owner);
		dialog.initStyle(StageStyle.UTILITY);
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setResizable(false);
		dialog.setTitle(title);

		return dialog;
	}


	/**
	 * Displays the dialog box on the screen and waits until it is closed.
	 * 
	 * @param dialog a dialog box to display
	 */
	private static void showDialog(Stage dialog) {
		dialog.sizeToScene();
		dialog.centerOnScreen();
		dialog.showAndWait();
	}

}
