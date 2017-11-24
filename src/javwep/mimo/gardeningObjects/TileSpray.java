/*
*  Written by Mimo on 12-nov.-2017
*
* CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: Tool used to decorate Tiles with color patches indicating selection states,...
*  ---------------
*    Notes :
*/

package javwep.mimo.gardeningObjects;


import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;


public class TileSpray
 {
   // Private Resources
   private static double cellDimension;

   // Public Resources




// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   public TileSpray( double theDim )
    {
	cellDimension = theDim;
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  0 -  Services
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Decorate a tile given by GridCell to reflect a given State  : "Decorate" paint only the center of the box !
   //
   public static void decorateCellForState( GridCell theCell, TileSyncStates theState, GraphicsContext theSurface )
    {
	switch ( theState ) {
	   case TOLOAD : {
		theSurface.setFill( AdHoc.toLoadFillColor );
		break;
	   }
	   case TOSYNC : {
		theSurface.setFill( AdHoc.selectedFillColor );
		break;
	   }
	   case TOCLEAR : {
		theSurface.setFill( AdHoc.toClearFillColor );
		break;
	   }
	   default : {
		theSurface.clearRect(theCell.getScreenLeftU(), theCell.getScreenTopV(), cellDimension, cellDimension);
		return;
	   }
	}
	theSurface.fillRect(theCell.getScreenLeftU() + 2, theCell.getScreenTopV() + 2, cellDimension - 4, cellDimension - 4);
   }


   // Paint the whole tile given by GridCell to reflect a given State
   //
   public static void paintCellForState( GridCell theCell, TileSyncStates theState, GraphicsContext theSurface )
    {
	switch ( theState ) {
	   case BROKEN : {
		theSurface.setFill( Color.DARKORANGE );
		break;
	   }
	   case POPULATED : {
		theSurface.setFill( AdHoc.NODIRINDEX_CELL );
		break;
	   }
	   case PACKED_1 : {
		theSurface.setFill( AdHoc.OUTOFSIGHT_CELL );
		break;
	   }
	   case PACKED_2 : {
		theSurface.setFill( AdHoc.QUITEOLDER_CELL );
		break;
	   }
	   case PACKED_3 : {
		theSurface.setFill( AdHoc.ABITOLDER_CELL );
		break;
	   }
	   case PACKED_4 : {
		theSurface.setFill( AdHoc.QUITECRISP_CELL);
		break;
	   }
	   case UGLY : {
		theSurface.setFill( Color.RED);
		break;
	   }
	   default : {
		theSurface.setFill( Color.TRANSPARENT );
	   }
	}
	theSurface.fillRect(theCell.getScreenLeftU(), theCell.getScreenTopV(), cellDimension, cellDimension);
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//			MAINPROGRAMSECTION  2 - SPECIFIC UTILITIES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

}
