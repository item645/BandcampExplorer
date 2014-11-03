package com.bandcamp.explorer.ui;

import java.net.URL;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaPlayer.Status;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import com.bandcamp.explorer.data.Release;
import com.bandcamp.explorer.data.Track;
import com.bandcamp.explorer.ui.CellFactory.CellCustomizer;

/**
 * Controller class for release player form.
 */
public class ReleasePlayerForm extends SplitPane {

	private Stage stage;
	@FXML private Button loadReleaseButton;
	@FXML private Button previousButton;
	@FXML private Button nextButton;
	@FXML private Button playButton;
	@FXML private Button stopButton;
	@FXML private ImageView artworkView;
	@FXML private Hyperlink releaseLink;
	@FXML private TextArea releaseInfo;
	@FXML private Label nowPlayingInfo;
	@FXML private Slider timeSlider;
	@FXML private Slider volumeSlider;
	@FXML private Label volumeLevel;
	@FXML private TableView<Track> tracksTableView;
	@FXML private TableColumn<Track, Track> playButtonColumn;
	@FXML private TableColumn<Track, Number> trackNumberColumn;
	@FXML private TableColumn<Track, String> artistColumn;
	@FXML private TableColumn<Track, String> titleColumn;
	@FXML private TableColumn<Track, Track.Time> timeColumn;

	// Icons for player control buttons
	private static final ImageView PLAY_ICON     = loadIcon("player_play.png");
	private static final ImageView PAUSE_ICON    = loadIcon("player_pause.png");
	private static final ImageView STOP_ICON     = loadIcon("player_stop.png");
	private static final ImageView PREVIOUS_ICON = loadIcon("player_previous.png");
	private static final ImageView NEXT_ICON     = loadIcon("player_next.png");

	// For play buttons in a track list view we use text instead
	private static final String PLAY_TEXT = "\u25BA";
	private static final String PAUSE_TEXT = "||";


	private final AudioPlayer audioPlayer = new AudioPlayer();
	private final TrackList trackList = new TrackList();
	private TrackListView trackListView;
	private Release release;


	/**
	 * Thin wrapper around tracks table view also encapsulating and providing access
	 * to some associated stuff, like observable items list and list of play buttons 
	 * from first column.
	 */
	private static class TrackListView {

		private final TableView<Track> tableView;
		private final ObservableList<Track> observableTracks = FXCollections.observableArrayList();
		private final SortedList<Track> sortedTracks = new SortedList<>(observableTracks);
		private final List<Button> playButtons = new ArrayList<>();


		/**
		 * Creates a wrapper around specified table view.
		 * 
		 * @param tableView tracks table view
		 */
		TrackListView(TableView<Track> tableView) {
			this.tableView = tableView;
			this.tableView.setPlaceholder(new Label());
			this.tableView.setItems(sortedTracks);
			sortedTracks.comparatorProperty().bind(tableView.comparatorProperty());
		}


		/**
		 * Removes all items from this track list view.
		 */
		void clear() {
			observableTracks.clear();
		}


		/**
		 * Adds a collection of tracks as items to this track list view.
		 * 
		 * @param tracks a collection of tracks
		 */
		void addAll(Collection<Track> tracks) {
			observableTracks.addAll(tracks);
		}


		/**
		 * Returns a list of play buttons from table's first column.
		 */
		List<Button> getPlayPuttons() {
			return playButtons;
		}

	}


	/**
	 * A subclass of ArrayList representing a list of tracks and providing
	 * additional methods for playable tracks lookup.
	 */
	private static class TrackList extends ArrayList<Track> {

		private static final long serialVersionUID = 2980118610902127703L;


		/**
		 * Finds a first playable track in this list.
		 * 
		 * @return first playable track; null, if no such track found
		 */
		Track getFirstPlayableTrack() {
			for (Track t : this)
				if (t.isPlayable())
					return t;
			return null;
		}


		/**
		 * Returns the closest playable track previous to
		 * the given track.
		 * 
		 * @param track a given track to search from
		 * @return closest playable track; null if no such track found
		 * @throws NullPointerException if track is null
		 */
		Track getPreviousPlayableTrack(Track track) {
			ListIterator<Track> itr = listIterator(track.getNumber() - 1);
			while (itr.hasPrevious()) {
				Track t = itr.previous();
				if (t != track && t.isPlayable())
					return t;
			}
			return null;
		}


		/**
		 * Returns the closest playable track next to
		 * the given track.
		 * 
		 * @param track a given track to search from
		 * @return closest playable track; null if no such track found
		 * @throws NullPointerException if track is null
		 */
		Track getNextPlayableTrack(Track track) {
			ListIterator<Track> itr = listIterator(track.getNumber() - 1);
			while (itr.hasNext()) {
				Track t = itr.next();
				if (t != track && t.isPlayable())
					return t;
			}
			return null;
		}

	}


	/**
	 * AudioPlayer is used to actually play audio tracks on release.
	 * This class employs JavaFX MediaPlayer to play audio and works with associated
	 * controls in enclosing class to provide abilities for full-fledged playback,
	 * such as playing, stopping, pausing, skipping to next or previous track,
	 * adjusting track's position and changing sound volume level.
	 */
	private class AudioPlayer {

		private final String LOADING_TRACK_MSG = "Loading track...";

		private MediaPlayer player;
		private Track track;
		private Duration duration;
		private ChangeListener<? super Number> timeSliderValueListener;
		private ChangeListener<? super Number> volumeSliderValueListener;
		private long currentSecond = -1;
		private boolean preventTimeSliderSeek;


		/**
		 * Sets up a track to play in this audio player.
		 * This method triggers a load of track's audio data and prepares
		 * associated controls and underlying media player for playback.
		 * 
		 * @param track a track to play
		 * @throws NullPointerException is track is null
		 * @throws IllegalArgumentException if track is not playable
		 */
		void setTrack(Track track) {
			if (this.track == Objects.requireNonNull(track))
				return;
			if (!track.isPlayable())
				throw new IllegalArgumentException("Track is not playable: " + track);

			if (player != null)
				quit();
			player = new MediaPlayer(new Media(track.getFileLink()));
			this.track = track;

			player.setVolume(volumeSlider.getValue() / volumeSlider.getMax());
			player.currentTimeProperty().addListener(observable -> updateTrackProgress());
			nowPlayingInfo.setText(LOADING_TRACK_MSG);

			setStateTransitionHandlers();

			timeSlider.setDisable(false);
			timeSlider.valueProperty().addListener(
				timeSliderValueListener = (observable, oldValue, newValue) -> {
					if (!preventTimeSliderSeek)
						player.seek(duration.multiply(newValue.doubleValue() / timeSlider.getMax()));
			});
			volumeSlider.setDisable(false);
			volumeSlider.valueProperty().addListener(
				volumeSliderValueListener = (observable, oldValue, newValue) -> {
					player.setVolume(newValue.doubleValue() / volumeSlider.getMax());
			});

			disablePlayerButtons(false);
			playButton.setOnAction(event -> play());
			stopButton.setOnAction(event -> stop());
			prepareSwitchButton(previousButton, trackList.getPreviousPlayableTrack(track));
			prepareSwitchButton(nextButton, trackList.getNextPlayableTrack(track));

			currentSecond = -1;
		}


		/**
		 * Assigns handlers for media player state transition events.
		 */
		private void setStateTransitionHandlers() {
			player.setOnReady(() -> {
				duration = player.getMedia().getDuration();
				updateTrackProgress();
			});
			player.setOnPlaying(() -> {
				playButton.setGraphic(PAUSE_ICON);
				playButton.setOnAction(event -> pause());
				updateTrackListButtons();
			});
			player.setOnPaused(() -> {
				playButton.setGraphic(PLAY_ICON);
				playButton.setOnAction(event -> play());
				updateTrackListButtons();
			});
			player.setOnStopped(() -> {
				playButton.setGraphic(PLAY_ICON);
				playButton.setOnAction(event -> play());
				updateTrackProgress();
				updateTrackListButtons();
			});
			player.setOnStalled(() -> {
				nowPlayingInfo.setText(LOADING_TRACK_MSG);
			});
			player.setOnEndOfMedia(() -> {
				Track next = trackList.getNextPlayableTrack(track);
				if (next != null) {
					updateTrackProgress();
					setTrack(next);
					play();
				}
				else {
					stop();
					updateTrackProgress();
				}
			});
			player.setOnError(() -> {
				MediaException e = player.getError();
				if (e != null)
					e.printStackTrace();
				quit();
			});
		}


		/**
		 * Checks if player is currently playing audio.
		 */
		boolean isPlaying() {
			return player != null && player.getStatus() == Status.PLAYING;
		}


		/**
		 * Checks if player is currently playing specified track.
		 * 
		 * @param track a track to check for playback
		 */
		boolean isPlayingTrack(Track track) {
			return isPlaying() && this.track == track;
		}


		/**
		 * Starts playback for current track.
		 */
		void play() {
			if (player != null) {
				switch (player.getStatus()) {
				case UNKNOWN: case PAUSED: case READY: case STOPPED:
					player.play();
				default:
				}
			}
		}


		/**
		 * Pauses currently playing track.
		 */
		void pause() {
			if (isPlaying())
				player.pause();
		}


		/**
		 * Stop playback for current track.
		 */
		void stop() {
			if (player != null) {
				switch (player.getStatus()) {
				case PLAYING: case PAUSED:
					player.stop();
				default:
				}
			}
		}


		/**
		 * Quits this player, unloading media for current track, disabling controls
		 * and resetting its state values to default.
		 */
		void quit() {
			if (player != null)
				player.dispose();
			if (timeSliderValueListener != null)
				timeSlider.valueProperty().removeListener(timeSliderValueListener);
			if (volumeSliderValueListener != null)
				volumeSlider.valueProperty().removeListener(volumeSliderValueListener);
			timeSlider.setDisable(true);
			volumeSlider.setDisable(true);
			track = null;
			duration = null;
			currentSecond = -1;
			playButton.setGraphic(PLAY_ICON);
			nowPlayingInfo.setText("");
			disablePlayerButtons(true);
		}


		/**
		 * Disables/enable player's control buttons
		 */
		private void disablePlayerButtons(boolean disable) {
			playButton.setDisable(disable);
			stopButton.setDisable(disable);
			previousButton.setDisable(disable);
			nextButton.setDisable(disable);
		}


		/**
		 * Sets up previous/next button for playing specified track.
		 * 
		 * @param button previous or next button
		 * @param track a track to play when button is pressed
		 */
		private void prepareSwitchButton(Button button, Track track) {
			if (track == null)
				button.setDisable(true);
			else {
				button.setDisable(false);
				button.setOnAction(event -> {
					boolean wasPlaying = isPlaying();
					setTrack(track);
					if (wasPlaying)
						play();
				});
			}
		}


		/**
		 * Updates play buttons in a tracklist view so that they conform to
		 * audio player current state.
		 */
		private void updateTrackListButtons() {
			int thisTrackIndex = track.getNumber() - 1;
			List<Button> buttons = trackListView.getPlayPuttons();
			for (int i = 0; i < buttons.size(); i++) {
				Button button = buttons.get(i);
				if (button != null) {
					if (i == thisTrackIndex) {
						// button corresponding to current track
						if (isPlaying()) {
							button.setText(PAUSE_TEXT);
							button.setOnAction(event -> pause());
						}
						else {
							button.setText(PLAY_TEXT);
							button.setOnAction(event -> play());
						}
					}
					else {
						button.setText(PLAY_TEXT);
						int otherTrackIndex = i;
						button.setOnAction(event -> {
							// no need for isPlayable() check here because buttons are 
							// created only for playable tracks 
							setTrack(trackList.get(otherTrackIndex));
							play();
						});
					}
				}
			}
		}


		/**
		 * Updates information about current track's progress.
		 * This includes updating of time slider to reflect current track time as
		 * well as updating nowPlayingInfo label with artist/title and 
		 * elapsed/total track time.
		 */
		private void updateTrackProgress() {
			Duration currentTime = player.getCurrentTime();
			long second = currentTime.equals(player.getStopTime()) 
					? Math.round(currentTime.toSeconds()) : (long)currentTime.toSeconds();
			if (second == currentSecond)
				return; // prevents updating more than once in a second
			else
				currentSecond = second;

			nowPlayingInfo.setText(getNowPlayingInfo(currentTime));

			timeSlider.setDisable(duration.isUnknown());
			if (!timeSlider.isDisabled() && duration.greaterThan(Duration.ZERO) && !timeSlider.isValueChanging()) {
				preventTimeSliderSeek = true; // prevents unnecessary firing of time slider seek listener
				timeSlider.setValue(currentTime.divide(duration.toMillis()).toMillis() * timeSlider.getMax());
				preventTimeSliderSeek = false;
			}
		}


		/**
		 * Builds a string to display in nowPlayingInfo label using current track time.
		 * 
		 * @param currentTime a current track time elsapsed
		 * @return info text
		 */
		private String getNowPlayingInfo(Duration currentTime) {
			return new StringBuilder(track.getArtist()).append(" - ").append(track.getTitle())
					.append(" (").append(formatTime(currentTime)).append('/')
					.append(formatTime(duration)).append(")").toString();
		}


		/**
		 * Converts given duration to a string representation using HH:mm:ss or
		 * mm:ss format, depending on its length.
		 * 
		 * @param duration a duration to convert
		 * @return duration as text
		 */
		private String formatTime(Duration duration) {
			long seconds = Math.round(duration.toSeconds());
			return LocalTime.ofSecondOfDay(seconds)
					.format(seconds >= 3600 ? Track.Time.HH_mm_ss : Track.Time.mm_ss);
		}

	}

	
	private ReleasePlayerForm() {}

	
	/**
	 * Loads an icon for player control button.
	 * 
	 * @param name icon name
	 * @return icon image view; null, if icon with such name is not found
	 */
	private static ImageView loadIcon(String name) {
		URL url = ReleasePlayerForm.class.getResource(name);
		return url != null ? new ImageView(url.toString()) : null;
	}

	
	/**
	 * Loads a release player form component.
	 */
	public static ReleasePlayerForm load() {
		return Utils.loadFXMLComponent(
				ReleasePlayerForm.class.getResource("ReleasePlayerForm.fxml"),
				ReleasePlayerForm::new);
	}
	

	/**
	 * Sets the owner window for player's top stage.
	 * This method is used to set the primary stage as an owner to ensure
	 * that when primary window is closing, player window will be automatically
	 * closed as well.
	 * 
	 * @param owner an owner window
	 * @throws NullPointerException if player's top stage is not yet initialized
	 * @throws IllegalStateException if top stage has already been made visible
	 */
	public void setOwner(Window owner) {
		stage.initOwner(owner);
	}


	/**
	 * Sets a release to play in this player.
	 * 
	 * If there was no release in player, sets the release, loads
	 * all necessary stuff, prepares the player to play first playable
	 * track on this release (if any) and brings player's window to front.
	 * 
	 * If there's another release loaded in a player, interrupts the playback,
	 * unloads release data and sets a new one as described above.
	 * 
	 * If attempted to set the same release object that is already set
	 * for this player, does nothing except bringing player's window to front.
	 * 
	 * If null release is set, unloads the current release (if any), interrupts
	 * the playback.
	 * 
	 * @param release a release
	 */
	void setRelease(Release release) {
		if (this.release == release) {
			if (release != null) {
				// For same release just bring window to front
				stage.setIconified(false);
				stage.show();
			}
			return;
		}
		else
			this.release = release;

		if (release != null) {
			audioPlayer.quit();

			// Loading artwork
			String artworkLink = release.getArtworkThumbLink();
			artworkView.setImage(artworkLink != null ? new Image(artworkLink, true) : null);

			// Setting release page link
			releaseLink.setText(release.getURI().toString());

			// Setting release info
			String tagsStr = release.getTagsString();
			if (!tagsStr.isEmpty()) {
				releaseInfo.setText(new StringBuilder("Tags: ").append(tagsStr).append('.')
						.append('\n').append('\n').append(release.getInformation()).toString());
			}
			else
				releaseInfo.setText(release.getInformation());

			// Setting the tracklist
			trackList.clear();
			trackList.addAll(release.getTracks());
			trackListView.clear();
			trackListView.addAll(trackList);
			// Grow list of play buttons to match number of tracks on release so references
			// to new buttons can later be added via trackListView.getPlayPuttons().set
			// on cells construction
			List<Button> playButtons = trackListView.getPlayPuttons();
			playButtons.clear();
			trackList.forEach(track -> playButtons.add(null));

			// Prepare the first playable track to play
			Track first = trackList.getFirstPlayableTrack();
			if (first != null)
				audioPlayer.setTrack(first);

			// Show the window
			stage.setTitle(release.getArtist() + " - " + release.getTitle());
			stage.setIconified(false);
			stage.show();
			playButton.requestFocus();
		}
		else {
			audioPlayer.quit();
			artworkView.setImage(null);
			releaseLink.setText(null);
			releaseInfo.clear();
			trackList.clear();
			trackListView.getPlayPuttons().clear();
			trackListView.clear();
			stage.setTitle(null);
			stage.hide();
		}
	}


	/**
	 * Loads a top level stage for this player.
	 */
	private void initStage() {
		stage = new Stage();
		stage.setResizable(false);
		stage.setOnCloseRequest(event -> {
			setRelease(null);
			stage.hide();
			event.consume();
		});
		stage.setScene(new Scene(this));
	}


	/**
	 * Plays a track selected in track list view.
	 */
	private void playSelectedTrack() {
		Track track = tracksTableView.getSelectionModel().getSelectedItem();
		if (track != null && track.isPlayable()) {
			audioPlayer.setTrack(track);
			audioPlayer.play();
		}
	}


	/**
	 * Handler for key press on table view.
	 * If Enter was pressed, plays selected track.
	 */
	@FXML
	private void onTracksTableKeyPress(KeyEvent event) {
		if (event.getCode() == KeyCode.ENTER)
			playSelectedTrack();
	}


	/**
	 * Handler for mouse click on a table view.
	 * If double-clicked , plays selected track.
	 */
	@FXML
	private void onTracksTableMouseClick(MouseEvent event) {
		if (event.getClickCount() > 1)
			playSelectedTrack();
	}


	/**
	 * Prompts user to enter a URL to a release, loads the release using supplied
	 * URL and sets it to play in this player.
	 * If there's an error loading release, displays message box with information
	 * about error.
	 * If empty URL was supplied or cancel pressed, does nothing.
	 */
	@FXML
	private void loadRelease() {
		Optional<String> url = Dialogs.inputBox("Enter a release URL to load:", "Load Release", stage);
		if (url.isPresent()) {
			String u = url.get().trim();
			if (!u.isEmpty()) {
				try {
					setRelease(new Release(u));
				} 
				catch (Exception e) {
					Dialogs.messageBox("Error loading release: " + e, "Error", stage);
				}
			}
		}
	}


	/**
	 * Opens release web page on Bandcamp using default web browser.
	 */
	@FXML
	private void openReleasePage() {
		if (release != null)
			Utils.browse(release.getURI());
	}


	/**
	 * Initialization method invoked by FXML loader, provides initial setup for components.
	 */
	@FXML
	private void initialize() {
		checkComponents();

		initStage();

		trackListView = new TrackListView(tracksTableView);

		// Setting a custom cell factory to display a play/pause button 
		// for each playable track
		playButtonColumn.setCellFactory(new CellFactory<Track, Track>(track -> {
			if (track.isPlayable()) {
				// Since this factory will be invoked not only during table initial
				// fill but also on any column sort actions, we need to adjust our
				// newly created buttons to conform with player's current state
				Button button = new Button(audioPlayer.isPlayingTrack(track) ? PAUSE_TEXT : PLAY_TEXT);
				button.setFont(new Font(10));
				button.setOnAction(event -> {
					if (audioPlayer.isPlayingTrack(track)) {
						button.setText(PAUSE_TEXT);
						audioPlayer.pause();
					}
					else {
						button.setText(PLAY_TEXT);
						audioPlayer.setTrack(track);
						audioPlayer.play();
					}
				});
				// Saving button references so they could be later accessed from audio player's
				// state transition handlers
				trackListView.getPlayPuttons().set(track.getNumber() - 1, button);
				return button;
			}
			else
				return null; // don't create buttons for unplayable tracks
		}));
		playButtonColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
		playButtonColumn.setSortable(false);

		trackNumberColumn.setCellValueFactory(cellData -> cellData.getValue().numberProperty());

		CellFactory<Track, String> tooltip = new CellFactory<>(CellCustomizer.tooltip());

		artistColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
		artistColumn.setCellFactory(tooltip);
		artistColumn.setCellValueFactory(cellData -> cellData.getValue().artistProperty());

		titleColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
		titleColumn.setCellFactory(tooltip);
		titleColumn.setCellValueFactory(cellData -> cellData.getValue().titleProperty());

		timeColumn.setCellValueFactory(cellData -> cellData.getValue().timeProperty());

		playButton.setGraphic(PLAY_ICON);
		stopButton.setGraphic(STOP_ICON);
		previousButton.setGraphic(PREVIOUS_ICON);
		nextButton.setGraphic(NEXT_ICON);

		// Make the volume label display volume percentage
		volumeLevel.textProperty().bind(
				Bindings.createStringBinding(
						() -> Math.round(volumeSlider.getValue()) + "%", volumeSlider.valueProperty()));
	}


	/**
	 * Helper method to check if all components defined by FXML file have been injected.
	 */
	private void checkComponents() {
		// copy-pasted from SceneBuilder's "sample controller skeleton" window
		assert volumeLevel != null : "fx:id=\"volumeLevel\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert artistColumn != null : "fx:id=\"artistColumn\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert tracksTableView != null : "fx:id=\"tracksTableView\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert previousButton != null : "fx:id=\"previousButton\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert releaseInfo != null : "fx:id=\"releaseInfo\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert artworkView != null : "fx:id=\"artworkView\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert playButton != null : "fx:id=\"playButton\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert loadReleaseButton != null : "fx:id=\"loadReleaseButton\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert nextButton != null : "fx:id=\"nextButton\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert timeColumn != null : "fx:id=\"timeColumn\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert releaseLink != null : "fx:id=\"releaseLink\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert volumeSlider != null : "fx:id=\"volumeSlider\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert playButtonColumn != null : "fx:id=\"playButtonColumn\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert titleColumn != null : "fx:id=\"titleColumn\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert nowPlayingInfo != null : "fx:id=\"nowPlayingInfo\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert stopButton != null : "fx:id=\"stopButton\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert timeSlider != null : "fx:id=\"timeSlider\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert trackNumberColumn != null : "fx:id=\"trackNumberColumn\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
	}

}
