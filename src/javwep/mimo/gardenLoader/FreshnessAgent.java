/*
*  Written by Mimo on 23-oct.-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: For any Cell and BasePath, look for FlightGear data traces in the relative folder...
*			- If nothing found, set the TileSyncState to ORPHAN : corresponding Tile has NO FlightGear data in its local corresponding folder;
*			- If at least one files with .btg.gz extension is found, set the TileSyncState to POPULATED;
*			- If a .dirindex  file is found, recover its LastModifiedTime and use it to set the freshness level of a Tile PACKED_n
*			   depending on the "terrainFreshnessGap" set in the sysContext file (PACKED_4: age <  Gap; PACKED_3: age < Gap * 2;
			   PACKED_2: age < Gap * 4; PACKED_4: age >= Gap * 4;
*  ---------------
*    Notes : For now, this one is only used to assess the Terrain folders data freshness; Terrains are probably more stable than Objects, but Objects have
*		their own Sync Action...
*/

package javwep.mimo.gardenLoader;


import appBasicToolKit.AppGroundKit;
import appContextLoader.AppCtxtManager;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import javwep.mimo.gardeningObjects.*;
import logWriter.CleverLogger;
import toolException.ToolException;


public class FreshnessAgent
 {
   // Private Resources
   private final AppGroundKit groundClerc;
   private static AppCtxtManager configClerc;
   private final CleverLogger logClerc;
   private final static ArrayList<String> fileContent = new ArrayList<>();
   private static final String BTGFILE_EXT = AdHoc.BTGFilesEXT;
   private static final String DIRINDEX_FILENAME = AdHoc.TERR_DIRINDEX;
   private static int terrainFreshnessGap, agingGap;
   private static long daysCount;
   private static byte freshnessRank;

   // Public Resources




// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   public FreshnessAgent( AppGroundKit theKit )
    {
	groundClerc = theKit;
	configClerc = theKit.ctxtClerc;
	logClerc = theKit.logClerc;
	terrainFreshnessGap = decodeFreshness(configClerc.get("Terrain_FRESHGAP", "180"));
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  1 -  SERVICES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Assess the Synchronization state of all the Tiles of the current Region, depending on their  related Folder content...
   //
   public static int assessRegionSyncState( GridCellMatrix theGrid, GridCell theCell, Path theBasePath ) throws ToolException
    {
	if ( ! theGrid.isInitialized() ) {
	   throw new ToolException("Boxes GRID has not been initialized!");
	}

	int uglyTilesCnt = 0;
	Path hectoFolderPath, tileFolderPath, indexFilePath;

	theCell.setDisable(false);
	for (int i = 0; i < theGrid.getGridDimension(); i++) {
	   for (int j = 0; j < theGrid.getGridDimension(); j++) {

		theCell.setUsingMatrixCoord(i, j);
		try {
		   // Check first for Orphan state : no data at all
		   hectoFolderPath = theBasePath.resolve(TileNames.getHectoTileNameForCell(theCell));
		   if ( ! hectoFolderPath.toFile().isDirectory() ) {  	// for instance, parent folder (e020n40) does not exist, or ...
			theGrid.setStateOfBoxByCell(theCell, TileSyncStates.ORPHAN);
			continue;
		   }
		   tileFolderPath =  hectoFolderPath.resolve(TileNames.getTileNameForCell(theCell));
		   if ( ! tileFolderPath.toFile().isDirectory()) {  	// ... or folder (e023n47) does not exist
			theGrid.setStateOfBoxByCell(theCell, TileSyncStates.ORPHAN);
			continue;
		   }
		   // Check for existing .dirindex file
		   indexFilePath = tileFolderPath.resolve(DIRINDEX_FILENAME);
		   if ( ! indexFilePath.toFile().isFile()) {
			// No .dirindex : search for download residues
			DirectoryStream tileContent = Files.newDirectoryStream( tileFolderPath, BTGFILE_EXT );
			if ( ! tileContent.iterator().hasNext() ) {
			   // This one seems equiped with NO relevant data and NO dirindex
			   theGrid.setStateOfBoxByCell(theCell, TileSyncStates.ORPHAN);
			   continue;
			}  // Else set as exposed to some previous download, but without more recent dirindex...
			theGrid.setStateOfBoxByCell(theCell, TileSyncStates.POPULATED);
			continue;
		   }
		   // Dirindex found... Check for freshness, based on LastModification Time
		   theGrid.setStateOfBoxByCell(theCell,
					   evaluateStateByDirindexAge( Files.getLastModifiedTime(indexFilePath).to(TimeUnit.DAYS) ));

		} catch (IOException err) {
		theGrid.setStateOfBoxByCell(theCell, TileSyncStates.UGLY );
		uglyTilesCnt++;
		}
	   }
	}
	theCell.setDisable(true);
	return uglyTilesCnt;
   }




// wwwwwwwwwwwwwwwwwwwwwwwwwww
//			MAINPROGRAMSECTION  2 - SPECIFIC UTILITIES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Evaluate dirindex freshness, based on its
   private static TileSyncStates evaluateStateByDirindexAge( long theDays )
    {
	agingGap = terrainFreshnessGap;
	daysCount = LocalDate.now().toEpochDay() - theDays;
	freshnessRank = 1;

	for (byte i = 4; i > 1; i--) {
	   if ( daysCount < agingGap ) {
		freshnessRank = i;
		break;
	   }
	   agingGap = agingGap << 1;
	}

	return TileSyncStates.getStateByCode((byte) (TileSyncStates.POPULATED.code + freshnessRank) );
   }


   // Parse the Freshness Tab retrieved in the sysContext config file, using default if needed : values are expressed in DAYS
   private int decodeFreshness( String theInput )
    {
	try {
	   int result = Integer.parseInt(theInput);
	   return ( result > 10 ) ? result : 10 ;

	} catch ( NumberFormatException err) {
	   return 90;
	}
   }

}
