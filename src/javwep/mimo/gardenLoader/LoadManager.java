/*
*  Written by Mimo on 07-Oct-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: Organizing the DOWNLOAD Operations and launching the Thread TASKS
*  ---------------
*   Notes :
*/

package javwep.mimo.gardenLoader;


import appBasicToolKit.AppGroundKit;
import appContextLoader.AppCtxtManager;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javwep.mimo.gardeningObjects.*;
import static javwep.mimo.gardeningObjects.TileSyncStates.TOLOAD;
import static javwep.mimo.gardeningObjects.TileSyncStates.TOSYNC;
import javwep.mimo.gardeningThread.SyncSas;
import javwep.mimo.gardeningThread.SyncTasks;
import static javwep.mimo.gardeningThread.SyncTasks.*;
import logWriter.CleverLogger;
import threadManager.ThreadCst;
import toolException.ToolException;


public class LoadManager
 {
   // Private Resources
   private final AppGroundKit groundClerc;
   private final AppCtxtManager configClerc;
   private final CleverLogger logClerc;
   private final FreshnessAgent freshnessClerc;
   private DNSAgent dnsClerc;
   private HTTPAgent httpClerc;
   private Canvas gridBoard, selectBoard;
   private GraphicsContext gridBrush, selectBrush;
   private GridCell targetCell, tempCell, workerCell;
   private double regionScreenDim, regionEsriDim;		// the RegionSquare side dimension expressed in SCREEN and  ESRI contexts
   private double oneDegreeScreenDim;				// Scaling factors between the Region ESRI and the SCREEN domains
   private TileSpray tileSprayGun = null;
   private static final Pattern AIRPORT_PATTERN = AdHoc.AIRP_FILE_PATTERN;
   private Matcher matchClerc;
   private Path rootTerrasyncFolder, terrainsPath;
   private final SyncSas syncRoom;
   private GridCellMatrix currentStateMatrix, foreseenStateMatrix;
   private RectBox regionEsriGeometry;	//  the dimensions of the RegionSquare, expressed in the ESRI domain, int Long-Lat degrees
   private boolean inStartedState, inActivatedState, synchroChannelISOpen = false;
   private SyncTasks runningAction = null;

   // Public Resources




// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   public LoadManager( AppGroundKit theKit, SyncSas theSas )
    {
	groundClerc = theKit;
	configClerc = theKit.ctxtClerc;
	logClerc = theKit.logClerc;
	freshnessClerc = new FreshnessAgent(theKit);
	syncRoom = theSas;

	inStartedState = false;
	inActivatedState = false;
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  1 -  BUSINESS LOGIC
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Start this and set some parameters
   //
   public void start( Canvas theGrid, Canvas thePicker ) throws ToolException
    {
	if ( inStartedState ) {
	   throw new ToolException("This Loader is ALREADY started! Please check.");
	}

	gridBoard = theGrid;
	gridBrush = theGrid.getGraphicsContext2D();
	selectBoard = thePicker;
	selectBrush = thePicker.getGraphicsContext2D();

	// Get the Grid and Pointer boards dimension (two identical, exactly overlaping, squares); Compute SCALING factor
	regionScreenDim = theGrid.getWidth();
	regionEsriDim = AdHoc.zoomedRegionDim;
	oneDegreeScreenDim = ( regionScreenDim + 1 ) / regionEsriDim;
	if ( tileSprayGun == null ) {
	   tileSprayGun = new TileSpray(oneDegreeScreenDim);
	}

	// Set GRID Graphic opacity
	gridBrush.setGlobalAlpha(0.80);
	selectBrush.setGlobalAlpha(0.80);

	// Initialize the gridBox Matrices for CURRENT and FUTURE Actions-requesting boxes, and the Region Agent
	currentStateMatrix = new GridCellMatrix(logClerc, gridBoard, oneDegreeScreenDim);
	foreseenStateMatrix = new GridCellMatrix(logClerc, selectBoard, oneDegreeScreenDim);

	// Initialize the DNS Lookup assistant, Terrain subfolder... and the everywhere used ".dirindex" file name
	dnsClerc = new DNSAgent( groundClerc );
	httpClerc = new HTTPAgent( logClerc, true );

	inStartedState = true;
	logIt("    >> Detail Painter started; Parameters checked and internal Objects set.");
   }


   // Activates the Download Manager and set the selected Region ESRI coordinates, for the time the Downloading TAB is active: start Matrices
   //
   public void activateThisEsriRegion( RectBox theRegionEsriGeom ) throws ToolException
    {
	if ( inActivatedState ) {
	   throw new ToolException("The Loader is ALREADY activated for this REGION! Please check.");
	}

	try {
	   currentStateMatrix.invokeOnRegion( theRegionEsriGeom );
	   foreseenStateMatrix.invokeOnRegion( theRegionEsriGeom );
	   regionEsriGeometry = theRegionEsriGeom;
	   targetCell = new GridCell(theRegionEsriGeom, oneDegreeScreenDim);
	   tempCell = new GridCell(theRegionEsriGeom, oneDegreeScreenDim);
	   workerCell = new GridCell(theRegionEsriGeom, oneDegreeScreenDim);
	   runningAction = SyncTasks.NONE;
	   inActivatedState = true;

	} catch (ToolException ex) {
	   throw new ToolException(ex.getMessage());
	}
   }

   // Passivate the Download Manager, for the time the Selecting TAB is active: Revoke also Matrices
   //
   public void passivateCurrentRegion()
    {
	inActivatedState = false;
	regionEsriGeometry = null;
	currentStateMatrix.revoke();
	foreseenStateMatrix.revoke();
	targetCell = null;
	tempCell = null;
	workerCell = null;
   }


   // Set the Running ACTION ( for interpretation of further Mouse click on the Grid )
   //
   public void setRunningAction( SyncTasks theAction )
    {
	runningAction = theAction;
   }

   public SyncTasks getRunningAction()
    {
	return runningAction;
   }


   // Initialyze the Region with given Terrasync Folder, and paint the current Folder content, searching Tiles candidate for SYNC (populated or packed)
   //
   public int proceedWithInitialFolderScan( File theRootFolder ) throws ToolException
    {
	if ( ! inActivatedState ) {
	   throw new ToolException("The Loader is NOT activated for a REGION! Please check.");
	}

	// Set Terrasync Folder
	rootTerrasyncFolder = theRootFolder.toPath();
	logIt("    >> INIT SCAN started for Region at Long. " + regionEsriGeometry.leftX() + "° , Lat. " + regionEsriGeometry.bottomY() + "°.");
	logIt("       Terrasync folder set at: " + rootTerrasyncFolder.toString());

	try {
	   // Check TERRAIN folder reachable/existing
	   terrainsPath = rootTerrasyncFolder.resolve(AdHoc.TERR_TERRAIN);
	   if ( ! terrainsPath.toFile().isDirectory() ) {
		logIt("       Found NO Terrain Folder... Possibly a fresh Terrasync base ?");
		return -1;
	   }

	   // OK. Look after "Syncable" tiles
	   int cnt = FreshnessAgent.assessRegionSyncState(currentStateMatrix, tempCell, terrainsPath );
	   logIt("       Found " + cnt + " tiles with severe IOExceptions.");
	   if ( cnt < 10 ) {
		gridBoard.toFront();
		currentStateMatrix.renderAllCells();
	   }
	   return cnt;

	} catch (ToolException err) {
	   logIt("Error: " + err.getMessage());
	   throw new ToolException("Error SCANING the Terrasync Folder for this Region! See Log.");
	}
   }


   // Handling a CLICK on a Grid pointing a Box for action (CLEAR, LOAD, SYNC) ...
   //
   public void reflectGridCellPick( double theFocusX, double theFocusY, Label headLine ) throws ToolException
    {
	if ( ! inActivatedState ) {
	   throw new ToolException("The Loader is NOT activated for a REGION! Please check.");
	}

	// Set the cell to the anchorage of the Box containing the Focus point
	targetCell.setUsingScreenCoord(theFocusX, theFocusY);
	targetCell.setDisable(false);

	switch (runningAction) {
	   case SYNCALL : {
		// Action is always availabe, except for  UGLY state ; if ORPHAN, go for fresh load; else go for  sync
		if ( currentStateMatrix.getBoxStateByCell(targetCell) == TileSyncStates.ORPHAN ) {
		   foreseenStateMatrix.toggleStateOfCell(targetCell, TOLOAD);
		} else {
		   foreseenStateMatrix.toggleStateOfCell(targetCell, TOSYNC);
		}
		break;
	   }
	   case SYNCOBJ : {
		// Action is availabe only for PACKED states ; else go for SYNCALL load
		if ( currentStateMatrix.getBoxStateByCell(targetCell).code >= TileSyncStates.PACKED_1.code
			&& currentStateMatrix.getBoxStateByCell(targetCell).code <= TileSyncStates.PACKED_4.code ) {
		   foreseenStateMatrix.toggleStateOfCell(targetCell, TOSYNC);
		}
		break;
	   }
	   case CLEAR : {
		foreseenStateMatrix.toggleStateOfCell(targetCell, TileSyncStates.TOCLEAR);
		break;
	   }
	   default: return;
	}
	headLine.setText("Selected Tile at Longitude " + targetCell.getEsriLeftX()
							   + "°, Latitude " + targetCell.getEsriBottomY() + "°.");
   }


   // Clear the whole GridBoard
   //
   public void clearGridBoard()
    {
	if ( ! inActivatedState ) {   return;  }

	clearGrid();
	runningAction = SyncTasks.NONE;
	foreseenStateMatrix.resetAll();
   }


   // Clear the whole Pointer Board
   //
   public void clearPointerBoard()
    {
	if ( ! inActivatedState ) {   return;  }

	clearPointer();
	runningAction = SyncTasks.NONE;
	foreseenStateMatrix.resetAll();
   }



   // Get hold of the Target CELL
   //
   public GridCell getTargetCell() throws ToolException
    {
	if ( ! inActivatedState ) {
	   throw new ToolException("The Loader is NOT activated for a REGION! Please check.");
	}
	return targetCell;
   }


   // Enable/Disable the Target CELL
   //
   public void setTargetCellDisabled( boolean theOn )
    {
	targetCell.setDisable(theOn);
   }


   // Resolve the currently selected Box SCREEN coordinates into a GridCell, and prepare the Airport listing, if Not an ORPHAN Tile..
   //
   public void loadListOfAirportsByFocusCoords( double theFocusX, double theFocusY,
								 Path theBase, ArrayList<String> theList, Label headLine) throws ToolException
    {
	if ( ! inActivatedState ) {
	   throw new ToolException("The Loader is NOT activated for a REGION! Please check.");
	}

	// First Clear the previous Selected Box decoration, if already used
	if ( targetCell.isEnabled() ) {
	   selectBrush.clearRect(targetCell.getScreenLeftU(), targetCell.getScreenTopV(), oneDegreeScreenDim, oneDegreeScreenDim);
	}
	// Set the cell to the anchorage of the Box containing the Focus point
	targetCell.setUsingScreenCoord(theFocusX, theFocusY);
	targetCell.setDisable(false);

	// Don't loose time handling unloaded tiles...
	if ( currentStateMatrix.getBoxStateByCell(targetCell) == TileSyncStates.ORPHAN ) {
	   targetCell.setDisable(true);
	   headLine.setText("Selected Tile at Longitude " + targetCell.getEsriLeftX()
								   + "°, Latitude " + targetCell.getEsriBottomY() + "° is NOT populated.");
	   return;
	}
	// OK, apply the decoration...
	selectBrush.setFill(AdHoc.selectedFillColor);
	selectBrush.fillRect(targetCell.getScreenLeftU() + 2, targetCell.getScreenTopV() + 2, oneDegreeScreenDim - 4, oneDegreeScreenDim - 4);

	// Load that list...
	theList.clear();
	// Compute Terrain Path
	Path tileTerrainPath = theBase.resolve(AdHoc.TERR_TERRAIN)
							   .resolve( TileNames.getHectoTileNameForCell(targetCell) )
									.resolve( TileNames.getTileNameForCell(targetCell) );

	try ( DirectoryStream<Path> dirContent = Files.newDirectoryStream( tileTerrainPath ) ) {
	   for ( Path aFile : dirContent ) {
		if ( Files.isRegularFile(aFile) ) {
		   matchClerc = AIRPORT_PATTERN.matcher( aFile.getFileName().toString() );
		   if ( matchClerc.matches() ) {
			theList.add(matchClerc.group(1));
		   }
		}
	   }
	   headLine.setText("Selected Tile at Longitude " + targetCell.getEsriLeftX()
							+ "°, Latitude " + targetCell.getEsriBottomY() + "° has " + theList.size() + " airports.");

	} catch (IOException err) {
	   headLine.setText("Got an error while looking for airports at Longitude " + targetCell.getEsriLeftX()
													+ "°, Latitude " + targetCell.getEsriBottomY() + "°.");
	   logIt("" + err.getMessage());
	}
   }


   // Get a List of the currently selected Tiles
   //
   public void logListOfSelectedTiles()
    {
	switch ( runningAction ) {
	   case SYNCALL : {
		foreseenStateMatrix.logSelectionTarget(runningAction);
		break;
	   }
	   case CLEAR : {
		foreseenStateMatrix.logSelectionTarget(runningAction);
		break;
	   } case SYNCOBJ : {
		foreseenStateMatrix.logSelectionTarget(runningAction);
		break;
	   }
	   default:
	}
	logIt("");
	logFlush();
   }


   // Find a valid synchronisation Repository
   //
   public String openSyncSession() throws ToolException
    {
	if ( ! inActivatedState ) {
	   throw new ToolException("The Loader is NOT activated for a REGION! Please check.");
	}

	synchroChannelISOpen = false;
	logIt("");
	logIt(" >>> Opening a SYNCHRONIZE Session, at " + Instant.now().toString());
	syncRoom.synchroSource = dnsClerc.findPreferedSource();
	logIt("   >>> Sync Session opened; Source root at:" + syncRoom.synchroSource.toExternalForm());
	logIt("");
	logFlush();
	synchroChannelISOpen = true;
	return syncRoom.synchroSource.toExternalForm();
   }


   // Fix the current Synchronization Session Mandate - The DownloadManager  already knows the Action to perform
   //
   public void executeAction( File theTarget, Thread theActor )
    {
	if ( ! inActivatedState ) {  return; }

	syncRoom.mandatedTask = runningAction;
	syncRoom.localTerraRootPath = theTarget.toPath();
	syncRoom.selectedTilesMatrix = foreseenStateMatrix;
	syncRoom.tempRegionCell = workerCell;

	if ( syncRoom.threadISIdle() ) {
	   syncRoom.trdMission = ThreadCst.START_TASK;
	   syncRoom.trdTaskResult = ThreadCst.MISSION_REQUESTED;
	   theActor.interrupt();

	} else {
	   System.out.println("Thread is NOT IDLE !" );	   //	A CHANGER POUR ALERTPOST
	}
   }


   // Returns the item selected in the Browsing View
   //
   public void getDirindexItemsFrom( String theTarget, ArrayList<String> theResult )
    {
	try {
	   httpClerc.loadDirindexAt(new URL(theTarget), theResult);

	} catch (ToolException err) {
	   switch (err.getCode()) {
		case 404 : {
		   logIt(" <!> Got a 404 Error on: " + theTarget);
		   logIt("     Tile possibly NOT EXISTING in Terrasync: please check. Skip for now.");
		   break;
		}
		default : {
		   logIt(" <!> ToolException while LOADING Dirindex at: " + theTarget);
		   logIt("       " + err.getMessage());
		}
	   }
	} catch (MalformedURLException ex) {
	   logIt(" <!> Malformed URL: " + ex.getMessage());
	}
   }


   // Get some isolated File loaded...
   //
   public boolean loadAndSaveDIDSucceed( URL theSource, Path theTarget)
    {
	try {
	   httpClerc.loadFileContentFrom_To(theSource, theTarget);

	} catch (ToolException err) {
	   switch (err.getCode()) {
		case 404 : {
		   logIt(" <!> Got a 404 Error on: " + theSource.toExternalForm());
		   break;
		}
		default : {
		   logIt(" <?> Dir/File IO Error on " + theTarget.toString());
		   logIt("     " + err.getMessage());
		}
	   }
	   return false;
	}
	return true;
   }





// wwwwwwwwwwwwwwwwwwwwwwwwwww
//			MAINPROGRAMSECTION  2 - SPECIFIC UTILITIES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Clear the GRID Board Surface
   private void clearGrid()
    {
	gridBrush.clearRect(0, 0, regionScreenDim, regionScreenDim);
   }

   // Clear the POINTER Board Surface
   public void clearPointer()
    {
	selectBrush.clearRect(0, 0, regionScreenDim, regionScreenDim);
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


   // Timer utility
   //
   public void getAPauseOf(int milliSec)
    {
	try {
	   Thread.sleep(milliSec);
	} catch (InterruptedException e) {
	}
   }
}

