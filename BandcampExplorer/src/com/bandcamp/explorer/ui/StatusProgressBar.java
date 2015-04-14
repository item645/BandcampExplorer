package com.bandcamp.explorer.ui;

import java.util.concurrent.Executor;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Controller class for StatusProgressBar component.
 * StatusProgressBar is a progress bar window that is capable of displaying a
 * status text corresponding to the current state of operation,
 * optionally allowing to cancel the operation using Cancel button or by
 * closing progress bar window.
 */
class StatusProgressBar extends AnchorPane {

	@FXML private ProgressBar progressBar;
	@FXML private Text statusText;
	@FXML private Button cancelButton;

	private final Stage stage;
	private Executor executor;
	private Runnable onCancelAction;


	/**
	 * Creates an instance of progress bar.
	 */
	private StatusProgressBar(Window owner) {
		stage = new Stage();
		stage.initOwner(owner);
		stage.initStyle(StageStyle.UTILITY);
		stage.setOnCloseRequest(event -> {
			onCancel();
			event.consume();
		});
		stage.setScene(new Scene(this));
	}


	/**
	 * Creates an instance of progress bar.
	 * 
	 * @param owner the owner of progress bar window
	 */
	static StatusProgressBar create(Window owner) {
		assert owner != null;
		return Utils.loadFXMLComponent(
				StatusProgressBar.class.getResource("StatusProgressBar.fxml"),
				() -> new StatusProgressBar(owner));
	}


	/**
	 * Sets an executor to use when progress bar gets hidden
	 * with a non-zero delay.
	 * If executor is not set, delay is performed using separate thread.
	 * 
	 * @param executor an executor
	 */
	void setExecutor(Executor executor) {
		this.executor = executor;
	}


	/**
	 * Sets an action to execute when Cancel button is pressed or when
	 * progress bar window is closed.
	 * 
	 * @param action an action
	 */
	void setOnCancel(Runnable action) {
		onCancelAction = action;
	}


	/**
	 * Binds a status text property of this progress bar to the
	 * given observable value.
	 * 
	 * @param observable observable value
	 */
	void initStatusText(ObservableValue<String> observable) {
		assert observable != null;
		statusText.textProperty().bind(observable);
	}


	/**
	 * Unbinds and resets status text property of this progress bar,
	 * setting its value to an empty string.
	 */
	private void resetStatusText() {
		statusText.textProperty().unbind();
		statusText.textProperty().set("");
	}


	/**
	 * Binds a progress property of this progress bar to the
	 * given observable value
	 * 
	 * @param observable observable value
	 */
	void initProgress(ObservableValue<Number> observable) {
		assert observable != null;
		progressBar.progressProperty().bind(observable);
	}


	/**
	 *  Unbinds and resets progress property of this progress bar,
	 *  setting its value to 0.
	 */
	private void resetProgress() {
		progressBar.progressProperty().unbind();
		progressBar.progressProperty().set(0);
	}


	/**
	 * Displays progress bar window.
	 */
	void show() {
		stage.centerOnScreen();
		stage.show();
	}


	/**
	 * Hides progress bar window, then unbinds and resets its progress
	 * and status text properties.
	 * 
	 * @param delayMls a number of milliseconds to delay hiding of progress
	 *        bar window; if delayMls <= 0, then window hides without delay 
	 */
	void hideAndReset(long delayMls) {
		if (delayMls > 0) {
			Runnable delay = () -> {
				try {
					Thread.sleep(delayMls);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				Platform.runLater(this::hideAndReset);
			};
			if (executor != null)
				executor.execute(delay);
			else
				new Thread(delay).start();
		}
		else
			hideAndReset();
	}


	/**
	 * Hides progress bar window, then unbinds and resets its progress
	 * and status text properties.
	 */
	private void hideAndReset() {
		stage.hide();
		resetStatusText();
		resetProgress();
	}


	/**
	 * A handler for progress bar cancellation request. If cancel action is set,
	 * runs that action.
	 */
	@FXML
	private void onCancel() {
		if (onCancelAction != null)
			onCancelAction.run();
	}


	/**
	 * Initialization method invoked by FXML loader.
	 */
	@FXML
	private void initialize() {
		assert cancelButton != null : "fx:id=\"cancelButton\" was not injected: check your FXML file 'StatusProgressBar.fxml'.";
		assert statusText != null : "fx:id=\"statusText\" was not injected: check your FXML file 'StatusProgressBar.fxml'.";
		assert progressBar != null : "fx:id=\"progressBar\" was not injected: check your FXML file 'StatusProgressBar.fxml'.";

		cancelButton.setOnKeyPressed(event -> {
			if (event.getCode() == KeyCode.ENTER)
				cancelButton.fire();
		});
	}

}
