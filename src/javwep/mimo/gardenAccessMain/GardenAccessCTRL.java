/*
*  Written by Mimo on 24-août-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: MMI setup, User events handling, task management... - Controller class
*  ---------------
*   Notes : The global purpose was to use JavaFX to produce another asynchronous downloader for FlightGear TERRASYNC Scenery, kind of  "lite" version
*		of  the well known TERRAMASTER, based on the current HTTP  download principles. This is just the first version (covering only TERRAIN,
*		OBJECTS and AIRPORTS), which could easily be extended to support additional features like Buildings, Roads, Pylons, Models...
*		Considering that the most time-consuming operations are http downloads and human interactions, the code is looking mainly for
*		comfortable use, fair information, and readable, reusable structures, not maximum speed (using the SHA-1 files signature  feature is anyway
*		delivering quite satifying results - for the rest, download time remains essentially constrained by the internet access pipe...).
*
*	While certainly not a model of Java OO development (using less than 25 specific classes clearly points in the direction of a peristent procedural bias),
*		and possibly for that reason too, I see FlightGearGardener as a (very humble !) celebration of James Goslin's marvelously clever, robust,
*		expressive, efficient, predictable, well-balanced and, all in one, pleasurable creation...
*/


package javwep.mimo.gardenAccessMain;


import appBasicToolKit.AppGroundKit;
import appContextLoader.AppCtxtManager;
import genericPopup.GenPop;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javwep.mimo.gardenDrawer.DrawManager;
import javwep.mimo.gardenLoader.LoadManager;
import javwep.mimo.gardeningObjects.AdHoc;
import javwep.mimo.gardeningObjects.RectBox;
import javwep.mimo.gardeningThread.SyncSas;
import javwep.mimo.gardeningThread.SyncStamper;
import javwep.mimo.gardeningThread.SyncTasks;
import static javwep.mimo.gardeningThread.SyncTasks.*;
import javwep.mimo.gardeningThread.SyncWorker;
import logWriter.CleverLogger;
import popChooser.Chooser;
import static threadManager.ThreadCst.*;
import toolException.ToolException;


public class GardenAccessCTRL implements Initializable
 {
   // Private Resources
   private AppGroundKit groundClerc;
   private AppCtxtManager configClerc;
   private CleverLogger logClerc;
   private DrawManager drawClerc;
   private LoadManager downloadClerc;
   private SyncSas syncRoom;

   // Focusing, downloading...
   private RectBox regionEsriGeometry = null;
   private File terrasyncSearchFolder, terrasynchFolder = null;
   private Thread syncThread;
   private boolean debugON = false;
   private boolean preemptiveActionRunning = false;
   private ArrayList<String> airportsList;
   private ObservableList<String> airportsData;

   // Browsing...
   private final int maxBrowseLevel = 6;
   private final int[] browseLevelsLength = new int[maxBrowseLevel];
   private int currentBrowseLevel = -1;
   private final StringBuilder sourceUrlSTB = new StringBuilder();
   private ArrayList<String> browseList;
   private ObservableList<String> browseDirData, selectFileData, targetFileData;
   private Matcher matchClerc;
   private SyncStamper stampClerc;
   private char SLASH;
   private boolean outdatedOnly = false;

   //   GUI Resources
   private Stage dialogStage;
   private Chooser notifier;
   private DirectoryChooser terrasyncFolderFinder;
   @FXML private  TabPane laScene;
   @FXML private  Tab selectionTAB, downloadTAB, browseTAB;
   @FXML private  Label headLine1, headLine2, headLine3, footLine;
   @FXML private  Canvas drawFocusCAN, drawMapCAN, regionCAN, tileGridCAN, tileSelectCAN;
   @FXML private  Label topYCoord, midYCoord, botYCoord, leftXCoord, midXCoord, rightXCoord;
   @FXML private  Label topYCoord2, midYCoord2, botYCoord2, leftXCoord2, midXCoord2, rightXCoord2;
   @FXML private  Label terraFolderLBL, downloadReportLBL, browseFolderLBL;
   @FXML private  Button applyZoomBTN, findTerrasyncBTN, applySelectionBTN, clearSelectionBTN, resetGridBTN;
   @FXML private  Button selectForSYNCBTN, selectForCLEARBTN, checkObjectsBTN, lookForAirportsBTN, abortBTN;
   @FXML private  Button goBrowseBTN, goBrowseOutdatedBTN, loadItBTN;
   @FXML private  ListView airportsView, browseDirView, selectFileView, targetFileView;

   // Public Resources



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Global INITIALIZATION - INITIALIZE
   //   1. Preparing for Application background bases - THIS HAPPENS possibly BEFORE THE GUI IS READY !!!
   //
   @Override
   public void initialize( URL url, ResourceBundle rb )
    {
	// 0. Instantiate the ground base services
	configClerc = new AppCtxtManager();
	logClerc = new CleverLogger();
	groundClerc = new AppGroundKit(configClerc, logClerc);

	// 2. Initialize a few items... : TerraSync local repository chooser...
	terrasyncFolderFinder = new DirectoryChooser();
	airportsList = new ArrayList<>();
	browseList = new ArrayList<>();

	// 3. Preparing the Synchronization SAS
	syncRoom = new SyncSas(this);
   }


   // Global INITIALIZATION - GET ALIVE
   //  2. Receives from the Main an handle to the Notifier Popup and get the real business alive
   //
   public void getAlive(Chooser theAcknowledger, Stage theStage) throws Exception
    {
	// 0. Get access to the GUI STAGE and Notification Utility
	notifier = theAcknowledger;
	dialogStage = theStage;

	// 1. Retrieve the Application Context "config file" and load the info
	try {
	   configClerc.load("FLIGHTGEARGardener");

	} catch (ToolException err) {
	   fatalPost("Fatal Error opening the CONTEXT file:  " + err.getMessage()
					+ " \n... Aborting this task !");
	   // No LOG opened yet : don't use CleanQuit !
	   Platform.exit();
	   System.exit(0);
	}
	debugON = ( configClerc.get("DEBUGlogging", "NO").equalsIgnoreCase("YES") );
	groundClerc.debugIsON = debugON;
	syncRoom.debugON = debugON;

	// 2. Open the Log File using the Config info
	try {
	   logClerc.open(configClerc);

	} catch (ToolException err) {
	   if (logClerc != null) { logClerc.close();   }
	   fatalPost("Fatal Error opening the Log file:  " + err.getMessage()
					+ " ... Aborting this task !");
	   // No LOG yet : don't use CleanQuit !
	   Platform.exit();
	   System.exit(0);
	}
	logIt("   >>> FLIGHTGEARGardener Session launched on: " + LocalDateTime.now().toString());
	logIt("");
	if ( debugON ) {
	   logIt("      <--> DEBUG mode - verbose logging enabled !");
	   logIt("");
	}
	logFlush();

	// 3. Preset  the Terrasync basic info
	terrasyncSearchFolder = new File(configClerc.get("Terrasync_Folder", configClerc.getHomeDir()));
	SLASH = ( configClerc.getDirSep().charAt(0) );

	// 4. Validate the various CANVASes Geometry and compatibility, as well as usability...
	if ( canvasGeomNOTSquare(regionCAN) ) {
	   logIt("");
	   logIt("   <!> The REGION Canvas is NOT a usable Square.");
	   fatalPost("The REGION Canvas is NOT a usable Square; Unable to proceed.");
	   cleanQuit();
	}
	if ( canvasesGeomNOTCompatible(drawMapCAN, drawFocusCAN) || canvasesGeomNOTCompatible(regionCAN, tileGridCAN)
										   || canvasesGeomNOTCompatible(regionCAN, tileSelectCAN) ) {
	   logIt("   <!> At least two Canvases with incompatible Geometries.");
	   fatalPost("There is at least two Canvases with incompatible Geometries; Please check! Unable to proceed.");
	   cleanQuit();
	}
	if ( canvasesNOTUsable() ) {
	   logIt("   <!> At least some Canvases with incompatible Geometry.");
	   fatalPost("There is at least some Canvases with incompatible Geometry; Please check! Unable to proceed.");
	   cleanQuit();
	}

	// 5. Other initializations :  "Focusing" background handler, Airports info listview ....
	downloadClerc = new LoadManager( groundClerc, syncRoom );
	drawClerc = new DrawManager( groundClerc, drawMapCAN, drawFocusCAN, regionCAN );
	stampClerc = new SyncStamper(configClerc, logClerc);
	airportsData = FXCollections.observableArrayList();
	airportsView.setItems(airportsData);
	browseDirData = FXCollections.observableArrayList();
	browseDirView.setItems(browseDirData);
	selectFileData = FXCollections.observableArrayList();
	selectFileView.setItems(selectFileData);
	targetFileData = FXCollections.observableArrayList();
	targetFileView.setItems(targetFileData);

	// Set up the "Focusing" Tab  and the "Download" Clerc
	try {
	   topYCoord.setText("90° N");
	   midYCoord.setText("0° N");
	   botYCoord.setText("90° S");
	   leftXCoord.setText("180° W");
	   midXCoord.setText("0° E");
	   rightXCoord.setText("180° E");

	   logIt("   >>> Initialization started!");
	   drawClerc.drawTheEarthMap();
	   downloadClerc.start(tileGridCAN, tileSelectCAN);

	} catch (ToolException err) {
	   fatalPost(err.getMessage() + "\n Unable to proceed...");
	   cleanQuit();
	}

	headLine1.setText("Regional focus: None selected yet. Please click in the Map.");
	logIt("   >>> Initialization completed!");
	logIt("");
   }



   // Preparing for Application CLOSE
   //
   public void cleanQuit()
    {
	logIt("");
	logIt("   >>> Preparing to Exit on: " + Instant.now() );

	// CLOSE hereafter outstanding resources (Files, Tables, Connections...)

	// Closing the Log file itself
	logIt("");
	logIt("<!> Now closing the LOG file : See You later...");
	if (logClerc != null) { logClerc.close(); }

	// Shuting the application down
	Platform.exit();
	System.exit(0);
   }




// wwwwwwwwwwwwwwwwwwwwwwwwwww
//				@MainSection  1 - GENERIC EVENTS HANDLING code
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Receive TABPANE mouse clicks to setup the just selected TAB...
   //
   @FXML
   private void onTabSelection( MouseEvent event )
    {
	//	if ( laScene.getSelectionModel().getSelectedItem() == selectionTAB) {
	//	   postIt("Please pick your centre of interest clicking a point in the Map ...");
	//	   return;
	//	}
   }

   // To avoid percolation till TabSelection again in case the pane is clicked...
   //
   @FXML
   private void onPaneClicked( MouseEvent event )
    {
	event.consume();
   }

   // If NO critical Operation in progress, exit
   //
   @FXML
   void onExitAsked( ActionEvent event )
    {
	if ( preemptiveActionRunning ) {
	   pausedPost("There is currently an ACTION in progress ; please, close it first, or wait for completion.");
	   return;
	}
	cleanQuit();
   }




// wwwwwwwwwwwwwwwwwwwwwwwwwww
//				@MainSection  1 - SPECIFIC EVENTS HANDLING code
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Leaving the DOWNLOAD TAB...
   // <+> <+> <+>
   @FXML
   private void onBackToSelectingTAB (ActionEvent event)
    {
	regionEsriGeometry = null;
	downloadClerc.passivateCurrentRegion();
	try {
	   onResetGridRequest(event);

	} catch (ToolException ex) { }	  // Let's go

	downloadTAB.setDisable(true);
	postIt("Please pick a new centre of interrest, Apply Zoom, or Exit ...");
	laScene.getSelectionModel().select(selectionTAB);
	applyZoomBTN.setDisable(false);
	selectionTAB.setDisable(false);
   }


   // User just clicked some spot in the Map...
   // <+> <+> <+>
   @FXML
   private void onMapFocusGiven (MouseEvent event)
    {
	if ( drawClerc.setFocusRegionAt( event.getX(), event.getY(), headLine1 ) ) {
	   applyZoomBTN.setDisable(false);
	   postIt("Click another point, or Apply zoom, or Exit...");
	}
   }


   // Paint the selected Rectangle image to the REGION BOARD, paint the GRID (on the REGION too), and set the GRID BOARD on top
   // <+> <+> <+>
   @FXML
   private void onApplyZoomRequest (ActionEvent event)
    {
	applyZoomBTN.setDisable(true);
	selectionTAB.setDisable(true);
	try {
	   drawClerc.projectSelectedRegion();
	   tileGridCAN.toFront();
	   regionEsriGeometry = drawClerc.getRegionSqEsriGeom();
	   downloadClerc.activateThisEsriRegion(regionEsriGeometry);

	} catch (ToolException ex) {
	   regionEsriGeometry = null;
	   postIt("Error: " + ex.getMessage());
	   return;
	}

	// Display the Bounds and Mids of the Region, for the current Selection
	topYCoord2.setText("" + regionEsriGeometry.topY() + "°");
	midYCoord2.setText("" + ( regionEsriGeometry.bottomY() + (regionEsriGeometry.height()/2) ) + "°");
	botYCoord2.setText("" + regionEsriGeometry.bottomY() + "°");
	leftXCoord2.setText("" + regionEsriGeometry.leftX() + "°");
	midXCoord2.setText("" + ( regionEsriGeometry.leftX() + (regionEsriGeometry.width()/2) ) + "°");
	rightXCoord2.setText("" + regionEsriGeometry.rightX() + "°");
	postIt("Please select your Terrasync repository, or go back to Focusing TAB...");
	applySelectionBTN.setDisable(true);
	laScene.getSelectionModel().select(downloadTAB);
	downloadTAB.setDisable(false);
   }


   // Launch the Directory finder for Terrasynch main FOLDER
   // <+> <+> <+>
   @FXML
   private void onFindTerrasyncRequest(ActionEvent event)
    {
	postIt("Please browse to a valid Terrasync folder...");
	tileGridCAN.setMouseTransparent(true);
	terrasyncFolderFinder.setInitialDirectory(terrasyncSearchFolder);
	terrasynchFolder = terrasyncFolderFinder.showDialog(dialogStage);
	if ( terrasynchFolder == null ) { return;  }

	terrasyncSearchFolder = terrasynchFolder.getParentFile();
	// Check Folder for Terrasync traces...
	findTerrasyncBTN.setDisable(true);
	terraFolderLBL.setText(terrasynchFolder.getPath());
	postIt("Now scaning this Folder for already downloaded tiles...");
	try {
	   // Ask for Scaning the subfolders in the current Region, searching already downloaded tiles (PACKED (green) or at least POPULATED (yellowgreen))
	   int errCnt = downloadClerc.proceedWithInitialFolderScan( terrasynchFolder );
	   if ( errCnt == -1 ) {
		postIt("Found NO POPULATED tile in this Folder for the selected Region... Try SYNC, Clear, ..., or BACK...");
		headLine2.setText("Region currently NOT populated yet.");
	   } else {
		if ( errCnt == 0 ) {
		   postIt("Found loaded tiles in this Folder for the selected Region... Try SYNC, Clear, ..., or BACK...");
		   headLine2.setText("Region populated. See coloured boxes : bluer is fresher... Try SYNC, CLEAR, or go Back...");
		} else {
		   alertPost("Found " + errCnt + " severe errors while scanning this Folder for the selected Region..."
						+ " (Did abort after 10).\nPlease investigate!");
		   postIt("Found " + errCnt + " severe errors  in this Folder / Region... Please investigate...");
		   headLine2.setText("Currently populated/packed with " + errCnt + " tiles.");
		}
		selectForCLEARBTN.setDisable(false);
	   }
	   selectForSYNCBTN.setDisable(false);
	   selectForCLEARBTN.setDisable(false);
	   checkObjectsBTN.setDisable(false);
	   lookForAirportsBTN.setDisable(false);
	   goBrowseBTN.setDisable(false);
	   goBrowseOutdatedBTN.setDisable(false);
	   resetGridBTN.setDisable(false);

	} catch (ToolException ex) {
	   postIt("Scan Terrasync Folder Error: " + ex.getMessage());
	   alertPost("Scan Terrasync Folder error: " + ex.getMessage());
	}
   }


   // Reset the GRID Board, disable all buttons but enable Terrasync Folder finder for fresh search
   // <+> <+> <+>
   @FXML
   private void onResetGridRequest (ActionEvent event) throws ToolException
    {
	downloadClerc.clearGridBoard();
	headLine2.setText("");
	tileGridCAN.setMouseTransparent(true);
	findTerrasyncBTN.setDisable(false);
	onClearSelectionRequest(event);
	resetGridBTN.setDisable(true);
	selectForSYNCBTN.setDisable(true);
	selectForCLEARBTN.setDisable(true);
	checkObjectsBTN.setDisable(true);
	lookForAirportsBTN.setDisable(true);
	goBrowseBTN.setDisable(true);
	goBrowseOutdatedBTN.setDisable(true);
	terraFolderLBL.setText("");
	downloadReportLBL.setText("");
	postIt("Please select a Terrasync repository, or BACK to Focusing...");
   }


   // Clear the Pointer Board for all selected tiles and disable unusable buttons
   // <+> <+> <+>
   @FXML
   private void onClearSelectionRequest (ActionEvent event) throws ToolException
    {
	setSelectionContext(SyncTasks.NONE, false);
	downloadClerc.clearPointerBoard();
	headLine3.setText("");
	airportsData.clear();
	airportsView.setVisible(false);
	applySelectionBTN.setText("Apply the Selection");
	postIt("Please select an ACTION, or RESET Grid, or BACK to Focusing...");
   }


   // Prepare the Grid for selecting tile candidates to Synchronize
   // <+> <+> <+>
   @FXML
   private void onSelectTilesToSyncRequest (ActionEvent event)
    {
	setSelectionContext(SyncTasks.SYNCALL, true);
	downloadReportLBL.setText("Selection target: SYNCRONIZE Tiles.");
	applySelectionBTN.setText("SYNC the selected Tiles");
	postIt("Please select TILES to synchronize, or Reset Selection/Grid...");
   }


   // Prepare the Grid for selecting tile candidates to Remove
   // <+> <+> <+>
   @FXML
   private void onSelectTilesToRemoveRequest (ActionEvent event)
    {
	setSelectionContext(SyncTasks.CLEAR, true);
	downloadReportLBL.setText("Selection target: REMOVE Tiles.");
	applySelectionBTN.setText("CLEAR the selected Tiles");
	postIt("Please select TILES to CLEAR, or Reset Selection/Grid...");
   }


   //  Prepare the Grid for selecting tile candidates to Sync OBJECTS only (Terrain must be current, but will not be resynchronized)
   // <+> <+> <+>
   @FXML
   private void onCheckObjectsRequest (ActionEvent event)
    {
	setSelectionContext(SyncTasks.SYNCOBJ, true);
	downloadReportLBL.setText("Selection target: SYNCHRONIZE Objects.");
	applySelectionBTN.setText("SYNC Objects selected Tiles");
	postIt("Please select TILES to CHECK Objects, or Reset Selection/Grid...");
   }


   // Set local Action: On Tile box clicked, will look this Tile Terrain for ABCD.btg.gz files, and show a list of findings...
   // <+> <+> <+>
   @FXML
   private void onLookForAirportsRequest (ActionEvent event)
    {
	setSelectionContext(SyncTasks.SHOWPORT, true);
	downloadClerc.setTargetCellDisabled(true);
	airportsView.setVisible(true);
	downloadReportLBL.setText("ACTION: SHOW AIRPORTS Info.");
	applySelectionBTN.setText("Select a Tile to see its Airports");
	postIt("Please select TILES to see Airports list, or Reset Selection/Grid...");
   }


   // This uses the Mouse event coordinates (in the Grid Geometry) to set the corresponding Grid Tile and Terrasync box name... if not Set
   // <+> <+> <+>
   @FXML
   private void onTileClicked (MouseEvent event)
    {
	switch ( downloadClerc.getRunningAction() ) {
	   case SHOWPORT : {
		try {
		   downloadClerc.loadListOfAirportsByFocusCoords( event.getX(), event.getY(), terrasynchFolder.toPath(), airportsList, headLine3);
		   clearSelectionBTN.setDisable(false);
		   Collections.sort(airportsList);
		   airportsData.setAll(airportsList);
		   if ( airportsList.isEmpty() ) {
			postIt("NO AIRPORT found in the current Terrasync cell folderList; Please RESET Selection or click another cell...");
		   } else {
			postIt("Airports List shown; Click another cell, or RESET Selection...");
		   }

		} catch (ToolException err) {
			logIt("Error retrieving target Tile for MouseClick " + err.getMessage());
		}
		break;
	   }
	   case NONE : case UGLY : {
		return;
	   }
	   default : {
		try {
		   downloadClerc.reflectGridCellPick( event.getX(), event.getY(), headLine3 );
		   clearSelectionBTN.setDisable(false);
		   applySelectionBTN.setDisable(false);
		   postIt("Tile has been added to current Action targets; Click another, or RESET Selection...");

		} catch (ToolException err) {
		   logIt("Error retrieving target Tile for MouseClick " + err.getMessage());
		}
	   }
	}
   }


   // Try Applying the retained ACTION to the Selected tiles, using the Action Thread...
   // <+> <+> <+>
   @FXML
   private void onApplySelectionRequest (ActionEvent event)
    {
	applySelectionBTN.setDisable(true);
	clearSelectionBTN.setDisable(true);

	// Depending on the running Action...
	switch ( downloadClerc.getRunningAction() ) {
	   case SYNCALL : {
		// This Action needs a connection to the remote Terrasync Repo
		try {
		   headLine3.setText("Retained Synchronisation Server: " + downloadClerc.openSyncSession());

		   // Launch the Session
		   if ( debugON ) { downloadClerc.logListOfSelectedTiles(); 	}
		   postIt("GLOBAL Synchronization starting...");
		   logIt("<!> Starting a GLOBAL TERRAINSYNC at " + Instant.now().toString());

		} catch (ToolException ex) {
		   headLine3.setText("");
		   alertPost("Unable to find Terrasync Servers; Check for WEB ACCESS status, then retry... " + ex.getMessage());
		   applySelectionBTN.setDisable(false);
		   clearSelectionBTN.setDisable(false);
		   return;
		}
		break;
	   }
	   case SYNCOBJ : {
		// This Action needs a connection to the remote Terrasync Repo
		try {
		   headLine3.setText("Retained Synchronisation Server: " + downloadClerc.openSyncSession());

		   // Launch the Session
		   if ( debugON ) { downloadClerc.logListOfSelectedTiles(); 	}
		   postIt("OBJECTS Synchronization starting...");
		   logIt("<!> Starting an OBJECTS SYNC at " + Instant.now().toString());

		} catch (ToolException ex) {
		   headLine3.setText("");
		   alertPost("Unable to find Terrasync Servers; Check for WEB ACCESS status, then retry... " + ex.getMessage());
		   applySelectionBTN.setDisable(false);
		   clearSelectionBTN.setDisable(false);
		   return;
		}
		break;
	   }
	   case CLEAR : {
		// Get Confirmation for critical Actions
		if ( ! yesChoosen("Do you confirm DELETING content for the selected Tiles ?") ) {
		   applySelectionBTN.setDisable(false);
		   clearSelectionBTN.setDisable(false);
		   return;
		}
		// Launch the Session
		downloadClerc.logListOfSelectedTiles();
		postIt("CLEAR starting...");
		logIt("<!> Starting a TILES CLEAR at " + Instant.now().toString());

		// This action does not rely on external Terrasync Repository: proceed...
		break;
	   }
	   default : return;
	}

	// Let's proceed : Start the Sync thread... if not yet running.
	if ( ! syncThreadISAlive() ) {
	   alertPost("Synchro Thread is NOT LIVING ! Unable to proceed... Please retry later.");
	   applySelectionBTN.setDisable(false);
	   clearSelectionBTN.setDisable(false);
	   return;
	}

	// ... and Go
	downloadClerc.executeAction( terrasynchFolder, syncThread );
	abortBTN.setDisable(false);
   }


   // User whish browsing the Remote Terrasync content... outside Terrain and Objects
   // <+> <+> <+>
   @FXML
   private void onGoBrowseRequest (ActionEvent event)
    {
	outdatedOnly = false;

	headLine2.setText("Browsing Terrasync Root Page (except Terrain or Objects) for ALL items.");
	if ( prepareBrowseAction(outdatedOnly) ) {
	   return;
	}
	downloadTAB.setDisable(true);
	laScene.getSelectionModel().select(browseTAB);
	browseTAB.setDisable(false);
	postIt("Select a Directory for drill-down or a File to load, or go Back to Downloading...");
   }


   // User whish browsing the Remote Terrasync content... outside Terrain and Objects, but ONLY for OUTDATED items...
   // <+> <+> <+>
   @FXML
   private void onGoBrowseOutdatedRequest (ActionEvent event)
    {
	outdatedOnly = true;

	headLine2.setText("Browsing Terrasync Root Page (except Terrain or Objects) for OUTDATED items.");
	if ( prepareBrowseAction(outdatedOnly) ) {
	   return;
	}
	downloadTAB.setDisable(true);
	laScene.getSelectionModel().select(browseTAB);
	browseTAB.setDisable(false);
	postIt("Select a Directory for drill-down or a File to load, or go Back to Downloading...");
   }


   // The Browse DIRECTORY view has been Clicked...
   // <+> <+> <+>
   @FXML
   private void onBrowseDirViewClicked (MouseEvent event)
    {
	if ( browseDirView.getSelectionModel().isEmpty() || currentBrowseLevel + 1 >= maxBrowseLevel ) { return;	}

	currentBrowseLevel++;
	sourceUrlSTB.append(browseDirView.getSelectionModel().getSelectedItem());
	sourceUrlSTB.append('/');
	browseLevelsLength[currentBrowseLevel] = sourceUrlSTB.length();
	// Invalidate the Target viewer
	targetFileData.clear();

	if ( ! browseRequestGOTResults(outdatedOnly) ) {
	   currentBrowseLevel--;
	   sourceUrlSTB.setLength(browseLevelsLength[currentBrowseLevel]);
	} else {
	   if ( outdatedOnly ) {
		headLine2.setText("Browsing Terrasync for OUTDATED items at " + sourceUrlSTB.toString());
	   } else {
		headLine2.setText("Browsing Terrasync for ALL items at " + sourceUrlSTB.toString());
	   }
	   postIt("Select a Directory for drill-down, or a File for Load, or go Back in Folders, or go Back to Downloading...");
	}
   }


   // Browse one level up
   // <+> <+> <+>
   @FXML
   private void onGetUpRequest (ActionEvent event)
    {
	if ( currentBrowseLevel <= 0) { return; }

	currentBrowseLevel--;
	sourceUrlSTB.setLength(browseLevelsLength[currentBrowseLevel]);
	browseRequestGOTResults(outdatedOnly);
	targetFileData.clear();
	postIt("Select a Directory for drill-down, or a File for Load, or go Back in Folders, or go Back to Downloading...");
   }


   // The Browse FILES view has been Clicked...
   // <+> <+> <+>
   @FXML
   private void onSelectFileViewClicked (MouseEvent event)
    {
	if ( selectFileView.getSelectionModel().isEmpty() ) { return; 	}

	targetFileData.add( (String) selectFileView.getSelectionModel().getSelectedItem());
	loadItBTN.setDisable(false);
	postIt("Select another File, or Clear all, or Load all, or ...; click a single selected file to remove it.");
   }


   // The Browse FILE view has been Clicked... this line must be removed
   // <+> <+> <+>
   @FXML
   private void onTargetViewClicked (MouseEvent event)
    {
	if ( targetFileView.getSelectionModel().isEmpty() ) { return; 	}

	int toDeleteLineIndex =  targetFileView.getSelectionModel().getSelectedIndex();

	targetFileData.remove(toDeleteLineIndex);
   }


   // Clear the whole Target select view
   // <+> <+> <+>
   @FXML
   private void onClearTargetRequest (ActionEvent event)
    {
	targetFileData.clear();
   }


   // Load the selected files
   // <+> <+> <+>
   @FXML
   private void onLoadItRequest (ActionEvent event)
    {
	loadItBTN.setDisable(true);
	try {
	   for (String aLine : targetFileData) {
		URL sourceURL = new URL(sourceUrlSTB.toString() + aLine);
		Path targetPath = Paths.get(terrasynchFolder.toString(),
							   sourceURL.toExternalForm().substring(browseLevelsLength[0]).replace('/', SLASH));

		if ( ! downloadClerc.loadAndSaveDIDSucceed(sourceURL, targetPath ) ) {
		   logIt(" <!> Error while downloading a selected File to:");
		   logIt("       " + targetPath.toString());
		   postIt("Error while downloading the selected File...");
		   continue;
		}
	   }
	   postIt("The selected files have been saved to the current Terrasync folder...");

	} catch (MalformedURLException ex) {
	   logIt(" <!> Malformed URL: " + ex.getMessage());
	   alertPost("Got a malformed URL while looking for File to download... "  + ex.getMessage() );
	}

	targetFileData.clear();
   }


   // Abort the current Task after the current Tile...
   // <+> <+> <+>
   @FXML
   private void onAbortRequest (ActionEvent event)
    {
	abortBTN.setDisable(true);
	if ( syncRoom.threadISBuzy() ) {
	   syncRoom.trdMission = ABORT_TASK;
	   postIt("Abort asked. Waiting for completion...");
      }
   }


   // Leaving the BROWSING Tab...
   // <+> <+> <+>
   @FXML
   private void onBackToDownloadingTAB (ActionEvent event)
    {
	currentBrowseLevel = -1;
	browseTAB.setDisable(true);
	browseDirView.setMouseTransparent(true);
	laScene.getSelectionModel().select(downloadTAB);
	downloadTAB.setDisable(false);
   }




// wwwwwwwwwwwwwwwwwwwwwwwwwww
//			MAINPROGRAMSECTION  2 - SPECIFIC UTILITIES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Check a Canvas for usable SQUARE geometry
   private boolean canvasGeomNOTSquare ( Canvas theOne )
    {
	return ( theOne.getWidth() != theOne.getHeight() || theOne.getHeight() < laScene.getHeight() / 2 );
   }

   // Check two canvases for exact superposition
   private boolean canvasesGeomNOTCompatible( Canvas theOne, Canvas theOther )
    {
	return ( theOne.getLayoutX() != theOther.getLayoutX() || theOne.getLayoutY() != theOther.getLayoutY()
		|| theOne.getWidth() != theOther.getWidth() || theOne.getHeight()!= theOther.getHeight() );
   }

   // Check for usability...
   private boolean canvasesNOTUsable()
    {
	double minDim = AdHoc.minUsableDimension;
	return ( drawMapCAN.getHeight() < minDim ) || ( drawMapCAN.getWidth() < minDim ) || ( regionCAN.getWidth() < minDim );
   }


   // Prepare the MMI for Selection...
   private void setSelectionContext( SyncTasks theTarget, boolean theOnFlag )
    {
	downloadClerc.setRunningAction(theTarget);
	downloadReportLBL.setText("");
	selectForSYNCBTN.setDisable(theOnFlag);
	selectForCLEARBTN.setDisable(theOnFlag);
	checkObjectsBTN.setDisable(theOnFlag);
	lookForAirportsBTN.setDisable(theOnFlag);
	goBrowseBTN.setDisable(theOnFlag);
	goBrowseOutdatedBTN.setDisable(theOnFlag);
	tileSelectCAN.setMouseTransparent(! theOnFlag);
	if ( theOnFlag ) {
	   tileSelectCAN.toFront();
	   airportsView.setVisible(false);
	}
   }

   // Prepare context for BROWSE Action, depending on Filter (Show ALL, or just OUTDATED.)
   private boolean prepareBrowseAction( boolean theFilter )
    {
	try {
	   headLine3.setText("Retained Synchronisation Server: " + downloadClerc.openSyncSession());
	   logIt("<!> Starting a BROWSE Action at " + Instant.now().toString());
	   sourceUrlSTB.setLength(0);
	   sourceUrlSTB.append(syncRoom.synchroSource.toExternalForm());
	   sourceUrlSTB.append('/');
	   currentBrowseLevel = 0;
	   browseLevelsLength[currentBrowseLevel] = sourceUrlSTB.length();
	   // Invalidate all 3 viewers
	   browseDirData.clear();
	   selectFileData.clear();
	   targetFileData.clear();
	   if ( ! browseRequestGOTResults(theFilter) ) {
		postIt("There seems to be NO additional data here (outside Terrain and Objects...).");
		return true;
	   }
	   browseDirView.setMouseTransparent(false);

	} catch (ToolException ex) {
	   headLine3.setText("");
	   alertPost("Unable to find Terrasync Servers; Check for WEB ACCESS status, then retry... " + ex.getMessage());
	   return true;
	}
	return false;
   }

   // Manage Browsing operations
   private boolean browseRequestGOTResults( boolean filterIsON )
    {
	downloadClerc.getDirindexItemsFrom(sourceUrlSTB.toString() + AdHoc.TERR_DIRINDEX, browseList);

	if ( browseList.size() <= 0 ) {
	   postIt("Nothing to browse here ...");
	   pausedPost("Found nothing to browse here ... Choose another, get Up, or go Back.");
	   return false;
	}

	browseDirData.clear();
	selectFileData.clear();
	Path targetPath = null;

	if ( filterIsON ) {
	   targetPath = Paths.get(terrasynchFolder.toString(),
						   sourceUrlSTB.toString().substring(browseLevelsLength[0]).replace('/', SLASH));
	}

	for (String aLine : browseList) {
	   matchClerc = AdHoc.TILE_DIR_PATTERN.matcher(aLine);
	   if ( matchClerc.matches() ) {
		if ( matchClerc.group(2).equals("Objects") ||  matchClerc.group(2).equals("Terrain") ) { continue;  }
		browseDirData.add( matchClerc.group(2));
	   } else {
		matchClerc = AdHoc.TILE_FILE_PATTERN.matcher(aLine);
		if ( matchClerc.matches() ) {
		   // Check for file currency if needed...
		   if ( targetPath != null ) {
			if ( stampClerc.fileIsCurrent( targetPath.resolve(matchClerc.group(2) ),
										   matchClerc.group(6), matchClerc.group(4)) ) {
			   continue;
			}
		   }
		   selectFileData.add( matchClerc.group(2));
		}
	   }
	}

	Collections.sort(browseDirData);
	if ( selectFileData.size() <= 0  ) {
	   selectFileView.setMouseTransparent(true);
	   browseFolderLBL.setText("");
	   if ( browseDirData.size() <= 0 ) {
		postIt("Nothing usable to browse here ...");
		pausedPost("Found nothing to browse here ... Get Up, or Back.");
		return false;
	   }
	} else {
	   Collections.sort(selectFileData);
	   selectFileView.setMouseTransparent(false);
	   browseFolderLBL.setText("...hereunder the filtered content of remote folder /"
									   + sourceUrlSTB.toString().substring(browseLevelsLength[0]));
	}
	return true;
   }




// wwwwwwwwwwwwwwwwwwwwwwwwwww
//				@MainSection  3 - GENERIC UTILITIES code
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Logger utility
   private void logIt(String theMessage)
    {
	logClerc.add(theMessage);
   }

   private void logFlush()
    {
	logClerc.writeDisk();
   }


   //  A utility to display just one message without pause
   private void postIt(String theMessage)
    {
	footLine.setText(theMessage);
   }

   //  Utilities to display Info, Alert or fatal messages, with a pause
   private void pausedPost(String theMessage)
    {
	if ( groundClerc.lockGrantedFor("pausedPost") ) {
	   notifier.resetHintTo("At " + LocalTime.now().format(AdHoc.SHORT_TIME) + ", please acknowledge...");
	   notifier.postInfo(theMessage);
	   groundClerc.dropLockFor("pausedPost");
	} else {
	   postIt("Unable to LOCK the Notifier ! Please consider EXITing ?");
	}
   }

   private void alertPost(String theMessage)
    {
	if ( groundClerc.lockGrantedFor("alertPost") ) {
	   notifier.resetHintTo("At " + LocalTime.now().format(AdHoc.SHORT_TIME) + " : Alert message...");
	   notifier.postAlert(theMessage);
	   groundClerc.dropLockFor("alertPost");
	} else {
	   postIt("Unable to LOCK the Notifier ! Please consider EXITing ?");
	}
   }

   private void fatalPost(String theMessage)
    {
	if ( groundClerc.lockGrantedFor("fatalPost") ) {
	   notifier.resetHintTo("At " + LocalTime.now().format(AdHoc.SHORT_TIME) + " : Emergency message...");
	   notifier.postLast(theMessage);
	   groundClerc.dropLockFor("fatalPost");
	} else {
	   postIt("Unable to LOCK the Notifier ! Please consider EXITing ?");
	}
	syncRoom.notificationCompleted = true;
   }

   private boolean yesChoosen(String theMessage)
    {
	boolean answer = false;
	if ( groundClerc.lockGrantedFor("yesChoosen") ) {
	   notifier.resetHintTo("At " + LocalTime.now().format(AdHoc.SHORT_TIME) + ", please select your prefered action...");
	   answer = notifier.postChoice(theMessage, "NO", "YES") == GenPop.yesAnswer;
	   groundClerc.dropLockFor("yesChoosen");
	}
	return answer;
   }


   // Timer utility
   //
   public void getAPauseOf(int milliSec)
    {
	try {
	   Thread.sleep(milliSec);
	} catch (InterruptedException e) {
	}
   }



// ===============================================
//				   @MainSection  6 - Utilities to prepare for THREAD RUN AND REPORT
// ===============================================

   // Starter for the Synchronize THREAD
   //
   private boolean syncThreadISAlive()
    {
	if ( syncRoom.threadISStarted() ) {
	   return true;

	} else {
	   syncThread = new Thread (new SyncWorker(syncRoom, groundClerc));

	   if ( unableToStart(syncThread) ) {
		pausedPost("Unable to START SYNCHRONIZATION SESSION within 10 seconds.\n"
					+ "Please try again later...");
		logIt("Unable to Start this SYNCHRONIZATION Thread after 10 seconds.");
		return false;
	   }
	   // Wait a bit for Thread installation...
	   for (int i = 0; i < 5; i++) {
		getAPauseOf(1000);
		if (syncRoom.threadISIdle()) {
		   break;
		}
	   }
	}
	return syncRoom.threadISIdle();
   }


   // Utility to start comfortably a new thread
   //
   private boolean unableToStart(Thread theThread)
    {
	theThread.start();
	for (int i = 0; i < 10; i++) {
	   if (theThread.isAlive()) {
		return false;
	   }
	   getAPauseOf(1000);
	}
	return true;
   }


   // REPORTING methods for the Sync Thread
   public void reportDownloadProgress()
    {
	switch (syncRoom.trdTaskResult) {

	   case BUZY_STATE : {
		downloadReportLBL.setText(syncRoom.trdTaskNotice);
		break;
	   }
	   case START_TASK : {
		preemptiveActionRunning = true;
		downloadReportLBL.setText("Download Task started...");
		postIt("The Download Task is running...");
		break;
	   }
	   case MISSION_COMPLETED : {
		abortBTN.setDisable(true);
		downloadClerc.clearGridBoard();
		downloadClerc.clearPointerBoard();
		preemptiveActionRunning = false;
		pausedPost(syncRoom.trdTaskNotice);
		postIt("Please chose another Action, or go back.");
		clearSelectionBTN.setDisable(false);
		setSelectionContext(SyncTasks.NONE, false);
		try {
		   downloadClerc.proceedWithInitialFolderScan( terrasynchFolder );
		} catch (ToolException ex) {	}
		break;
	   }
	   case MISSION_CRASHED : {
		abortBTN.setDisable(true);
		downloadClerc.clearGridBoard();
		downloadClerc.clearPointerBoard();
		preemptiveActionRunning = false;
		clearSelectionBTN.setDisable(false);
		alertPost(syncRoom.trdTaskNotice);
		postIt("Please chose an Action, another Replicate, or Leave.");
		setSelectionContext(SyncTasks.NONE, false);
		try {
		   downloadClerc.proceedWithInitialFolderScan( terrasynchFolder );
		} catch (ToolException ex) {	}
		break;
	   }
	   case MISSION_ABORTED : {
		abortBTN.setDisable(true);
		downloadClerc.clearGridBoard();
		downloadClerc.clearPointerBoard();
		preemptiveActionRunning = false;
		clearSelectionBTN.setDisable(false);
		postIt("Please chose another Action, or go back.");
		setSelectionContext(SyncTasks.NONE, false);
		try {
		   downloadClerc.proceedWithInitialFolderScan( terrasynchFolder );
		} catch (ToolException ex) {	}
	   }
	   default :
	}
   }


   public void reportSyncObjectsTermination()
    {
	abortBTN.setDisable(true);
	downloadClerc.clearGridBoard();
	downloadClerc.clearPointerBoard();
	preemptiveActionRunning = false;
	pausedPost(syncRoom.trdTaskNotice);
	postIt("Please chose another Action, or go back.");
	clearSelectionBTN.setDisable(false);
	setSelectionContext(SyncTasks.NONE, false);
	try {
	   downloadClerc.proceedWithInitialFolderScan( terrasynchFolder );
	} catch (ToolException ex) {	}
   }


   public void reportClearTermination()
    {
	abortBTN.setDisable(true);
	downloadClerc.clearGridBoard();
	downloadClerc.clearPointerBoard();
	preemptiveActionRunning = false;
	pausedPost(syncRoom.trdTaskNotice);
	postIt("Please chose another Action, or go back.");
	clearSelectionBTN.setDisable(false);
	setSelectionContext(SyncTasks.NONE, false);
	try {
	   downloadClerc.proceedWithInitialFolderScan( terrasynchFolder );
	} catch (ToolException ex) {	}
   }


   // POSTING methods for the Sync Thread

    public void notifyDownloadMessage()
    {
   }

   public void notifyDownloadAlertMessage()
    {
   }

   public void notifyDownloadFatalMessage()
    {
	fatalPost("From SYNC Thread: " + syncRoom.trdTaskNotice);
   }
}

