/*
*  Written by Mimo on  26-sept-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: Provides a unified access to the various representations of the basic "TILE" object (1° x 1° square) in Screen, Geographic, and Grid
*		   coordinates. Cells are assembled as a double array  ( MATRIX_DIM x MATRIX_DIM ) in the CellGridMatrix, covering the selected REGION.
*		   Cells Coordinates both in GRID (i,j) and ESRI (x,y) are expected to be INTEGERs, while SCREEN (u,v) are RECEIVED as DOUBLE (pixel
*		   corrdinates), but internally converted to a double value best representing an integral degree (zoomed Region Dimension and  Shape
*		   Imaging Factor have been choosen to ease this conversion.
*  ---------------
*   Notes : Except for theOneScreenDegree and Region geometry (which are immutable), the other parameters are mutable, to ensure flexible reusability ...
*		   - with caution:  As this object is expected to be used only internally - input values are NOT checked for consistency against coordinate
*		   systems limits.
*		   This object may be decorated as "disabled", to provide kind of virtual disqualification flag ("don't use my current data") when needed... But
*		   this flag is completely independent of the other data and methods. Its use/interpretation is entirely left to the application.
*/

package javwep.mimo.gardeningObjects;


public class GridCell
 {
   // Private Resources
   private static final int MATRIX_DIM = AdHoc.zoomedRegionDim;	   // For short; note that MATRIX_DIM is the Region dimension in Esri degrees
   private static RectBox regionEsriGeometry;			   //  The ESRI coordinates of the selected Region - which is expected a square
   private int matrixI, matrixJ;				// Region Grid Matrix coordinates (Vertical UP): 0 <= I < MATRIX_DIM ; 0 <= J < MATRIX_DIM
   private boolean disabledState = true;

   // Private transformation factors
   private static int ESRI2GRID_HTR;		// Translation between the ESRI and the GRID domains along the horizontal axis
   private static int ESRI2GRID_VTR;		// Translation between the ESRI and the GRID domains along the horizontal axis
   private static double GRID2CNVS_HSC;		// Scaling factor between the GRID and the SCREEN domains along the horizontal axis
   private static double GRID2CNVS_VSC;		// Scaling factor between the GRID and the SCREEN domains along the vertical axis
   private static double CNVS2GRID_HSC;		// Scaling factor between the SCREEN and the GRID domains along the horizontal axis
   private static double CNVS2GRID_VSC;		// Scaling factor between the SCREEN and the GRID domains along the vertical axis


   // Public Resources



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   public GridCell( RectBox theEsriRegion, double theOneScreenDegree )
    {
	regionEsriGeometry = theEsriRegion;
	// Set Esri to Grid translations, depending on the Region square location - Reverse translation (implicit GRID2ESRI_#TR) is just -ESRI2GRID_#TR
	ESRI2GRID_HTR = (int) regionEsriGeometry.leftX();
	ESRI2GRID_VTR = (int) regionEsriGeometry.bottomY();

	// Set Grid to Canvas, and Canvas to Grid
	GRID2CNVS_HSC = theOneScreenDegree;	   // Expresses one ESRI degree in GRID dimension
	GRID2CNVS_VSC = - GRID2CNVS_HSC;	   // A negative number: GRID and SCREEN have opposite verticality
	CNVS2GRID_HSC = 1 / GRID2CNVS_HSC;
	CNVS2GRID_VSC = - CNVS2GRID_HSC;
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  1 - SERVICES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Enable...
   //
   public void setDisable( boolean theVal ){
	disabledState = theVal;
   }

   public boolean isEnabled(){
	return ! disabledState;
   }


   // Setters
   //
   public void setUsingEsriCoord( int theLeftX, int theBottomY )
    {
	matrixI = theLeftX - ESRI2GRID_HTR;
	matrixJ = theBottomY - ESRI2GRID_VTR;
   }


   public void setUsingScreenCoord( double theLeftU, double theTopV )
    {
	matrixI = (int) Math.floor(theLeftU * CNVS2GRID_HSC );
	matrixJ = (int) (MATRIX_DIM + Math.floor(theTopV * CNVS2GRID_VSC ));
   }


   public void setUsingMatrixCoord( int theMatrixI, int theMatrixJ ){
	matrixI = theMatrixI;
	matrixJ = theMatrixJ;
   }


   // Getters
   //
   public int getEsriLeftX()
    {
	return matrixI + ESRI2GRID_HTR;
   }

   public int getEsriBottomY()
    {
	return matrixJ + ESRI2GRID_VTR;
   }

   public int getMatrixI()
    {
	return matrixI;
   }

   public int getMatrixJ()
    {
	return matrixJ;
   }

   public double getScreenLeftU()
    {
	return matrixI * GRID2CNVS_HSC;
   }

   public double getScreenTopV()
    {
	return  ( matrixJ - MATRIX_DIM + 1 ) * GRID2CNVS_VSC;
   }

   public double getOneDegreeScreenDim()	   // This is an ABSOLUTE value, while GRID2CNVS scale factors are signed
    {
	return GRID2CNVS_HSC;
   }

   public int getESRI2GRID_HTR()
    {
	return ESRI2GRID_HTR;
    }

   public int getESRI2GRID_VTR()
    {
	return ESRI2GRID_VTR;
    }

   public double getGRID2CNVS_HSC()
    {
	return GRID2CNVS_HSC;
    }

   public double getGRID2CNVS_VSC()
    {
	return GRID2CNVS_VSC;
    }

   public static double getCNVS2GRID_HSC()
    {
	return CNVS2GRID_HSC;
    }

   public static double getCNVS2GRID_VSC()
    {
	return CNVS2GRID_VSC;
    }

}
