/*
*  Written by Mimo on 01-Oct-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: Common Memory to support synchronisation operations using a separate thread
*  ---------------
*   Notes :
*/

package javwep.mimo.gardeningThread;


import java.net.URL;
import java.nio.file.Path;
import javafx.application.Platform;
import javwep.mimo.gardenAccessMain.GardenAccessCTRL;
import javwep.mimo.gardeningObjects.GridCell;
import javwep.mimo.gardeningObjects.GridCellMatrix;
import threadManager.ThreadCst;
import threadManager.ThreadSas;


public class SyncSas extends ThreadSas
 {
   // Private Resources

   // public resources which are FIXED DURING THE WHOLE LIFE CYCLE OF THE SYNCHRONIZER (Set in Constructor)
   private final GardenAccessCTRL ctrlClerc;

   // Public resources which are FIXED DURING ONE SYNCHRONISING TASK
   public URL synchroSource;				// The root of the selected remote Terrasync Server
   public Path localTerraRootPath;			// The root of the selected local Terrasync Repository
   public GridCellMatrix selectedTilesMatrix;	// The GridBoxMatrix with Task candidates
   public GridCell tempRegionCell;			// A generic GridCell, initialized for the current Region...

   // Synchro Flags
   public SyncTasks mandatedTask;
   public boolean debugON = false, notificationCompleted = false;
   public int scannedItemCount, errorCount, updatedItemCount;
   public int intermediateCount, checkOutcome;



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // CONSTRUCTION
   //
   public SyncSas( GardenAccessCTRL theCtrl)
    {
	ctrlClerc = theCtrl;
   }


// wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww
//				   MAINPROGRAMSECTION  1 - REPORT TO GUI
// wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww

   // Reporting state and score of the Align Task
   //
   public void reportSyncRunningState()
    {
	trdTaskResult = ThreadCst.START_TASK;
	trdTaskNotice = "DOWNLOAD Task just started.";
	// To be run on the FX Thread !
	Platform.runLater( ctrlClerc::reportDownloadProgress);
   }

   public void reportSyncProgress()
    {
	trdTaskResult = ThreadCst.BUZY_STATE;
	trdTaskNotice = "Processing Tile " + scannedItemCount + " ; Updated: " + updatedItemCount + " ; Download Count: " + intermediateCount;
	// To be run on the FX Thread !
	Platform.runLater( ctrlClerc::reportDownloadProgress);
   }

   public void reportSyncCompletedState()
    {
	trdTaskResult = ThreadCst.MISSION_COMPLETED;
	trdTaskNotice = "Download Task did succeed; total Updated Tiles : " + scannedItemCount + " / Items " + updatedItemCount;
	// To be run on the FX Thread !
	Platform.runLater( ctrlClerc::reportDownloadProgress);
    }

   public void reportSyncAbortedState()
    {
	trdTaskResult = ThreadCst.MISSION_ABORTED;
	trdTaskNotice = "Download Task unable to Load any Tile - Check Network connection and retry later...";
	// To be run on the FX Thread !
	Platform.runLater( ctrlClerc::reportDownloadProgress);
   }

   public void reportSyncCrashedState()
    {
	trdTaskResult = ThreadCst.MISSION_CRASHED;
	trdTaskNotice = "CLONE Task completed with errors : " + errorCount;
	// To be run on the FX Thread !
	Platform.runLater( ctrlClerc::reportDownloadProgress);
    }

   public void reportClearCompletedState()
    {
	trdTaskResult = ThreadCst.MISSION_COMPLETED;
	// To be run on the FX Thread !
	Platform.runLater( ctrlClerc::reportClearTermination );
    }

   public void reportSyncObjCompletedState()
    {
	trdTaskResult = ThreadCst.MISSION_COMPLETED;
	// To be run on the FX Thread !
	Platform.runLater( ctrlClerc::reportSyncObjectsTermination );
    }

// wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww
//				   MAINPROGRAMSECTION  2 - NOTIFICATIONS
// wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww
   // Post a paused message
   public void notify( String theMessage )
    {
	trdTaskNotice = theMessage;
	notificationCompleted = false;
	// To be run on the FX Thread !
	Platform.runLater( ctrlClerc::notifyDownloadMessage );
   }

   public void notifyAlert( String theMessage )
    {
	trdTaskNotice = theMessage;
	notificationCompleted = false;
	// To be run on the FX Thread !
	Platform.runLater( ctrlClerc::notifyDownloadAlertMessage );
   }

   public void notifyLast( String theMessage )
    {
	trdTaskNotice = theMessage;
	notificationCompleted = false;
	// To be run on the FX Thread !
	Platform.runLater( ctrlClerc::notifyDownloadFatalMessage );
   }
}
