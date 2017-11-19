/*
*  Created by Mimo on 16-11-2017
*
*  CONTEXT :	Implementation of a basic downloader for Flightgear Terrasync data.
*  PURPOSE :	Managing the handling of graphical representations of geogrphic "SHAPES"
*  ROLE :	Handling the rendering of the background shapes - Will take care of presenting a "focusable" high-level Earth map until a spot is
*		   selected as "interrest center" by the user... The Sub region is then projected on the Download TAB, as true background image !
*  ---------------
*   Notes :
*/


package javwep.mimo.gardenDrawer;


import appBasicToolKit.AppGroundKit;
import appContextLoader.AppCtxtManager;
import javwep.mimo.gardeningObjects.AdHoc;
import javwep.mimo.gardeningObjects.RectBox;
import java.nio.file.Path;
import java.nio.file.Paths;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import logWriter.CleverLogger;
import toolException.ToolException;


public class DrawManager
 {

   // Private Resources
   private static AppGroundKit groundClerc;
   private static AppCtxtManager configClerc;
   private final CleverLogger logClerc;
   private final BagReader bagClerc;
   private PolyStroke strokeClerc;


   private String backgroundShapesName;
   private RectBox earthScreenGeometry;	//  the dimensions of the Selecting TAB main Canvases, where the full  EARTH MAP is shown
   private RectBox regionScreenGeometry;	// the dimensions of the Downloading TAB main Canvases, where the selected REGION MAP is shown
   private RectBox imagingScreenGeometry;	//  the dimensions of the "abstract" imaging Canvas, where the image of the Map is drawn
   private RectBox earthEsriGeometry;		//  the dimensions of the original ESRI Map BOUNDS
   private boolean globalMapOnDisplay;
   private int regionEsriDim, regionEsriMidDim; // the RegionSquare side dimension expressed in the ESRI context ( usual degrees ); Half this value

   // GUI Resources
   private final Canvas earthCANVAS, focusCANVAS, regionCANVAS;
   private GraphicsContext earthLayerBrush, focusLayerBrush, regionLayerBrush;
   private ImagingCanvas imagingBoard;

   private double esri2ScreenXScale, esri2ScreenYScale;	   // Scaling factors between the  ESRI and the SELECTION domains
   private double grid2EsriScale, esri2RegionScale;	   // Scaling factors between the ESRI and the REGION domains
   private double esri2imageXScale, esri2imageYScale;	   // Transform from ESRI to Imaging
   private double screen2imageXScale, screen2imageYScale;  // Transform from SCREEN to Imaging

   // Public Resources
   private RectBox focusSqEsriGeometry;
   private RectBox focusSqScreenGeometry;



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   public DrawManager( AppGroundKit theKit, Canvas theEarth, Canvas theFocus, Canvas theRegion )
    {
	groundClerc = theKit;
	configClerc = theKit.ctxtClerc;
	logClerc = theKit.logClerc;
	earthCANVAS = theEarth;
	focusCANVAS = theFocus;
	regionCANVAS = theRegion;
	bagClerc = new BagReader(groundClerc);

	globalMapOnDisplay = false;
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  1 -  BUSINESS LOGIC
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Initialization : get access to the background focalization Map...
   public void drawTheEarthMap() throws ToolException {

	try {
	   // Get (local) Path to this BAG  .shp shapefile and "Connect"...
	   Path shapesFolderPath = Paths.get(configClerc.get("BackgroundMap_Location", ""));
	   backgroundShapesName = configClerc.get("Shapefile_Name", "");

	   bagClerc.connect(shapesFolderPath, backgroundShapesName);

	   // Get access to the  BACKGROUND and FOREGROUND boards on the SelectingTAB, and BACKGROUND Region image on the Downloading TAB
	   earthCANVAS.toFront();
	   earthLayerBrush = earthCANVAS.getGraphicsContext2D();
	   focusLayerBrush = focusCANVAS.getGraphicsContext2D();
	   focusLayerBrush.setStroke(AdHoc.focusBoxColor);
	   regionLayerBrush = regionCANVAS.getGraphicsContext2D();

	   // Prepare the SelectingTAB geometry to support projections from screen to earth, etc... and apply final coherency validations
	   prepareGroundGeometries();

	   // Get Access to StrokeClerc
	   strokeClerc = bagClerc.getPolystroke();

	} catch (ToolException err) {
	   throw new ToolException("Start Background Error: " + err.getMessage());
	}

	// Set the IMAGING board (Never visible; it's the Canvas on which the Map will be assembled and transformed into a reusable IMAGE)
	imagingBoard.reset();

	// Try Drawing all the Shapes...
	try {
	   strokeClerc.openShapesRangeStream(1, bagClerc.getMaxShapeRecordNumber());
	   while ( strokeClerc.hasNext() ) {
		strokeClerc.loadNextShape();
		// Draw it
		renderShapeImage( strokeClerc );
	   }
	   strokeClerc.closeShapesRangeStream();

	   // ... and show it
	   showMapImage();
	   drawCross();

	} catch (ToolException err) {
	   throw new ToolException("Error during Content handling: " + err.getMessage());
	}

	logIt("   >>> Selection Handler started; Shape Bag Rendered.");
	globalMapOnDisplay = true;
   }


   //  Compute and Draw the Focus Box on Focus layer
   //
   public boolean setFocusRegionAt( double theFocusX, double theFocusY, Label theLine )
    {
	if ( ! globalMapOnDisplay ) { return false; 	}

	try {
	   drawFocusBox(theFocusX, theFocusY);
	   theLine.setText("Regional focus selected, centered at " + getEsriFocusRegionCenterLonLat());
	   return true;

	} catch (ToolException ex) {
	   theLine.setText("Unable to SET the Regional focus.");
	   return false;
	}
   }


   // Returns an handle to the Region Square Geometry in ESRI degrees
   //
   public RectBox getRegionSqEsriGeom()
    {
	return focusSqEsriGeometry;
   }


   // Project the SELECTED REGION image onto the regionBoard- In principle this one is called ONLY after focus has been set, and Button enabled
   //
   public void projectSelectedRegion() throws ToolException
    {
	try {
	   projectFocusBoxOnRegionBoard();

	} catch (ToolException err) {
	   throw new ToolException("Error during REGION projection: " + err.getMessage());
	}
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//				@MainSection  2 - SPECIFIC UTILITIES code
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Preparing and validationg the Ground -
   private void prepareGroundGeometries() throws ToolException
    {
	// Set Earth brush Geometry and Region brush Geometry ( ! Screen geometries always anchored at LeftX:TopY, and Vertical DOWN ! ).
	earthScreenGeometry = new RectBox(0, earthCANVAS.getHeight(), earthCANVAS.getWidth(), - earthCANVAS.getHeight());
	regionScreenGeometry = new RectBox(0, regionCANVAS.getHeight(), regionCANVAS.getWidth(), - regionCANVAS.getHeight());

	// Check for USABILITY: too small graphics or null dimensions... (Note that Canvases dimensions and exact superposition must have been
	//	checked in advance by the calling MMI application !!!)
	// Check the Earth ESRI geometry usability
	earthEsriGeometry = new RectBox( AdHoc.standardESRI[0], AdHoc.standardESRI[1],
								AdHoc.standardESRI[2] - AdHoc.standardESRI[0],
										AdHoc.standardESRI[3] - AdHoc.standardESRI[1]);
	if (  earthEsriGeometry.absWidth() <= AdHoc.minUsableDimension ||  earthEsriGeometry.absHeight() <= AdHoc.minUsableDimension ) {
	   logIt("<!> Starting DRAWER: Found UNUSABLE dimension in ESRI Geometry:");
	   logIt("             Width: " + earthEsriGeometry.width() + " ; Height: " + earthEsriGeometry.height());
	   throw new ToolException("Starting DRAWER: Found UNUSABLE dimension in ESRI Geometry. See Log.");
	}

	// Check  REGION ESRI dimension usability (a square, too, expressed in degrees)
	regionEsriMidDim = AdHoc.zoomedRegionMidDim;	// A pure, positive integer
	regionEsriDim = regionEsriMidDim * 2;
	if ( regionEsriDim < (AdHoc.minUsableDimension / 3.0) ) {
	   logIt("<!> Starting DRAWER: Found UNUSABLE Zoomed Region dimension: " + regionEsriDim);
	   throw new ToolException("Starting DRAWER: Found UNUSABLE Zoomed Region dimension. See Log.");
	}

	// 4. Check IMAGING Board scaling (against  MAP layer) : must be at least 4, to ensure fair image scaling
	if ( AdHoc.imagingFactor < 4 ) {
	   logIt("<!> Starting DRAWER: Found UNUSABLE Image SCALING: " + AdHoc.imagingFactor);
	   throw new ToolException("Starting DRAWER: Found UNUSABLE Image SCALING factor. See Log.");
	}
	// Create the IMAGING Canvas, Geometry and transformation factors  ! Imaging vertical is DOWN, but XScale = YScale.
	esri2imageXScale = AdHoc.imagingFactor;
	esri2imageYScale = - AdHoc.imagingFactor;   // Negative, since Vertical ESRI is UP, but Vertical Imaging is DOWN
	imagingScreenGeometry = new RectBox(0, 0 - (earthEsriGeometry.height() * esri2imageYScale),
					earthEsriGeometry.width() * esri2imageXScale, earthEsriGeometry.height() * esri2imageYScale);
	imagingBoard = new ImagingCanvas(imagingScreenGeometry.absWidth(), imagingScreenGeometry.absHeight());
	screen2imageXScale = imagingScreenGeometry.width() / earthScreenGeometry.width();
	screen2imageYScale = imagingScreenGeometry.height() / earthScreenGeometry.height();

	// 5. Fix a few transformations parameters to express ESRI, Imaging and Region coordinates into each other.
	esri2ScreenXScale = earthScreenGeometry.width() / earthEsriGeometry.width();
	esri2ScreenYScale = earthScreenGeometry.height() / earthEsriGeometry.height(); // Note that this is NEGATIVE (ESRI is vertical UP, MAP is vertical DOWN)
	esri2RegionScale = (regionScreenGeometry.width() + 1) / regionEsriDim;  // Same for width and height: express one ESRI degree in GRID dimension

	// 6. Prepare the "default" geometries of the Selection Square in the ESRI and the Earth contexts, fixing ONLY width and height for now;
	//	position (X,Y) will be NEED to be updated later, after Region selection.
	focusSqEsriGeometry = new RectBox(0, 0, regionEsriDim, regionEsriDim);
	focusSqScreenGeometry = new RectBox(0, 0, regionEsriDim * esri2ScreenXScale, regionEsriDim * esri2ScreenYScale);
   }


   // Show the WHOLE EARTH MAP (rendered on the ImagingBoard) on the earthBoard
   public void showMapImage() throws ToolException
    {
	try {
	   if ( imagingBoard.shapshotISTaken() ) {
		earthCANVAS.toFront();
		imagingBoard.paintImagePart(imagingScreenGeometry, earthLayerBrush);
	   } else {
		throw new ToolException("Unable to take the Image SNAPSHOT.");
	   }

	} catch (ToolException ex) {
	   throw new ToolException("IMAGE paint Error: " + ex.getMessage());
	}
   }


   // Draw a grey cross through board middle
   //
   public void drawCross()
    {
	double midX = earthScreenGeometry.width() / 2;
	double midY = - earthScreenGeometry.height() / 2;
	earthLayerBrush.setStroke(AdHoc.selectingCrossColor);
	earthLayerBrush.strokeLine(midX, earthScreenGeometry.topY(), midX, earthScreenGeometry.bottomY());
	earthLayerBrush.strokeLine(earthScreenGeometry.leftX(), midY, earthScreenGeometry.rightX(), midY);
   }


   // Draw a Polystroke entire content (all parts) as Polylines or Polygons on the Imaging Board
   private void renderShapeImage( PolyStroke theShapeStrokes ) throws ToolException
    {
	int shapeNbr = theShapeStrokes.getShapeNumber();
	int partsCnt = theShapeStrokes.getPartsCount();

	try {
	   for (int i = 1; i <= partsCnt; i++) {

		if ( theShapeStrokes.typeIsPolygon() ) {
		   imagingBoard.writeShapePoints( getImagePointsRow( theShapeStrokes.loadCoordsOfPart(shapeNbr, i)), true);
		} else {
		   imagingBoard.writeShapePoints( getImagePointsRow( theShapeStrokes.loadCoordsOfPart(shapeNbr, i)), false);
		}
	   }

	} catch (ToolException ex) {
	   throw new ToolException("DRAW ERROR: " + ex.getMessage());
	}
   }


   // Applies Transformation from ESRI coordinates to Image Coordinates, to a row of points
   private double[][] getImagePointsRow( double[][] theRows)
    {
	double leftX = earthEsriGeometry.leftX();
	double topY = earthEsriGeometry.topY();

	// Row 1,i = ( row(1,i) - bottomY + height ) * Yscale

	for (int i = 0; i < theRows[0].length; i++) {
	   theRows[0][i] = ( theRows[0][i] - leftX ) * esri2imageXScale;
	   theRows[1][i] = ( theRows[1][i] - topY ) * esri2imageYScale;
	}
	return theRows;
   }


   // Draw the FocusBox on the Focus canvas (in overlay). The focus-center coordinates are expressed in the Earth Screen coordinates
   //	    Note that as the FocusBox is a square its width and height are identical (in ESRI coordinates !)... And the selected Focus point is at its center
   //	   ... but, as the Region Grid represents a fixed graduation in integral degrees, the FocusBox must be aligned on full degrees too
   //
   private void drawFocusBox( double theFocusX, double theFocusY ) throws ToolException
    {
	double halfWidth = focusSqScreenGeometry.absWidth() / 2;
	double halfHeight = focusSqScreenGeometry.absHeight() / 2;
	double leftX, leftXDeg, bottomY, bottomYDeg, roundedX, roundedY;

	// Clear the focus Board (which overlays the Earth map Board)
	focusLayerBrush.clearRect(earthScreenGeometry.leftX(), earthScreenGeometry.topY(),
								earthScreenGeometry.width(), - earthScreenGeometry.height());

	// Round the focus center to the closest point representing integral degrees coordinates in the ESRI domain
	roundedX = esri2ScreenXScale * Math.rint( theFocusX / esri2ScreenXScale );
	roundedY = esri2ScreenYScale * Math.rint( theFocusY / esri2ScreenYScale );

	// Draw the square Box... First  compute the Zoom Region  leftX and topY to avoid clipping ; do this in the global EARTH MAP context
	if ( (roundedX - halfWidth) <= earthScreenGeometry.leftX() ) {   // i.e. <= 0.0 in this case...
	   leftX = earthScreenGeometry.leftX();
	   leftXDeg = earthEsriGeometry.leftX();
	} else if ( roundedX + halfWidth >= earthScreenGeometry.rightX() ) {
	   leftX = earthScreenGeometry.rightX() - focusSqScreenGeometry.absWidth();
	   leftXDeg = earthEsriGeometry.rightX() - regionEsriDim;
	} else {
	   leftX = roundedX - halfWidth;
	   leftXDeg = Math.floor( earthEsriGeometry.leftX() + ( leftX / esri2ScreenXScale) );
	}

	if ( roundedY + halfHeight >= earthScreenGeometry.bottomY() ) {
	   bottomY = earthScreenGeometry.bottomY();
	   bottomYDeg = earthEsriGeometry.bottomY();
	} else if ( roundedY - halfHeight <= earthScreenGeometry.topY() ) {   // i.e. <= 0.0 in this case...
	   bottomY = earthScreenGeometry.topY() + focusSqScreenGeometry.absHeight();
	   bottomYDeg = earthEsriGeometry.topY() - regionEsriDim;
	} else {
	   bottomY =  roundedY + halfHeight;
	   bottomYDeg = Math.floor(earthEsriGeometry.topY() + ( bottomY / esri2ScreenYScale) );
	}

	// Move the two selected Region geometries to the selected spot
	focusSqScreenGeometry.moveAt(leftX, bottomY);
	focusSqEsriGeometry.moveAt(leftXDeg, bottomYDeg);

	// Plot the "Square" Focus region
	focusLayerBrush.strokeRect(focusSqScreenGeometry.leftX(), focusSqScreenGeometry.topY(),
							   focusSqScreenGeometry.width(), - focusSqScreenGeometry.height());
	focusCANVAS.toFront();
   }


   // Returns a String expressing the Coordinates of the Center of the Focus Region in ESRI coordinates (Checked earlier to be well inside the ESRI Earth Geom
   private String getEsriFocusRegionCenterLonLat()
    {
	double centerX = focusSqEsriGeometry.leftX() + regionEsriMidDim;
	double centerY = focusSqEsriGeometry.bottomY() + regionEsriMidDim;
	StringBuilder tmpStr = new StringBuilder(" ");

	if ( centerX < 0 ) {
	   tmpStr.append((int) (- centerX));
	   tmpStr.append("° W, ");
	} else {
	   tmpStr.append((int) centerX);
	   tmpStr.append("° E, ");
	}
	if ( centerY < 0 ) {
	   tmpStr.append((int) (- centerY));
	   tmpStr.append("° S");
	} else {
	   tmpStr.append((int) centerY);
	   tmpStr.append("° N");
	}
	return tmpStr.toString();
   }

   // Project the selected REGION on the RegionBoard in Download TAB
   public void projectFocusBoxOnRegionBoard() throws ToolException
    {
	double coord;

	try {
	   // First, express the Region Screen Geometry in Imaging SCREEN coordinates (just scaling):
	   RectBox focusBoxImagingGeometry = new RectBox( focusSqScreenGeometry.leftX() * screen2imageXScale,
										focusSqScreenGeometry.bottomY() * screen2imageYScale,
										   focusSqScreenGeometry.width() * screen2imageXScale,
											focusSqScreenGeometry.height() * screen2imageYScale);

	   // Get a snapshot and paint the selected square to the RegionBoard
	   if ( imagingBoard.shapshotISTaken() ) {
		regionCANVAS.toFront();
		imagingBoard.paintImagePart(focusBoxImagingGeometry, regionLayerBrush);
	   } else {
		throw new ToolException("Unable to take the Image SNAPSHOT.");
	   }

	   // Draw the basic GRID on top of the Image region
	   regionLayerBrush.setStroke(AdHoc.downloadGridColor);
	   for (int i = 0; i < regionEsriDim; i++) {
		coord =  i * esri2RegionScale;
		regionLayerBrush.strokeLine(coord, 0, coord, regionScreenGeometry.bottomY());
		regionLayerBrush.strokeLine(0, coord, regionScreenGeometry.rightX(), coord);
	   }
	   // Draw central cross
	   regionLayerBrush.setStroke(AdHoc.downloadCrossColor);
	   coord =  regionEsriMidDim * esri2RegionScale;
	   regionLayerBrush.strokeLine(coord, 0, coord, regionScreenGeometry.bottomY());
	   regionLayerBrush.strokeLine(0, coord, regionScreenGeometry.rightX(), coord);

	} catch (ToolException ex) {
	   throw new ToolException("REGION projection Error: " + ex.getMessage());
	}
	logIt("");
	logIt("   >>> Selected Region ( LeftTop corner at " + focusSqEsriGeometry.leftX() + " ; "
										+ focusSqEsriGeometry.topY() + " ) has been projected.");
	logFlush();
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

}
