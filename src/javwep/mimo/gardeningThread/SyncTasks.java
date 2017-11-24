/*
*  Written by Mimo on 07-Oct-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: A few TASK TYPES to pilot the Synchronization Thread
*  ---------------
*   Notes :
*/

package javwep.mimo.gardeningThread;


public enum SyncTasks {



   // Public Resources
   UGLY		((byte) -1),    // Invalid Task
   NONE		((byte)  0),    // Download selected tiles (in principle NOT already present in the Terrasync folder)
   INITSCAN		((byte)  2),    // Validate Synchronization for already downloaded tyles
   SYNCALL		((byte)  4),    // Synchronize by fresh LOAD or SYNC update for already downloaded tyles
   SYNCOBJ		((byte)  8),    // Validate Synchronization for already downloaded tyles for Objects only (if Terrain seems ok, however)
   SHOWPORT		((byte)  12),    // Select Tiles for AIRPORT info display
   CLEAR		((byte)  16);    // Select Tiles for CLEAR Action

   public final byte code;


// wwwwwwwwwwwwwwwwwwwwwwwwwww
//			MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   SyncTasks( byte theCode )
    {
	code = theCode;
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//			MAINPROGRAMSECTION  1 - SERVICES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   public static SyncTasks getStateByCode( byte theCode )
    {
	for ( SyncTasks aState : SyncTasks.values() ) {
	   if ( aState.code == theCode ) {
		return aState;
	   }
	}
	return UGLY;
   }
}
