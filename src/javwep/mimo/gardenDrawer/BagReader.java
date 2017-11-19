/*
*  Written by Mimo on 28-août-2017
*
*  CONTEXT 	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: Create a central point to connect to, and read data from, Shapefiles
*  ---------------
*    Notes : ShapeFiles are structured as tables of indexed records: index is just an natural INT, starting from 0. Each Shapefile in the same Bag
*	   presents another "aspect" of the data concerning a specific geographic region, arranged as digital "vectors" (not raster images !) : See
*	   ESRI "Shapefiles Technical Description"  for details. The main files are .shp, containing geometric info related to a specific Coordinates
*	   Reference System (CRS), .dbf  containing features described in DBase iV format, .shx containing index and offset, etc...
*	   All files in the same bag have the same name (not considering the file extention !)
*	   Shapes Offset and Content length are twice the stored values, expressing  8bit Bytes values, not 16bit Word values!
*	   Parts of this work is adapted from insightful Thomas Diewald ShapeFileReader. Please visit  http://thomasdiewald.com/blog
*/

package javwep.mimo.gardenDrawer;


import appBasicToolKit.AppGroundKit;
import appContextLoader.AppCtxtManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import javwep.mimo.gardeningObjects.AdHoc;
import logWriter.CleverLogger;
import toolException.ToolException;


public class BagReader
 {
   // Private Resources
   // Generic
   private static AppCtxtManager configClerc;
   private final CleverLogger logClerc;

   // Specific objects
   private Path shapeBagLocation;
   private String shapeBagName;
   private SeekableByteChannel gisSeekStream = null, shapeFileStream = null;
   private ByteBuffer headerBucket, read4KBucket, tmpBucket;
   private int maxShapeRecordNumber, recordIdx, maxShapeSize, readLen;
   private int[] shapesOffsetIndex, shapesLengthIndex;
   private ShapeTypes bagShapeType;
   private PolyStroke bagPolystroke;

   // Flags
   private boolean bagConnectedState, streamOpenState, mbrIndexLoaded, polystrokeAllocated;
   private static final StandardOpenOption vAccessRead = StandardOpenOption.READ;

   // Public Resources


// wwwwwwwwwwwwwwwwwwwwwwwwwww
//			MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   public BagReader ( AppGroundKit theContext )
    {
	configClerc = theContext.ctxtClerc;
	logClerc = theContext.logClerc;
	headerBucket = ByteBuffer.allocate(AdHoc.SHPHeaderSize);
	read4KBucket = ByteBuffer.allocate(AdHoc.READ4KBucketSize);
	maxShapeSize = -1;			// The size in Bytes of the longest Shape Record in the .shp file (-1 is just an init value)
	bagConnectedState = false;
	streamOpenState = false;
	mbrIndexLoaded = false;
	polystrokeAllocated = false;
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//			MAINPROGRAMSECTION  1 -  BUSINESS LOGIC
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // POPULATE a new BAG with initial actual Data from shapeFiles: shapes Type, number of included Shapes, array of shapes indexes
   //	Then,  IF its TYPE is polystroke, instantiates its standard PolyStroke.
   //
   public void connect( Path theBagLoc, String theBagName ) throws ToolException
    {
	// Check the load State
	if ( bagConnectedState ) {
	   throw new ToolException("This Bag is ALREADY connected.");
	}
	logIt("     > Trying to LOAD the BAG in folder " + theBagLoc.toString());
	logIt("       BAG Name: " + theBagName);

	// Validate Read-Access to a .shp "geometry" file and the .shx "index" files
	if ( ! Files.isReadable( theBagLoc.resolve(theBagName + AdHoc.indexFileEXT)) ) {
	   logIt("Unable to CONNECT : found NO readable Index file " + theBagName + AdHoc.indexFileEXT);
	   logIt("   in " + theBagLoc.toString());
	   throw new ToolException("Found NO readable Index file for this Folder/Name.");
	}
	if ( ! Files.isReadable( theBagLoc.resolve(theBagName + AdHoc.geomFileEXT)) ) {
	   logIt("Unable to CONNECT : found NO readable Geometry file " + theBagName + AdHoc.geomFileEXT);
	   logIt("   in " + theBagLoc.toString());
	   throw new ToolException("Found NO readable Geometry file for this Folder/Name.");
	}
	shapeBagLocation = theBagLoc;
	shapeBagName = theBagName;

	// Try loading the Index File Content
	try {
	   // Open the .shx file...
	   gisSeekStream = openSeekableStream( shapeBagLocation.resolve(shapeBagName + AdHoc.indexFileEXT) );

	   // Callibrate the Indexes: INDEXCEILING = number of Shapes (counted from 1 to max)
	   maxShapeRecordNumber = (int) (( gisSeekStream.size() - AdHoc.SHPHeaderSize ) / AdHoc.SHPRecHeadSize);
	   shapesOffsetIndex = new int[maxShapeRecordNumber];
	   shapesLengthIndex = new int[maxShapeRecordNumber];

	   // Load... First get the Header info
	   headerBucket.clear();
	   gisSeekStream.read(headerBucket);

	   // Validate and store data
	   if ( 0x0000270a != headerBucket.getInt(0) ) {
		logIt("Error during INDEX load - BAD FILE CODE in Index file Header: " + headerBucket.getInt(0));
		if ( gisSeekStream.isOpen() ) {  gisSeekStream.close(); }
		throw new ToolException("This Index File Header seems corrupted (bad File Code).");
	   }
	   headerBucket.order(ByteOrder.LITTLE_ENDIAN);

	   // Get Shape Type
	   try {
		bagShapeType = ShapeTypes.getTypeByCode(headerBucket.getInt(AdHoc.BagMBRPos - 4));

	   } catch (ToolException err) {
		logIt("Error during INDEX load - UNKNOWN TYPE in Index file Header: " + (AdHoc.BagMBRPos - 4));
		throw new ToolException("This Index File Header seems corrupted (Unknown Type).");
	   }

	   // Load the whole Index... First initialize the loop... NOTE: recordIDX ranges from ZERO up to (INDEXCEILING - 1) !
	   //	Also set the maximum Shape LENGTH
	   recordIdx = 0;
	   read4KBucket.clear();
	   gisSeekStream.position(AdHoc.SHPHeaderSize);
	   readLen = gisSeekStream.read(read4KBucket);

	   while ( readLen > 0 ) {
		// Walk the just read bytes per recordsize steps
		for (int i = 0; i < readLen ; i = i + AdHoc.SHPRecHeadSize) {
		   shapesOffsetIndex[recordIdx] = 2 * read4KBucket.getInt(i);
		   shapesLengthIndex[recordIdx] = 2 * read4KBucket.getInt(i+4);
		   if ( shapesLengthIndex[recordIdx] > maxShapeSize ) {
			maxShapeSize = shapesLengthIndex[recordIdx];
		   }
		   recordIdx++;
		}

		// Check for possible next bucket
		if ( readLen == AdHoc.READ4KBucketSize ) { 	// cannot be higher ; == means possibly another bucket ahead.
		   read4KBucket.clear();
		   readLen = gisSeekStream.read(read4KBucket);
		} else {					// we just exhausted the previous Read... Exit !
		   readLen = 0;
		}
	   }
	   // Close the .shx file
	   gisSeekStream.close();

	   // Check for coherent reading... Record Index must have reached the index ceiling value !
	   if ( recordIdx != maxShapeRecordNumber ) {
		logIt("Error during INDEX load - INCONSISTANT Index length found: " + maxShapeRecordNumber + " not equal to " + recordIdx);
		throw new ToolException("This Index File seems corrupted (INCONSISTANT Index length).");
	   }

	} catch (IOException ex) {
	   logIt("IO Exception during INDEX load: " + ex.getMessage());
	   throw new ToolException("Unable to proceed - IO Exception during INDEX load: " + ex.getMessage());
	}

	// Finally instantiates its own POLYSTROKE if meaningfull
	if ( isTypeOfPolystroke() ) {
	   bagPolystroke = new PolyStroke(this);
	}
	bagConnectedState = true;
   }


   // Returns a handle to its own Polystroke, if meaningfull
   //
   public PolyStroke getPolystroke() throws ToolException
    {
	if ( bagConnectedState && isTypeOfPolystroke() ) {
	   return bagPolystroke;
	}
	throw new ToolException("This Bag is NOT CONNECTED, or NOT of a PolyStroke TYPE.");
   }

   // Returns this BAG identity
   //
   public String getIdentity()
    {
	if ( bagConnectedState ) {
	   return shapeBagLocation + " : " + shapeBagName;
	} else {
	   return "";
	}
   }

   // Get the Bag MAX shape SIZE, if loaded ( -1 means unloaded (yet) , but is inconsistant with the instantiation sequence)
   public int getBagMaxShapeSize()
    {
	if ( maxShapeSize > 0 ) {
	   return maxShapeSize;
	} else {
	   return 0;
	}
   }

   // Get the Bag Minimum Bounding BOX in GIS coordinates... as  ESRI MBR... [ minX, minY, maxX, maxY ]
   public double[] getBagBoundingBox()
    {
	return new double[] { headerBucket.getDouble(AdHoc.BagMBRPos),
						   headerBucket.getDouble(AdHoc.BagMBRPos + 8),
							headerBucket.getDouble(AdHoc.BagMBRPos + 16),
							   headerBucket.getDouble(AdHoc.BagMBRPos + 24) };
   }

   // Confirm started state
   public boolean bagISLoaded()
    {
	return bagConnectedState;
   }

   // Returns the OFFSET of a given Shape Data in the .shp file
   public int getShapeOffset( int theShape )
    {
	return shapesOffsetIndex[theShape];
   }

   // Returns the SIZE of the Shape Data in the .shp file
   public int getShapeSize( int theShape )
    {
	return shapesLengthIndex[theShape];
   }

   //  Returns the TYPE of Shapes hosted by this Bag (nornally unique !)
   public ShapeTypes getBagType()
    {
	return bagShapeType;
   }

   //  Check TYPE is POLYGON or POLYLINE
   public boolean isTypeOfPolystroke()
    {
	return ( bagShapeType.isTypeOfPolyLine() ||  bagShapeType.isTypeOfPolygon() );
   }

   // Return the IndexFile records count ( = last index + 1 )
   public int getMaxShapeRecordNumber()
    {
	return maxShapeRecordNumber;
   }

   //  Check TYPE is POLYGON or POLYLINE
   public boolean isTypeOfPolyGon()
    {
	return ( bagShapeType.isTypeOfPolygon() );
   }


   // Load a "Polystroke" pointsBuffer with data from the given shape
   //
   public void loadSinglePolyStroke( ByteBuffer theBucket, int theShape ) throws ToolException
    {
	// Meaningfulness of this request must be checked by the caller : Bag is LOADED with shapes of TYPE POLYSTROKE
	try {
	   // Open the .shp file for seeckable reading
	   gisSeekStream = openSeekableStream( shapeBagLocation.resolve(shapeBagName + AdHoc.geomFileEXT) );

	   gisSeekStream.position( shapesOffsetIndex[theShape] + AdHoc.SHPRecHeadSize );
	   gisSeekStream.read(theBucket);

	   gisSeekStream.close();

	} catch (IOException ex) {
	   logIt("IOException during POLYSTROKE load - read error: " + ex.getMessage());
	   throw new ToolException("IOException during POLYSTROKE load - read error; Please see Log.");
	}
   }

   // Emergency .shp file close, if needed...
   //
   public void closeAdHocChannel() throws ToolException
    {
	try {
	   if ( gisSeekStream.isOpen() ) { gisSeekStream.close(); }

	} catch (IOException ex) {
	   logIt("IOException during closeADHOCChannel: " + ex.getMessage());
	   throw new ToolException("IOException during closeADHOCChannel: Please see Log.");
	}
   }


   //Streaming .shp file OPEN
   //
   public void openStreamChannel() throws ToolException
    {
	if ( ! bagConnectedState || streamOpenState ) {
	   logIt("BAG is NOT loaded or CHANNEL is already OPEN: aborting the Open.");
	   throw new ToolException("BAG is NOT loaded yet, or CHANNEL is already OPEN: please correct!");
	}

	try {
	   // Open the .shp file for seeckable reading if needed....
	   shapeFileStream = openSeekableStream( shapeBagLocation.resolve(shapeBagName + AdHoc.geomFileEXT) );

	} catch (ToolException err) {
	   closeStreamChannel();
	   logIt("Error during Open Stream: " + err.getMessage());
	   throw new ToolException(err.getMessage());
	}

	streamOpenState = true;
   }

   // Streaming .shp file CLOSE, if needed...
   //
   public void closeStreamChannel()
    {
	try {
	   if ( shapeFileStream.isOpen() ) { shapeFileStream.close(); }

	} catch (IOException ex) {
	   logIt("IOException during closeSTREAMChannel: " + ex.getMessage());
	}

	streamOpenState = false;
   }


   // Load the NEXT "Polystroke" pointsBuffer with data from the given shape
   //
   public void loadNextPolyStroke( ByteBuffer theBucket, int theShapeIdx ) throws ToolException
    {
	if ( ! bagConnectedState || ! streamOpenState ) {
	   logIt("BAG is NOT more loaded or CHANNEL has been CLOSED: aborting the Loop.");
	   throw new ToolException("BAG is NOT loaded yet, or CHANNEL is NOT OPEN: please correct!");
	}
	if ( theShapeIdx >= maxShapeRecordNumber ) {
	   logIt("Shape INDEX out of bound: " + theShapeIdx + " ; aborting the Loop.");
	   throw new ToolException("NO SUCH SHAPE in this Bag: please correct!");
	}

	// Meaningfulness of this request must be checked by the caller : shapes of TYPE POLYSTROKE
	try {
	   shapeFileStream.position(shapesOffsetIndex[theShapeIdx] + AdHoc.SHPRecHeadSize );
	   shapeFileStream.read(theBucket);

	} catch (IOException ex) {
	   streamOpenState = false;
	   logIt("IOException during SHAPEidx " + theShapeIdx + " load - STREAM terminated on error:");
	   logIt("           " + ex.getMessage());
	   throw new ToolException("IOException during POLYSTROKE load - read error; Please see Log.");
	}
   }


   // Populate a matrix with the Minimum Bounding Boxes (minX, minY, maxX, maxY) for all Shapes  in this  Bag
   //
   public void populateMBRsArray( double[][] theArray ) throws ToolException
    {
	if ( ! bagConnectedState ) {
	   throw new ToolException("Please LOAD this Bag before constructing more indexes.");
	}

	// Check for Polygon or Polyline types - other types do NOT give this information
	if ( ! ( bagShapeType.isTypeOfPolygon() || bagShapeType.isTypeOfPolyLine()  || bagShapeType.isTypeOfMultiPoint()) ) {
	   logIt("Error during MBR INDEX load - not supported by Bag current TYPE: " + bagShapeType);
	   throw new ToolException("The BAG TYPE does NOT support SHAPE MBR; Please correct.");
	}

	theArray = new double[maxShapeRecordNumber][4];
	tmpBucket = ByteBuffer.allocate(40);	   // Just read the shape type and local MBR at the beginning of the record

	try {
	   // Open the .shp file for seeckable reading
	   gisSeekStream = openSeekableStream( shapeBagLocation.resolve(shapeBagName + AdHoc.geomFileEXT) );

	   // Prepare to loop over the INDEX
	   for (int i = 0; i < maxShapeRecordNumber; i++) {
		gisSeekStream.position( shapesOffsetIndex[i] + 4 );
		tmpBucket.clear();
		gisSeekStream.read(tmpBucket);
		tmpBucket.order(ByteOrder.LITTLE_ENDIAN);

		// Check for correct Record TYPE
		if ( tmpBucket.getInt(4) != bagShapeType.getCodeValue()) {
		   logIt("Error Shape load - UNEXPECTED Shape TYPE: " + tmpBucket.getInt(4));
		   logIt("      >>> Just record a void Box collapsed to all zeroes !");
		   for (int j = 0; j < 4; j++) {
			theArray[i][j] = 0.0;
		   }
		} else {
		   for (int j = 0; j < 4; j++) {
			theArray[i][j] = tmpBucket.getDouble(8 * (j+1));
		   }
		}
	   }
	   // Close the .shp file (possibly not reused immediately)
	   gisSeekStream.close();

	} catch (ToolException err) {
	   logIt("" + err.getMessage());
	} catch (IOException ex) {
	   logIt("IO Exception during shapes MBR load: " + ex.getMessage());
	   throw new ToolException("Unable to proceed - IO Exception during shapes MBR load: " + ex.getMessage());
	}

	mbrIndexLoaded = true;
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//			MAINPROGRAMSECTION  2 - SPECIFIC UTILITIES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Open a ByteChannel in Read-Only mode for Seek reading
   private SeekableByteChannel openSeekableStream ( Path theSource ) throws ToolException
    {
	try {
	   // Open the source Flac File as Byte Channel for further parsing
	   return (SeekableByteChannel) Files.newByteChannel( theSource, vAccessRead );

	} catch (IOException e) {
	   logIt("Error OPENING the file in SeekMode: " + e.getMessage());
	   throw new ToolException("Error OPENING the file in SeekMode: " + e.getMessage());
	}
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//			MAINPROGRAMSECTION  3 - GENERIC UTILITIES
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
