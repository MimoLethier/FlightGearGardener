/*
*  Written by Mimo on  07-Oct-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: Handling the Region Grid as a MATRIX of all the 1deg by 1deg cells being located in one given Region (not the whole world!). For each
*		   cell, it just contains its current Sync STATE (UGLY, BROKEN, UNKNOWN, POPULATED, PACKED_n, or TOCLEAR, TOLOAD, TOSYNC)
*		   This object is reusable, via INVOKE/REVOKE. It includes methods to apply some MATRIX -wide actions.
*		   NOTE that the Matrix(i, j) is Vertical UP, as ESRI(x, y) (but contrary to SCREEN(u, v) !).
*  ---------------
*   Notes :
*/


package javwep.mimo.gardeningObjects;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import static javwep.mimo.gardeningObjects.TileSyncStates.UGLY;
import static javwep.mimo.gardeningObjects.TileSyncStates.UNKNOWN;
import javwep.mimo.gardeningThread.SyncTasks;
import static javwep.mimo.gardeningThread.SyncTasks.CLEAR;
import static javwep.mimo.gardeningThread.SyncTasks.SYNCALL;
import logWriter.CleverLogger;
import toolException.ToolException;



public class GridCellMatrix
 {
   // Private Resources
   private static final int MATRIX_DIM = AdHoc.zoomedRegionDim;	   //	For short; note that MATRIX_DIM == regionEsriDim !
   private final CleverLogger logClerc;
   private final GraphicsContext gridSurface;
   private double oneDegreeScreenDim;
   private GridCell tempCell;			   // Used for local, transient parameters passing
   private final byte[][] gridMatrix;		   // Array of Tyle STATE CODEs : GridDim x GridDim
   private boolean inInitializedState;

   // Public Resources



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   public GridCellMatrix( CleverLogger theLog, Canvas theScreen, double theOneScreenDegree )
    {
	logClerc = theLog;
	gridSurface = theScreen.getGraphicsContext2D();
	oneDegreeScreenDim = theOneScreenDegree;
	gridMatrix = new byte[MATRIX_DIM][MATRIX_DIM];
	inInitializedState = false;
   }


   // INITIALIZATION  first check the given ESRI Region coordinates validity (Must be a square, residind in the ESRI domain (boudaries AND verticality UP !),
   //	   then fixes transformation parameters between Matrix view and Esri view
   //
   public void invokeOnRegion( RectBox theEsriRegion ) throws ToolException
    {
	if ( inInitializedState ) {
	   throw new ToolException("This Matrix is ALREADY initialized! Please check.");
	}
	if ( (theEsriRegion.width() != MATRIX_DIM) || (theEsriRegion.leftX() < -190) || (theEsriRegion.rightX() > 190)
		|| (theEsriRegion.height() != MATRIX_DIM) || (theEsriRegion.topY() > 90) || (theEsriRegion.bottomY() < -90)) {
	   throw new ToolException("The Region BOUNDARIES are NOT ESRI compliant!");
	}

	// All box are created  of  "Unknown" type...
	resetAll();

	// Get a private temporary GRIDCELL initialized for this Region
	tempCell = new GridCell(theEsriRegion, oneDegreeScreenDim);
	inInitializedState = true;
   }


   // Prepare for reuse - forbid further use before new initialization
   //
   public void revoke()
    {
	tempCell = null;
	inInitializedState = false;
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  1 -  Services
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Ready or not ?
   //
   public boolean isInitialized()
    {
	return inInitializedState;
   }

   // Dimension ?
   //
   public int getGridDimension()
    {
	return MATRIX_DIM;
   }


   // Returns the current STATE of a box given as a GridCell - Note that this one DOES NOT start with coherency checks - the cell, if enabled, is expected valid
   //
   public TileSyncStates getBoxStateByCell( GridCell theCell )
    {
	if ( theCell.isEnabled() ) {
	   return TileSyncStates.getStateByCode(gridMatrix[theCell.getMatrixI()][theCell.getMatrixJ()]);
	}
	return UGLY;
   }


   // Set a box to specific State for a given GridCell
   //
   public void setStateOfBoxByCell( GridCell theCell, TileSyncStates theState )
    {
	gridMatrix[theCell.getMatrixI()][theCell.getMatrixJ()] = theState.code;
   }


   // Returns the current STATE of a box given by the GRID X:Y indexes
   //
   public TileSyncStates getBoxStateByIndex( int theX, int theY )
    {
	if ( theX < 0 || theX >= MATRIX_DIM || theY < 0 || theY >= MATRIX_DIM ) { return UGLY; }

	return TileSyncStates.getStateByCode(gridMatrix[theX][theY]);
   }


   // Set a box to specific State
   //
   public void setStatusOfBoxAtIndex( int indexX, int indexY, TileSyncStates theState )
    {
	gridMatrix[indexX][indexY] = theState.code;
   }


   // Toggle the STATE for a box  given  as a GRIDCELL
   //
   public void toggleStateOfCell( GridCell theCell, TileSyncStates theState ) throws ToolException
    {
	int indexI = theCell.getMatrixI();
	int indexJ = theCell.getMatrixJ();

	if ( gridMatrix[indexI][indexJ] == theState.code ) {
	   TileSpray.decorateCellForState(theCell, UNKNOWN, gridSurface);
	   gridMatrix[indexI][indexJ] = UNKNOWN.code;
	} else {
	   TileSpray.decorateCellForState(theCell, theState, gridSurface);
	   gridMatrix[indexI][indexJ] = theState.code;
	}
   }


   // Paint all the cells depending on their current State...
   //
   public void renderAllCells()
    {
	tempCell.setDisable(false);
	for (int i = 0; i < MATRIX_DIM; i++) {
	   for (int j = 0; j < MATRIX_DIM; j++) {

		tempCell.setUsingMatrixCoord(i, j);
		TileSpray.paintCellForState(tempCell, getBoxStateByCell(tempCell), gridSurface);
	   }
	}
	tempCell.setDisable(true);
   }


   // Write current selection targets to Log - TO REVIEW !!!
   //
   public void logSelectionTarget( SyncTasks theTask )
    {
	logClerc.add("");
	logClerc.add("Current Selection Target: Action is " + theTask.name() + "  ; Target is/are:");
	logClerc.add("");
	tempCell.setDisable(false);
	for (int i = 0; i < MATRIX_DIM; i++) {
	   for (int j = 0; j < MATRIX_DIM; j++) {
		switch ( theTask ) {
		   case CLEAR : {
			if ( gridMatrix[i][j] != TileSyncStates.TOCLEAR.code ) { continue; }
			break;
		   }
		   case SYNCALL : {
			if ( ! ( gridMatrix[i][j] == TileSyncStates.TOSYNC.code || gridMatrix[i][j] == TileSyncStates.TOLOAD.code ) ) { continue; }
			break;
		   }
		   default : continue;
		}
		tempCell.setUsingMatrixCoord(i, j);
		logClerc.add("   Tile at Coord " + tempCell.getEsriLeftX()+ "°, " + tempCell.getEsriBottomY()
					   + "°, with Terrasync Folder at: " + TileNames.getHectoTileNameForCell(tempCell)
												+ " / " +TileNames.getTileNameForCell(tempCell));
	   }
	}
	tempCell.setDisable(true);
   }


   // Reset the whole matrix cells to State UNKNOWN
   //
   public void resetAll()
    {
	// All box are returned to the "Unknown" type...
	for (int i = 0; i < MATRIX_DIM; i++) {
	   for (int j = 0; j < MATRIX_DIM; j++) {
		gridMatrix[i][j] = UNKNOWN.code;	   // All box is created Unknown...
	   }
	}
   }




// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  2 - Specific Utilities
// wwwwwwwwwwwwwwwwwwwwwwwwwww

}

