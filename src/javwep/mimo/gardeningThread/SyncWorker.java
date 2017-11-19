/*
*  Written by Mimo on 01-Oct-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: Handling the TERRASYNC "slow" synchronisation operations in a separate thread
*  ---------------
*   Notes :
*/

package javwep.mimo.gardeningThread;


import appBasicToolKit.AppGroundKit;
import appContextLoader.AppCtxtManager;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javwep.mimo.gardenLoader.HTTPAgent;
import javwep.mimo.gardeningObjects.AdHoc;
import javwep.mimo.gardeningObjects.GridCell;
import javwep.mimo.gardeningObjects.TileNames;
import javwep.mimo.gardeningObjects.TileSyncStates;
import static javwep.mimo.gardeningObjects.TileSyncStates.TOLOAD;
import static javwep.mimo.gardeningObjects.TileSyncStates.TOSYNC;
import logWriter.CleverLogger;
import threadManager.ThreadCst;
import toolException.ToolException;


public class SyncWorker implements Runnable
 {
   // PRIVATE RESOURCES
   private static final int GRID_DIM = AdHoc.zoomedRegionDim;
   private static final int ERROR_404 = 404;
   private final static boolean CLEAR_AIRPORTS = true;
   private static Pattern AIRPORT_PATTERN, TERRAIN_PATTERN;
   private final AppGroundKit groundClerc;
   private final AppCtxtManager configClerc;
   private final HTTPAgent remoteIOClerc;
   private final SyncSas syncRoom;
   private final CleverLogger syncLogger;
   private final SyncStamper stampClerc;
   private GridCell tempCell;

   private final StringBuilder tempBaseURL, tempBasePath;
   private int tempRootURLLen, tempRootPathLen;
   private final char[] urlTileSubpath = new char[TileNames.tileSubpathLength];
   private final char[] ioTileSubpath = new char[TileNames.tileSubpathLength];

   private final StringBuilder dirindexShaStB, airportPathStB;
   private final ArrayList<String> dirindexItemsList, dirindexAirportsList;
   private final ArrayList<Path> tileFolderContentList;
   private final String SLASH;
   private URL dirindexURL = null;
   private Matcher matchClerc;

   //  INTERNAL FLAGS
   private boolean debugON = false, quitTaskWalkISRequested = false;



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // CONSTRUCTOR
   public SyncWorker(SyncSas theSas, AppGroundKit theKit)
    {
	// Get in touch with the common memory for IPC, initialize a specific Sync Logger, and other key objects
	syncRoom = theSas;
	groundClerc = theKit;
	configClerc = theKit.ctxtClerc;
	syncLogger = new CleverLogger();
	debugON = syncRoom.debugON;
	remoteIOClerc = new HTTPAgent(syncLogger, debugON);
	stampClerc = new SyncStamper(configClerc, syncLogger);

	// Fix generic parameters value
	SLASH = configClerc.getDirSep();
	TERRAIN_PATTERN = AdHoc.TILE_TERR_PATTERN;
	AIRPORT_PATTERN = AdHoc.TILE_AIRP_PATTERN;

	// Key pathes navigation support
	tempBasePath = new StringBuilder();	   // Uses standard URL '/' as separator
	tempBaseURL = new StringBuilder();	   // Uses local  system separator

	// Reusable containers
	dirindexItemsList = new ArrayList<>();
	tileFolderContentList = new ArrayList<>();
	dirindexAirportsList = new ArrayList<>();
	dirindexShaStB = new StringBuilder();
	airportPathStB = new StringBuilder(AdHoc.TERR_AIRPORTS);
   }




// wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww
//				   MAINPROGRAMSECTION  1 - CORE BUSINESS - MAIN RECURSION LOOP
// wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww

   //	THREAD TASK SWITCHER
   // =======================
   //
   @Override
   public void run()
    {
	// Set initial STATE to IDLE... except if Logger Open fails
	syncRoom.trdState = (loggerOpenFailed()) ? ThreadCst.EXIT_THREAD : ThreadCst.IDLE_STATE;

	// Main LOOP
	while( syncRoom.trdMission != ThreadCst.EXIT_THREAD ) {

	   // Check if there is a pending request to execute a task... If yes, start the process
	   if ( syncRoom.trdMission == ThreadCst.START_TASK ) {
		syncRoom.trdState = ThreadCst.BUZY_STATE;
		syncRoom.trdMission = ThreadCst.DO_NOTHING;
		syncRoom.trdTaskResult = ThreadCst.MISSION_CRASHED;	   // just in case of uncontrolled exit

		// Clean the launch pad...
		quitTaskWalkISRequested = false;
		syncRoom.intermediateCount = 0;
		syncRoom.scannedItemCount = 0;
		syncRoom.updatedItemCount = 0;

		// Execute requested TASK
		tempCell = syncRoom.tempRegionCell;
		tempCell.setDisable(false);
   		switch (syncRoom.mandatedTask) {

   		   case SYNCALL : {   		   // ==================== SYNC TERRAINS ====================
			logIt("");
			logIt("<<TASK>> Entering a new GLOBAL SYNCRO task at " + LocalDateTime.now().toString());
			logIt("         From: " + syncRoom.synchroSource.toExternalForm().substring(7) );
			logIt("           to: " + syncRoom.localTerraRootPath.toString());
			logIt("");
			syncRoom.reportSyncRunningState();

			executeSyncAllTask();

			logIt("");
			logIt("<<TASK>> Exiting a new GLOBAL SYNCRO task at " + LocalDateTime.now().toString());
			logIt("");
			break;
   		   }

   		   case CLEAR : {   		   // ====================== CLEAR TILES ======================
			logIt("");
			logIt("<<TASK>> Entering a new TILE CLEAR task at " + LocalDateTime.now().toString());
			logIt("         On: " + syncRoom.localTerraRootPath.toString());
			logIt("");
			syncRoom.reportSyncRunningState();

   			executeClearTileTask();

			logIt("");
			logIt("<<TASK>> Exiting a new TILE CLEAR task at " + LocalDateTime.now().toString());
			logIt("");
   			break;
   		   }

   		   case SYNCOBJ : {   		   // ==================== SYNC OBJECTS ====================
			logIt("");
			logIt("<<TASK>> Entering a new OBJECTS SYNCRO task at " + LocalDateTime.now().toString());
			logIt("         From: " + syncRoom.synchroSource.toExternalForm().substring(7) );
			logIt("           to: " + syncRoom.localTerraRootPath.toString());
			logIt("");
			syncRoom.reportSyncRunningState();

			executeSyncObjectsTask();

			logIt("");
			logIt("<<TASK>> Exiting a new OBJECTS SYNCRO task at " + LocalDateTime.now().toString());
			logIt("");
			break;
   		   }

		   default :
   		}
		syncLogger.addNL(2);
		logFlush();
		tempCell.setDisable(true);
		syncRoom.trdState = ThreadCst.IDLE_STATE;
	   }
	   //  Task completed - Wait for next Task with some idle time
	   getAPauseOf(2000);
	}  //	Loop until EXIT is asked...

	// Close the Log File
	if (syncLogger != null) { syncLogger.close();   }
	syncRoom.trdState = ThreadCst.LIMBO_STATE;	// Go for final sleep...
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//				   MAINPROGRAMSECTION  2 - THE SERVICES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // ============================================================================================================
   //  CLEAR Tiles - This walks the selected GRID BOX; when reaching a tile with state TOCLEAR, will remove Terrains and Objects (but NOT
   //	   Airports, which generally require very limited storage anyway).  Only FILES will be removed, not possible subdirectories
   //	   if any... Tile-specific folders will be deleted too IFF they are left empty.
   // ============================================================================================================
   private void executeClearTileTask()
    {
	int incidentsCount = 0;

	// 0. Set the global context of this TASK : Prepare the Destination Root location for reuse. CLEAR is purely local : no remote source needed...
	tempBasePath.setLength(0);
	tempBasePath.append(syncRoom.localTerraRootPath.toString()).append(SLASH);
	tempRootPathLen = tempBasePath.length();

	// 1. Loop over this Region selected tiles
	for (int i = 0; i < GRID_DIM; i++) {
	   for (int j = 0; j < GRID_DIM; j++) {

		tempCell.setUsingMatrixCoord(i, j);

		if ( syncRoom.selectedTilesMatrix.getBoxStateByCell(tempCell) != TileSyncStates.TOCLEAR ) { continue; 	}
		syncRoom.scannedItemCount++;

		// Prepare the offset subpath to reach this Tile data files...
		if ( ! TileNames.foundUrlSubpathForCell(urlTileSubpath, tempCell) ) { continue; }
		if ( ! TileNames.urlGotMutedToPath(urlTileSubpath, ioTileSubpath, SLASH.charAt(0)) ) { continue; }

		logIt("  <Goal:> CLEAR Data for TILE at " + TileNames.getHectoTileNameForCell(tempCell)
										   + SLASH + TileNames.getTileNameForCell(tempCell));
		logIt("");

		// Start wit TERRAINs (COULD clear AIRPORTS data IFF the last param was set to true... but this is NOT used here)
		// Prepare the offset subpath to reach this Tile data files...
		tempBasePath.setLength(tempRootPathLen);
		tempBasePath.append(AdHoc.TERR_TERRAIN).append(ioTileSubpath);
		incidentsCount += deleteTerrainAndAirportsAt( Paths.get(tempBasePath.toString()), ! CLEAR_AIRPORTS);

		// Now for OBJECTS
		// Prepare the offset subpath to reach this Tile data files...
		tempBasePath.setLength(tempRootPathLen);
		tempBasePath.append(AdHoc.TERR_OBJECTS).append(ioTileSubpath);
		incidentsCount += deleteObjectsAt( Paths.get(tempBasePath.toString()) );

		if ( syncRoom.trdMission == ThreadCst.ABORT_TASK ) {
		   logIt("  <!!> Got a Request for ABORTING the Task. Returning...");
		   syncRoom.trdMission = ThreadCst.BUZY_STATE;
		   syncRoom.reportSyncAbortedState();
		   return;
		}
	   }
	   if ( syncRoom.trdMission == ThreadCst.ABORT_TASK ) {
		logIt("  <!!> Got a Request for ABORTING the Task. Returning...");
		syncRoom.trdMission = ThreadCst.BUZY_STATE;
		syncRoom.reportSyncAbortedState();
		return;
	   }
	}
	if ( incidentsCount > 0 ) {
	   syncRoom.trdTaskNotice = "Clear completed with " + incidentsCount + " problems; See Log for details.";
	   logIt("  <!!> Got " + incidentsCount + " errors during this action. Please investigate.");
	} else {
	   syncRoom.trdTaskNotice = "Clear completed without problems!";
	   logIt("  <-> Clear completed without problems!");
	}
	syncRoom.reportClearCompletedState();
   }


   // ============================================================================================================
   //  SYNC Tiles - This walks the SELECTION GRID BOX; when reaching a tile with state TOLOAD or TOSYNC, will try aligning local Terrains, Objects
   //	   and Airports data on the given remote Terrasync server.  In principle, TOLOAD will be used for Tiles that are not yet traceable on the
   //	   local repository; TOSYNC will be used for Tiles having at least some ".btg.gz" files detected in its local Terrain folder... TOLOAD will
   //	   blindly download and write/replace data files locally (Terrains, Objects and Airports); TOSYNC will use sha-1 signatures to avoid
   //	   downloading files still valid, saving bandwidth and time.
   //	   When successful (enough...), both will edit a new little text file (by default .ZsyncStamp), recording the action date and a sha-1 dirindex
   //	   file signature, which may be used afterwards to assess this Tile local freshness (see SUBTASKS comment hereafter)
   // ============================================================================================================
   private void executeSyncAllTask()
    {
	// 0. Set the global context of this TASK : Prepare the Source and Destination Root locations for reuse (both may be changed between two tasks...)
	tempBaseURL.setLength(0);
	tempBaseURL.append(syncRoom.synchroSource.toExternalForm()).append("/");
	tempRootURLLen = tempBaseURL.length();
	tempBasePath.setLength(0);
	tempBasePath.append(syncRoom.localTerraRootPath.toString()).append(SLASH);
	tempRootPathLen = tempBasePath.length();

	// 1. Loop over this Region selected tiles
	for (int i = 0; i < GRID_DIM; i++) {
	   for (int j = 0; j < GRID_DIM; j++) {

		tempCell.setUsingMatrixCoord(i, j);

		// Prepare the offset subpath to reach this Tile data files...
		if ( ! TileNames.foundUrlSubpathForCell(urlTileSubpath, tempCell) ) { continue; }
		if ( ! TileNames.urlGotMutedToPath(urlTileSubpath, ioTileSubpath, SLASH.charAt(0)) ) { continue; }

		switch ( syncRoom.selectedTilesMatrix.getBoxStateByCell(tempCell) ) {
		   case TOLOAD:
			logIt("  <Goal:> LOAD Data for TILE at " + new String(urlTileSubpath));;
			logIt("");
			syncRoom.scannedItemCount++;
			syncRoom.updatedItemCount += getLOADAllSubtaskResult();
			break;
		   case TOSYNC:
			logIt("  <Goal:> SYNC Data for TILE at " + new String(urlTileSubpath));
			logIt("");
			syncRoom.scannedItemCount++;
			syncRoom.updatedItemCount += getSYNCAllSubtaskResult();
			break;
		   default:
			continue;
		}
		if ( syncRoom.trdMission == ThreadCst.ABORT_TASK ) {
		   logIt("  <!!> Got a Request for ABORTING the Task. Returning...");
		   syncRoom.trdMission = ThreadCst.BUZY_STATE;
		   syncRoom.reportSyncAbortedState();
		   return;
		}
	   }
	   if ( syncRoom.trdMission == ThreadCst.ABORT_TASK ) {
		   logIt(" <!!> Got a Request for ABORTING the Task. Returning...");
		   syncRoom.trdMission = ThreadCst.BUZY_STATE;
		   syncRoom.reportSyncAbortedState();
		   return;
	   }
	}

	if ( syncRoom.updatedItemCount > 0 ) {
	   syncRoom.reportSyncCompletedState();
	} else {
	   syncRoom.reportSyncCrashedState();
	}
   }


   // ============================================================================================================
   //  SYNC OBJECTS - Walks the SELECTION GRID BOX; when reaching a Tile with state TOSYNC, will try to align local Objects data on the given
   //	   remote Terrasync server IFF the Tile is Up-To-Date (comparing remote dirindex SHA signature with the local Terrain ZsyncStamp
   //	   - which means that the action will be skiped for tiles without such ZsyncStamp).  TOSYNC will use sha1 signatures to avoid
   //	   downloading files still valid, saving bandwidth and time.
   //	   When successful (enough...), a new ZsyncStamp will be created in the Object folder...
   // ============================================================================================================

   private void executeSyncObjectsTask()
    {
	int itemsCount = 0, updatedCount = 0;

	// 0. Set the global context of this TASK : Prepare the Source and Destination Root locations for reuse (both may be changed between two tasks...)
	tempBaseURL.setLength(0);
	tempBaseURL.append(syncRoom.synchroSource.toExternalForm()).append("/");
	tempRootURLLen = tempBaseURL.length();
	tempBasePath.setLength(0);
	tempBasePath.append(syncRoom.localTerraRootPath.toString()).append(SLASH);
	tempRootPathLen = tempBasePath.length();

	// 1. Loop over this Region selected tiles
	for (int i = 0; i < GRID_DIM; i++) {
	   for (int j = 0; j < GRID_DIM; j++) {

		tempCell.setUsingMatrixCoord(i, j);

		try {
		   if ( syncRoom.selectedTilesMatrix.getBoxStateByCell(tempCell) != TileSyncStates.TOSYNC ) { continue; 	}
		   syncRoom.scannedItemCount++;

		   // Prepare the offset subpath to reach this Tile data files...
		   if ( ! TileNames.foundUrlSubpathForCell(urlTileSubpath, tempCell) ) { continue; }
		   if ( ! TileNames.urlGotMutedToPath(urlTileSubpath, ioTileSubpath, SLASH.charAt(0)) ) { continue; }

		   // 1. Set the SUBTASK context for THIS tile
		   tempBaseURL.setLength(tempRootURLLen);
		   tempBasePath.setLength(tempRootPathLen);
		   // 2. First check TERRAIN currency: if not Up-to-Date, loop (outdated terrain needs a refresh first !)
		   tempBaseURL.append(AdHoc.TERR_TERRAIN).append(urlTileSubpath);
		   tempBasePath.append(AdHoc.TERR_TERRAIN).append(ioTileSubpath);
		   Path terrainPath = Paths.get(tempBasePath.toString());
		   dirindexURL = new URL( tempBaseURL.toString() + "/" + AdHoc.TERR_DIRINDEX );
		   remoteIOClerc.loadDirindexAt(dirindexURL, dirindexItemsList);
		   if ( dirindexItemsList.size() < 3 ) {
			logIfVerbose(" <!> No, or Unusable TERRAIN dirindex at: " + dirindexURL.toExternalForm());
			logIfVerbose("     NO Tile data will be synchronized!");
			continue;	// No Terrain data --> No update at all
		   }

		   // 3. If remote dirindex Hash does not match the local Hash(recovered from current ZsyncStamp), this Tile needs a GLOBAL Sync...
		   if ( stampClerc.checkRemoteDirindexOnStamp( dirindexItemsList, terrainPath ) != SyncStamper.SUCCESS ) {
			logIt("  <!> This tile needs a GLOBAL Sync: " + terrainPath.toString());
			continue;
		   }

		   // 4. Go after OBJECTS...
		   tempBaseURL.setLength(tempRootURLLen);
		   tempBasePath.setLength(tempRootPathLen);
		   tempBaseURL.append(AdHoc.TERR_OBJECTS).append(urlTileSubpath);
		   tempBasePath.append(AdHoc.TERR_OBJECTS).append(ioTileSubpath);
		   Path objectPath = Paths.get(tempBasePath.toString());
		   logIfVerbose("     --> 3. Objects:");
		   dirindexURL = new URL( tempBaseURL.toString() + "/" + AdHoc.TERR_DIRINDEX );
		   remoteIOClerc.loadDirindexAt(dirindexURL, dirindexItemsList);
		   if ( dirindexItemsList.size() < 3 ) {
			logIfVerbose(" <!> No, or Unusable OBJECTs dirindex at: " + dirindexURL.toExternalForm());
			logIfVerbose("     NO Object data will be synchronized!");
			continue;
		   }
		   // 5. If remote dirindex Hash is not equal to local Hash (or the later does'nt exist or is damaged), then try a Sync of Objects- else, skip !
		   if ( stampClerc.checkRemoteDirindexOnStamp( dirindexItemsList, objectPath ) == SyncStamper.SUCCESS ) { continue;  }

		   itemsCount = loadFilesUsingDirindex(dirindexItemsList, tempBaseURL, objectPath, true);
		   updatedCount += itemsCount;
		   syncRoom.intermediateCount += itemsCount;
		   if ( itemsCount >= 0 ) {
			// 6. Update the local ZsyncStamp for OBJECTS
			if ( ! stampClerc.stampFileWritten( objectPath, stampClerc.getDirindexSignature(dirindexItemsList) ) ) {
			   logIt("  <!> Unable to write a new ZSYNC STAMP for OBJECTS...");
			   logIt("");
			}
			if ( itemsCount == 0 ) {
			   logIfVerbose("     --> All Objects files ( " + (dirindexItemsList.size() - 2) + " ) were found Up-to-Date");
			} else {
			   logIfVerbose("     --> " + itemsCount + " objects files (on " + (dirindexItemsList.size() - 2) + " ) updated");
			}
		   } else {
			stampClerc.stampFileWritten( objectPath, "<SYNC did not SUCCEED>");  // Leave an incomplete Load withness
		   }

		} catch (ToolException err) {
		   switch (err.getCode()) {
			case ERROR_404 : {
			   logIt(" <!> Got a 404 Error on: " + dirindexURL.toExternalForm());
			   logIt("     Tile possibly NOT EXISTING in Terrasync: please check. Skip for now.");
			   break;
			}
			default : {
			   logIt(" <!> ToolException while LOADING Dirindex at: " + dirindexURL.toExternalForm());
			   logIt("       " + err.getMessage());
			}
		   }
		} catch (MalformedURLException ex) {
		   logIt(" <!> Malformed URL: " + ex.getMessage());
		}
		logFlush();
		if ( syncRoom.trdMission == ThreadCst.ABORT_TASK ) {
		   logIt("  <!!> Got a Request for ABORTING the Task. Returning...");
		   syncRoom.trdMission = ThreadCst.BUZY_STATE;
		   syncRoom.reportSyncAbortedState();
		   return;
		}
		syncRoom.reportSyncProgress();
	   }
	   if ( syncRoom.trdMission == ThreadCst.ABORT_TASK ) {
		logIt("  <!!> Got a Request for ABORTING the Task. Returning...");
		syncRoom.trdMission = ThreadCst.BUZY_STATE;
		syncRoom.reportSyncAbortedState();
		return;
	   }
	}

	if ( syncRoom.updatedItemCount > 0 ) {
	   syncRoom.trdTaskNotice = "Task completed: " + syncRoom.updatedItemCount + " OBJECTS have been updated.";
	   logIt("  <-> Task completed: " + syncRoom.updatedItemCount + " OBJECTS have been updated.");
	} else {
	   syncRoom.trdTaskNotice = "Task completed: NO OBJECTS needed to be been updated.";
	   logIt("  <-> Task completed with NO OBJECTS needing to be been updated.!");
	}
	syncRoom.reportSyncObjCompletedState();
   }


   // SUBTASK =====================================
   //
   // Execute a pure "LOAD" subtask :  fresh data are downloaded for tiles which are not present locally
   //
   private int getLOADAllSubtaskResult()
    {
	int itemsCount = 0, updatedCount = 0;

	try {
	   // 1. Set the SUBTASK context for THIS tile
	   tempBaseURL.setLength(tempRootURLLen);
	   tempBasePath.setLength(tempRootPathLen);

	   // 2. First handle TERRAINS : set base locations for this tile (for instance, "/Terrain/w020n70/w018n73")
	   tempBaseURL.append(AdHoc.TERR_TERRAIN).append(urlTileSubpath);
	   tempBasePath.append(AdHoc.TERR_TERRAIN).append(ioTileSubpath);
	   Path terrainPath = Paths.get(tempBasePath.toString());
	   logIfVerbose("     --> 1. Terrains:");
	   dirindexURL = new URL( tempBaseURL.toString() + "/" + AdHoc.TERR_DIRINDEX );
	   remoteIOClerc.loadDirindexAt(dirindexURL, dirindexItemsList);
	   if ( dirindexItemsList.size() < 3 ) {	// Do not check "path:" consistency, since probalility is very low... (?)
		logIfVerbose(" <!> Unusable TERRAIN dirindex at: " + dirindexURL.toExternalForm());
		logIfVerbose("     NO Tile data will be loaded!");
		return 0;	// No Terrain data --> No update at all
	   }

	   // 3.  Try loading the dirindex-referenced terrains items
	   itemsCount = loadFilesUsingDirindex(dirindexItemsList, tempBaseURL, terrainPath, false);
	   updatedCount += itemsCount;
	   syncRoom.intermediateCount += itemsCount;
	   if ( itemsCount < dirindexItemsList.size() - 2 ) {
		logIt(" <!> ERROR during TERRAIN load; Tile data will NOT be FULLY loaded, please investigate!");
		return updatedCount;	// No  full Terrain data --> No Airports, no Objects load
	   }
	   // Save Terrain dirindex signature for ZsyncStamp
	   dirindexShaStB.setLength(0);
	   dirindexShaStB.append(stampClerc.getDirindexSignature(dirindexItemsList));

	   // 4. Now look for AIRPORTS - If any, then Load this content. First reset context...
	   tempBaseURL.setLength(tempRootURLLen);
	   tempBasePath.setLength(tempRootPathLen);
	   tempBaseURL.append(AdHoc.TERR_AIRPORTS);
	   tempBasePath.append(AdHoc.TERR_AIRPORTS);
	   logIfVerbose("     --> 2. Airports:");
	   itemsCount = loadAirportFilesUsingDirindex(dirindexItemsList, tempBaseURL, tempBasePath, false);
	   updatedCount += itemsCount;
	   syncRoom.intermediateCount += itemsCount;

	   // 5. Go after OBJECTS...
	   tempBaseURL.setLength(tempRootURLLen);
	   tempBasePath.setLength(tempRootPathLen);
	   tempBaseURL.append(AdHoc.TERR_OBJECTS).append(urlTileSubpath);
	   tempBasePath.append(AdHoc.TERR_OBJECTS).append(ioTileSubpath);
	   Path objectPath = Paths.get(tempBasePath.toString());
	   logIfVerbose("     --> 3. Objects:");
	   dirindexURL = new URL( tempBaseURL.toString() + "/" + AdHoc.TERR_DIRINDEX );
	   remoteIOClerc.loadDirindexAt(dirindexURL, dirindexItemsList);
	   if ( dirindexItemsList.size() < 3 ) {
		logIfVerbose(" <!> Unusable dirindex for Objects at: " + dirindexURL.toExternalForm());
		logIfVerbose("     Tile OBJECTS data will NOT be loaded (Skip)!");
	   } else {
		itemsCount = loadFilesUsingDirindex(dirindexItemsList, tempBaseURL, objectPath, false);
		updatedCount += itemsCount;
		syncRoom.intermediateCount += itemsCount;
		if ( itemsCount < dirindexItemsList.size() - 2 ) {
		   logIt(" <!> ERROR during OBJECTS load; Tile Object data will NOT be FULLY loaded, please investigate!");
		   stampClerc.stampFileWritten( objectPath, "<LOAD did not SUCCEED>");  // Leave an incomplete Load withness
		} else {	// Every object successfully downloaded - write a local ZsyncStamp for OBJECTS
		    if ( ! stampClerc.stampFileWritten( objectPath, stampClerc.getDirindexSignature(dirindexItemsList) ) ) {
			logIt("  <!> Unable to write a new ZSYNC STAMP for OBJECTS...");
			logIt("");
		   }
		}
	   }

	   // 6. Finally Edit a new ZsyncStamp for TERRAIN...
	   if ( ! stampClerc.stampFileWritten( terrainPath, dirindexShaStB.toString() ) ) {
		logIt("  <!> Unable to write a new ZSYNC STAMP for TERRAIN...");
		logIt("");
	   } else {
		logIfVerbose("    ---> Download SUBTask Completed with ZSYNCSTAMP update. <-----------");
		logIfVerbose("");
		updatedCount++;
	   }

	} catch (ToolException err) {
	   switch (err.getCode()) {
		case ERROR_404 : {
		   logIt(" <!> Got a 404 Error on: " + dirindexURL.toExternalForm());
		   logIt("     Tile possibly NOT EXISTING in Terrasync: please check. Skip for now.");
		   break;
		}
		default : {
		   logIt(" <!> ToolException while LOADING Dirindex at: " + dirindexURL.toExternalForm());
		   logIt("       " + err.getMessage());
		}
	   }
	} catch (MalformedURLException ex) {
	   logIt(" <!> Malformed URL: " + ex.getMessage());
	}
	logFlush();
	syncRoom.reportSyncProgress();
	return updatedCount;
   }


   // SUBTASK =====================================
   //
   // Execute a pure "LOAD" subtask :  fresh data are downloaded for tiles which are yet not present locally (existing files will be replaced, but none will be deleted!)
   //	   While close to "executeLOADSubTask", this one differs a little on feedbacks
   //
   private int getSYNCAllSubtaskResult()
    {
	int itemsCount = 0, updatedCount = 0;

	try {
	   // 1. Set the SUBTASK context for THIS tile
	   tempBaseURL.setLength(tempRootURLLen);
	   tempBasePath.setLength(tempRootPathLen);

	   // 2. First handle TERRAINS : set base locations for this tile (for instance, "/Terrain/w020n70/w018n73")
	   tempBaseURL.append(AdHoc.TERR_TERRAIN).append(urlTileSubpath);
	   tempBasePath.append(AdHoc.TERR_TERRAIN).append(ioTileSubpath);
	   Path terrainPath = Paths.get(tempBasePath.toString());
	   logIfVerbose("     --> 1. Terrains:");
	   dirindexURL = new URL( tempBaseURL.toString() + "/" + AdHoc.TERR_DIRINDEX );
	   remoteIOClerc.loadDirindexAt(dirindexURL, dirindexItemsList);
	   if ( dirindexItemsList.size() < 3 ) {
		logIfVerbose(" <!> Unusable TERRAIN dirindex at: " + dirindexURL.toExternalForm());
		logIfVerbose("     NO Tile data will be loaded!");
		return 0;	// No Terrain data --> No update at all
	   }

	   // 3.  Try loading the dirindex-referenced terrains items IFF they are "outdated"
	   itemsCount = loadFilesUsingDirindex(dirindexItemsList, tempBaseURL, terrainPath, true);
	   updatedCount += itemsCount;
	   syncRoom.intermediateCount += itemsCount;
	   if ( itemsCount == 0 ) {
		logIfVerbose("     --> All Terrain files ( " + (dirindexItemsList.size() - 2) + " ) were found Up-to-Date");
		logIfVerbose("");
	   }
	   // Save Terrain dirindex signature for ZsyncStamp
	   dirindexShaStB.setLength(0);
	   dirindexShaStB.append(stampClerc.getDirindexSignature(dirindexItemsList));

	   // 4. Now look for AIRPORTS - If any, then Load this content. First reset context
	   tempBaseURL.setLength(tempRootURLLen);
	   tempBasePath.setLength(tempRootPathLen);
	   tempBaseURL.append(AdHoc.TERR_AIRPORTS);
	   tempBasePath.append(AdHoc.TERR_AIRPORTS);
	   logIfVerbose("     --> 2. Airports:");
	   itemsCount = loadAirportFilesUsingDirindex(dirindexItemsList, tempBaseURL, tempBasePath, true);
	   updatedCount += itemsCount;
	   syncRoom.intermediateCount += itemsCount;
	   if ( itemsCount == 0 ) {
		logIfVerbose("     --> All Airport files were found Up-to-Date");
		logIfVerbose("");
	   }

	   // 5. Go after OBJECTS...
	   tempBaseURL.setLength(tempRootURLLen);
	   tempBasePath.setLength(tempRootPathLen);
	   tempBaseURL.append(AdHoc.TERR_OBJECTS).append(urlTileSubpath);
	   tempBasePath.append(AdHoc.TERR_OBJECTS).append(ioTileSubpath);
	   Path objectPath = Paths.get(tempBasePath.toString());
	   logIfVerbose("     --> 3. Objects:");
	   dirindexURL = new URL( tempBaseURL.toString() + "/" + AdHoc.TERR_DIRINDEX );
	   remoteIOClerc.loadDirindexAt(dirindexURL, dirindexItemsList);
	   if ( dirindexItemsList.size() < 3 ) {
		logIfVerbose(" <!> Unusable dirindex for Objects at: " + dirindexURL.toExternalForm());
		logIfVerbose("     Tile OBJECTS data will NOT be loaded (Skip)!");
	   } else {
		itemsCount = loadFilesUsingDirindex(dirindexItemsList, tempBaseURL, objectPath, true);
		updatedCount += itemsCount;
		syncRoom.intermediateCount += itemsCount;
		if ( itemsCount >= 0 ) {
		   // Update the local ZsyncStamp for OBJECTS
		   if ( ! stampClerc.stampFileWritten( objectPath, stampClerc.getDirindexSignature(dirindexItemsList) ) ) {
			logIt("  <!> Unable to write a new ZSYNC STAMP for OBJECTS...");
			logIt("");
		   }
		   if ( itemsCount == 0 ) {
			logIfVerbose("     --> All Objects files ( " + (dirindexItemsList.size() - 2) + " ) were found Up-to-Date");
		   } else {
			logIfVerbose("     --> " + itemsCount + " objects files (on " + (dirindexItemsList.size() - 2) + " ) updated");
		   }
		} else {
		   stampClerc.stampFileWritten( objectPath, "<SYNC did not SUCCEED>");  // Leave an incomplete Load withness
		}
	   }

	   // 6. Finally Edit a new ZsyncStamp for TERRAIN...
	   if ( ! stampClerc.stampFileWritten( terrainPath, dirindexShaStB.toString() ) ) {
		logIt("  <!> Unable to write a new ZSYNC STAMP...");
		logIt("");
	   } else {
		logIfVerbose("    ---> Download SUBTask Completed with ZSYNCSTAMP update. <-----------");
		logIfVerbose("");
		logIfVerbose("");
		updatedCount++;
	   }

	} catch (ToolException err) {
	   switch (err.getCode()) {
		case ERROR_404 : {
		   logIt(" <!> Got a 404 Error on: " + dirindexURL.toExternalForm());
		   logIt("     Tile possibly NOT EXISTING in Terrasync: please check. Skip for now.");
		   break;
		}
		default : {
		   logIt(" <!> ToolException while LOADING Dirindex at: " + dirindexURL.toExternalForm());
		   logIt("       " + err.getMessage());
		}
	   }
	} catch (MalformedURLException ex) {
	   logIt(" <!> Malformed URL: " + ex.getMessage());
	}
	logFlush();
	syncRoom.reportSyncProgress();
	return updatedCount;
   }




// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  4. SPECIFIC UTILITIES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Try loading the  files (OUTDADET or ALL) included in a tile .dirindex into the target terrasync folder
   private int loadFilesUsingDirindex( ArrayList<String> theIndex, StringBuilder theSource, Path theDestin, boolean outdatedOnly )
    {
	int copyCnt = 0, copySize = 0;

	try {
	   // Prepare the Target folders structure, if needed...
	   Files.createDirectories(theDestin);

	   // Then loop over the dirIndex content
	   readIndex:
	   for (String aLine : theIndex) {
		matchClerc = TERRAIN_PATTERN.matcher(aLine);
		if ( ! matchClerc.matches() ) {
		   if ( ! aLine.startsWith("f:") ) { continue; 	}  //	Normal for the 2 first lines, which don't.
		   logIt(" <?> Unexpected dirindex format line: " + aLine );
		   continue;
		}
		if ( outdatedOnly && fileIsCurrent( theDestin.resolve(matchClerc.group(2)),
										   matchClerc.group(6), matchClerc.group(4)) ) {  continue;  }
		copySize += remoteIOClerc.getFileContent(new URL(theSource + "/" + matchClerc.group(2)),
										theDestin.resolve(matchClerc.group(2)) );
		syncRoom.intermediateCount++;
		syncRoom.reportSyncProgress();
		copyCnt++;
	   }

	   // Finally, add/rewrite the dirindex file itself
	   try ( PrintWriter dirindexOut = new PrintWriter( new BufferedWriter(
							   new FileWriter( theDestin.resolve(AdHoc.TERR_DIRINDEX).toFile() ) ) ) ){
		theIndex.forEach((aFile) -> { dirindexOut.println(aFile);    });
	   }
	   copyCnt++;
	   logIfVerbose("        -> Results: " + copyCnt + " files, " + copySize + " bytes, copied to "
												+ theDestin.toString().substring(tempRootPathLen));
	   logIfVerbose("");
	   logFlush();

	} catch (IOException err) {
	   logIt("   <!> IO Exception: " + err.getMessage());
	   return -1;
	} catch (ToolException err) {
	   logIt("   <!> " + err.getMessage());
	   return -2;
	}
	return copyCnt;
   }

   // Load (ALL or OUTDATED-only) Data for an AIRPORT given by its ICAO label in a  terrain dirindex list
   //
   private int loadAirportFilesUsingDirindex( ArrayList<String> theList, StringBuilder theSource, StringBuilder theDestin, boolean outdatedOnly )
    {
	int itemsCount = 0, tempAirportBaseURLLen = theSource.length(), tempAirportBasePathLen = theDestin.length();

	// Loop through the list in search of typical "f:EIGN.btg.gz:'d4e567..." pattern
	for (String aLine : theList) {
	   matchClerc = AIRPORT_PATTERN.matcher( aLine );
	   if ( matchClerc.matches() ) {
		try {
		   // If found, build the specific subpath...
		   theSource.setLength(tempAirportBaseURLLen);
		   theDestin.setLength(tempAirportBasePathLen);
		   for (int u = 0; u < 3; u++) {
			theSource.append("/").append(matchClerc.group(2).charAt(u));
			theDestin.append(SLASH).append(matchClerc.group(2).charAt(u));
		   }
		   // Download the remote dirindex for AIRPORTS, and check for usability
		   dirindexURL = new URL( theSource.toString() + "/" + AdHoc.TERR_DIRINDEX);
		   remoteIOClerc.loadDirindexAt(dirindexURL, dirindexAirportsList);

		   if ( dirindexItemsList.size() < 3 ) {
			logIfVerbose(" <!> Unreachable or unusable dirindex at: " + dirindexURL.toExternalForm());
			logIfVerbose("     Tile AIRPORTS data will NOT be loaded!");
		   } else {
			String ICAOcode = matchClerc.group(2);
			readIndex:
			for (String anAirLine : dirindexAirportsList) {
			   matchClerc = TERRAIN_PATTERN.matcher( anAirLine );
			   if ( matchClerc.matches() ) {
				if ( matchClerc.group(2).startsWith(ICAOcode) ) {

				   if ( outdatedOnly && fileIsCurrent( Paths.get( theDestin.toString(), matchClerc.group(2) ),
											matchClerc.group(6), matchClerc.group(4)) ) {  continue;  }
				   remoteIOClerc.getFileContent( new URL(theSource.toString() + "/" + matchClerc.group(2)),
										   Paths.get( theDestin.toString(), matchClerc.group(2) ) );
				   itemsCount++;
				}
			   }
			}
		   }

		} catch (MalformedURLException ex) {
		   logIt("Malformed URL: " + ex.getMessage());
		} catch (ToolException err) {
		   logIt("" + err.getMessage());
		}
	   }
	}
	logIfVerbose("        -> Results " + itemsCount + " AIRPORT files downloaded.");
	logIfVerbose("");
	return itemsCount;
   }

   // Check a file for currency, checking for presence/readability, size, and sha1 signature...
   //
   private boolean fileIsCurrent( Path theFile, String theSize, String theSha )
    {
	try {
	   long fileSize = Long.parseLong(theSize);
	   if ( ! Files.isRegularFile(theFile)
				|| ! Files.isReadable(theFile)
						|| Files.size(theFile) != fileSize ) {  return false;  }
	   if ( theSha.equals(stampClerc.getMD5HashOfFile(theFile)) ) { return true; 	}

	} catch (NumberFormatException | IOException err) { }
	return false;
   }

   // Delete data files for tile objects
   //
   private int deleteObjectsAt( Path theFolder )
    {
	int undeletedCount, errFlag = 0;

	undeletedCount = listFileNotDirectoryIn(theFolder);
	if ( undeletedCount < 0 ) {
	   errFlag++;
	} else {
	   // Try deleting the Files and the Tile folder
	   for ( Path aPath : tileFolderContentList ) {
		try {
		   Files.delete(aPath);
		   logIt("    > Deleted Object file: " + aPath.getParent() + " / " + aPath.getFileName());
		   syncRoom.updatedItemCount++;

		} catch (IOException ex) {
		   logIt(" <!> IOException deleting Tile Object File " + aPath.getParent().toString()
											+ " / " + aPath.getFileName().toString());
		   logIt("   >> " + ex.getMessage());
		   undeletedCount++;
		   errFlag++;
		}
	   }
	   if ( undeletedCount == 0 ) {
		try {
		   Files.delete(theFolder);
		   logIt("        -> Deleting Object folder: " + theFolder.getFileName().toString());
		   syncRoom.updatedItemCount++;

		} catch (IOException ex) {
		   logIt(" <!> IOException deleting Tile Object Folder " + theFolder.getFileName().toString());
		   logIt("   >> " + ex.getMessage());
		   errFlag++;
		}
	   }
	}
	return errFlag;
   }

   // Delete data files for tile terrain and terrain-referenced airports
   private int deleteTerrainAndAirportsAt( Path theFolder, boolean airportToo )
    {
	int undeletedCount, errFlag = 0;

	undeletedCount = listFileNotDirectoryIn(theFolder);
	if ( undeletedCount < 0 ) {
	   errFlag++;
	} else {
	   // Try deleting the Files, the referenced airports, and the Tile folder
	   for ( Path aPath : tileFolderContentList ) {
		String fileName = aPath.getFileName().toString();
		// If aiportToo is set, look for Airports... - this option is NOT used for now, in principle...
		if ( airportToo && fileName.length() > 5 && fileName.charAt(4) == '.' ) {	// Most probably an Airport...
		   deleteAirportContentForICAO(fileName.substring(0, 4));
		}
		try {
		   Files.delete(aPath);
		   logIt("    > Deleted Terrain file: " + fileName);
		   syncRoom.updatedItemCount++;

		} catch (IOException ex) {
		   logIt(" IOException deleting Tile Terrain File " + aPath.getParent().toString()
											+ " / " + aPath.getFileName().toString());
		   logIt("   >> " + ex.getMessage());
		   undeletedCount++;
		   errFlag++;
		}
	   }
	   if ( undeletedCount == 0 ) {
		try {
		   Files.delete(theFolder);
		   logIt("        -> Deleting Terrain folder: " + theFolder.getFileName().toString());
		   syncRoom.updatedItemCount++;

		} catch (IOException ex) {
		   logIt(" IOException deleting Tile Terrain Folder " + theFolder.getFileName().toString());
		   logIt("   >> " + ex.getMessage());
		   errFlag++;
		}
	   }
	}
	return errFlag;
   }


   // Try deleting the Airport data for a given ICAO name
   private int deleteAirportContentForICAO( String theICAO )
    {
	airportPathStB.setLength(AdHoc.TERR_AIRPORTS.length());
	for (int u = 0; u < 3; u++) {
	   airportPathStB.append(SLASH);
	   airportPathStB.append(theICAO.charAt(u));
	}
	try ( DirectoryStream<Path> airporContent
				= Files.newDirectoryStream(syncRoom.localTerraRootPath.resolve(airportPathStB.toString()) ) ) {
	   for ( Path afile : airporContent ) {
		if ( afile.getFileName().toString().startsWith(theICAO) ) {
		   Files.delete(afile);
		   logIt("      > Deleted Airport File: " + afile.getFileName());
		   syncRoom.updatedItemCount++;
		}
	   }
	   return 1;

	} catch (IOException err) {
	   logIt(" Error deleting Tile AIRPORT for ICAO " + theICAO);
	   logIt("   >> " + err.getMessage());
	   return -1;
	}
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww
//				   MAINPROGRAMSECTION  5 - ANCILLARY UTILITIES
// wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww

   // Specific Directory content reader, listing only ordinary files, and returning the number of subdir if any...
   private int listFileNotDirectoryIn( Path theSource )
    {
	int dirCnt = 0;
	tileFolderContentList.clear();
	try ( DirectoryStream<Path> dirContent = Files.newDirectoryStream( theSource ) ) {
	   for ( Path afile : dirContent ) {
		if ( Files.isDirectory(afile) ) {
		   dirCnt++;
		} else {
		tileFolderContentList.add(afile);
		}
	   }

	} catch (IOException err) {
	   logIt(" Error loading Tile Content for " + theSource.toString());
	   logIt("   >> " + err.getMessage());
	   dirCnt = -1;
	}
	return dirCnt;
   }

   // Opening the Sync-specific LOGING FILE
   public boolean loggerOpenFailed()
    {
	LocalDateTime rightNow = LocalDateTime.now();
	// Try opening Log in place with timestamp
	try {
	   String nowStr = "on " + rightNow.getYear() + "-" + rightNow.getMonthValue() + "-" + rightNow.getDayOfMonth()
				+ "_" + rightNow.getHour() + "h" + rightNow.getMinute() + "m" + rightNow.getSecond() + "s.log";
	   syncLogger.open(configClerc.get("Log_Location") + configClerc.get("Sync_LogName") + nowStr);

	} catch (ToolException err) {
	   if (syncLogger != null) { syncLogger.close();   }
	   lastPost("Fatal Error opening the SYNC LOGGER:\n" + err.getMessage()
					+ "\n(Log Directory not found ? ); Please try again, or Exit.");
	   return true;
	}

	// Write the header
	syncLogger.add("TERRASYNC Session launched at: " + rightNow.toString());
	syncLogger.add("");
	syncLogger.add("    Verbose logging is ON: " + debugON);
	syncLogger.writeDisk();
	return false;
   }


   // Posting Notifications and waiting for return (simulating a MODAL behaviour from inside this background Thread (NOT the FX one, which will NOT accept
   // a second invocation while the first is still pending !)

   private void lastPost(String theMessage)
    {
	if ( groundClerc.lockGrantedFor("lastSyncPost") ) {
	   syncRoom.notifyLast( theMessage );
	   do { getAPauseOf(1000); } while ( ! syncRoom.notificationCompleted );
	   groundClerc.dropLockFor("lastSyncPost");
	}
   }

   // Logger utility
   private void logIt(String theMessage)
    {
	syncLogger.add(theMessage);
   }

   private void logIfVerbose(String theMessage)
    {
	if ( debugON ) {
	   syncLogger.add(theMessage);
	}
   }

   private void logFlush()
    {
	syncLogger.writeDisk();
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


