/*
*  Written by Mimo on 31-août-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: POLYSTROKE Object definition. Assist in reading Points making Parts in Shapes. May be closed (polygons) or not (polylines)
*		   Polystroke is a re-usable object, and contains ALL the "Parts" making off a Shape ! May be loaded by reading one single "Shape"
*		   requested by its number, or reading a stream of shapes (walking the .shp file) - the two accesses  types CANNOT be run concurrently !
*  ---------------
*    Notes :   Closed PolyStrokes do NEVER contain a second copy of the first Point as last Point  (contrary to ESRIN files!), but must be drawn using the
*		"polygon" primitive, which automatically clases the path. Shapes are identified by a sequential "Record Number" starting from 1.
*		As usual the internal "Index" starts from 0... so Index = Number - 1 !
*/

package javwep.mimo.gardenDrawer;



import javwep.mimo.gardeningObjects.AdHoc;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import toolException.ToolException;


public class PolyStroke
 {
   // Private Resources
   private final BagReader bag;
   private boolean singleLoadedState, polygonState;
   private int rangeLast, shapeIdx;	// Index =  the Shape number minus one !
   private int lastLoadedShape;		// -1 = NO Stream Load in progress ; 0 = Stream has been opened, not used ; 0 < n <= rangeLast = active
   private final ByteBuffer streamBucket;
   private ByteBuffer singleBucket;
   private int partsCount, allPointsCount, pointsStartPos;

   // Public Resources



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   public PolyStroke( BagReader theBag )
    {
	bag = theBag;
	polygonState = theBag.isTypeOfPolyGon();
	streamBucket = ByteBuffer.allocate(bag.getBagMaxShapeSize());
	singleLoadedState = false;
	lastLoadedShape = -1;
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//			MAINPROGRAMSECTION  1 -  BUSINESS LOGIC
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Returns its master BAG
   //
   public BagReader getSourceBag()
    {
	return bag;
   }


   // Load a fresh PolySTroke object with data from a SINGLE given SHAPE (This happens on another file connection than streaming, using another BBuffer !)
   //
   public void loadSingleShape( int theShape ) throws ToolException
    {
	// Meaningfull ?
	if ( singleLoadedState || ( lastLoadedShape > -1 ) ) {
	   throw new ToolException("This POLYSTROKE is already ALLOCATED. Please disallocate it before reuse.");
	}
	if ( ! bag.bagISLoaded()) {
	   throw new ToolException("The BAG is not LOADED yet. Please correct.");
	}
	if ( ! bag.isTypeOfPolystroke() ) {
	   throw new ToolException("The BAG is not loaded with POLYSTROKE shapes. Please correct.");
	}

	// Assign this to a specific Shape in a Bag...
	shapeIdx = theShape - 1;

	// Read the Bag .shp file for relative Point record
	singleBucket = ByteBuffer.allocate(bag.getShapeSize(shapeIdx));
	bag.loadSinglePolyStroke(singleBucket, shapeIdx);
	singleBucket.order(ByteOrder.LITTLE_ENDIAN);

	// Check coherency (Unique TYPE for ALL shapes)
	if ( singleBucket.getInt(0) != bag.getBagType().getCodeValue() ) {
	   throw new ToolException("This Shape TYPE is NOT the BAG TYPE. Please investigate.");
	}
	partsCount = singleBucket.getInt(AdHoc.partCountPos);
	allPointsCount = singleBucket.getInt(AdHoc.pointCountPos);
	pointsStartPos = AdHoc.pointCountPos + 4 + (4 * partsCount);

	singleLoadedState = true;
	lastLoadedShape = theShape;
   }

   // Disallocate the current Polystroke IFF it is allocated to a SINGLE shape
   //
   public void disallocateSingleShape()
    {
	if ( ! singleLoadedState ) { return; }
	singleBucket = null;
	singleLoadedState = false;
	lastLoadedShape = -1;
   }


   // Prepare for Streaming a sequence of fresh PolySTroke objects with data from a RANGE of SHAPE;
   //	   Stream none if theLast < theFirst or theLast >  Bag.indexCeiling
   //
   public void openShapesRangeStream( int theFirst, int theLast ) throws ToolException
    {
	// Meaningfull request?
	if ( ! bag.bagISLoaded()) {
	   throw new ToolException("The BAG is not LOADED yet.");
	}
	if ( ! bag.isTypeOfPolystroke() ) {
	   throw new ToolException("The BAG is not loaded with POLYSTROKE shapes.");
	}
	if ( singleLoadedState || ( lastLoadedShape > -1 ) ) {
	   throw new ToolException("This POLYSTROKE is SINGLE ALLOCATED. Please disallocate it before reuse.");
	}
	if ( ( theFirst < 1 ) || ( theFirst > bag.getMaxShapeRecordNumber() ) ) {
	   throw new ToolException("FIRST shape index (" + theFirst + ") OUT OF RANGE.");
	}
	if ( ( theLast < theFirst ) || ( theLast > bag.getMaxShapeRecordNumber() ) ) {
	   throw new ToolException("LAST shape index (" + theLast + ") OUT OF RANGE.");
	}

	// Assign the Streaming Range...
	shapeIdx = theFirst - 1;
	rangeLast = theLast -1;

	// Open the READ STREAM, if possible
	try {
	   bag.openStreamChannel();
	   lastLoadedShape = 0;

	} catch (ToolException err) {
	   rangeLast = shapeIdx - 1;
	   throw new ToolException(err.getMessage());
	}
   }

   // HASNEXT returns more Shapes ahead...
   //
   public boolean hasNext()
    {
	return ( !singleLoadedState ) && ( lastLoadedShape > -1 ) && ( shapeIdx <= rangeLast );
   }

   // Get next Shape Polystroke data
   //
   public void loadNextShape() throws ToolException
    {
	if ( singleLoadedState || (lastLoadedShape < 0) ) {
	   throw new ToolException("There is currently NO OPEN STREAM yet. Please investigate.");
	}

	streamBucket.clear();
	try {
	   bag.loadNextPolyStroke(streamBucket, shapeIdx);

	} catch (ToolException ex) {
	   bag.closeStreamChannel();
	   throw new ToolException("Stream interrupted: " + ex.getMessage());
	}
	streamBucket.order(ByteOrder.LITTLE_ENDIAN);

	// Check coherency (Same TYPE for ALL shapes)
	if ( streamBucket.getInt(0) != bag.getBagType().getCodeValue() ) {
	   throw new ToolException("This Shape TYPE is NOT the BAG TYPE. Please investigate.");
	}
	partsCount = streamBucket.getInt(AdHoc.partCountPos);
	allPointsCount = streamBucket.getInt(AdHoc.pointCountPos);
	pointsStartPos = AdHoc.pointCountPos + 4 + (4 * partsCount);

	// Go for "next" shape
	shapeIdx++;
	lastLoadedShape = shapeIdx;	   // Shape Number is one more than shapeIdx !
   }

   public void closeShapesRangeStream()
    {
	lastLoadedShape = -1;
	rangeLast = -1;
	streamBucket.clear();	// This forbids reusing the Bucket content thereafter !
	bag.closeStreamChannel();
   }


   // Get POLYGON... or not
   //
   public boolean typeIsPolygon()
    {
	return polygonState;
   }

   // Returns the CURRENT Shape NUMBER
   //
   public int getShapeNumber()
    {
	return lastLoadedShape;
   }

   // Get the count of Parts
   //
   public int getPartsCount()
    {
	return partsCount;
   }

   // Get the count of Points (all parts)
   //
   public int getAllPointsCount()
    {
	return allPointsCount;
   }

   // Get count of points of a part
   //
   public int getPartPointsCount( int thePart )
    {
	return getPartSize(thePart);
   }


   // Get  the Coordinates of points for Part n. Note this sequence NEVER includes the duplicated lastPoint = firstPoint in case of POLYGON !
   //
   public double[][] loadCoordsOfPart( int theShape, int thePart ) throws ToolException
    {
	if ( theShape != lastLoadedShape ) {
	   throw new ToolException("Invalid SHAPE number. Please correct.");
	}
	if ( thePart > partsCount || thePart < 1 ) {
	   throw new ToolException("Invalid PART number. Please correct.");
	}

	try {
	   int partSize = ( polygonState ) ? getPartSize(thePart) - 1 : getPartSize(thePart);
	   double[][] tempCoords = new double[2][partSize];

	   streamBucket.position(getPartStartOffset(thePart));
	   for (int i = 0; i < partSize; i++) {
		tempCoords[0][i] = streamBucket.getDouble();
		tempCoords[1][i] = streamBucket.getDouble();
	   }
	   return tempCoords;

	} catch (Exception ex) {
	   throw new ToolException("Iteration Error: " + ex.getMessage());
	}
   }




// wwwwwwwwwwwwwwwwwwwwwwwwwww
//			MAINPROGRAMSECTION  2 - SPECIFIC UTILITIES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Get the FIRST position for the n'th part (starting from  ONE )... in a row of 16 bytes (two doubles !) blocks
   private int getPartStartOffset(int thePart)
    {
	return ( pointsStartPos + ( streamBucket.getInt(AdHoc.pointCountPos + (4*thePart)) ) * 16 );
   }

   // Get the size of the n'th part (n starting from ONE), expressed in blocks of 16 bytes
   private int getPartSize(int thePart)
    {
	if ( partsCount == 1 ) {
	   return allPointsCount;
	}
	else if ( thePart == partsCount ) {
	   return allPointsCount - streamBucket.getInt(AdHoc.pointCountPos + (4*thePart));
	}
	return streamBucket.getInt(AdHoc.pointCountPos + (4*(thePart + 1))) - streamBucket.getInt(AdHoc.pointCountPos + (4*thePart));
   }

}
