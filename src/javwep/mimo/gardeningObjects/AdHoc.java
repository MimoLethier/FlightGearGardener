/*
*  Written by Mimo on 16-09-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: Dictionary of the FlightGear Gardener constants
*  ---------------
*    Notes :
*/

package javwep.mimo.gardeningObjects;


import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import javafx.scene.paint.Color;


public final class AdHoc
 {
   // Private Resources

   // Public Resources
   public static final String indexFileEXT = ".shx", geomFileEXT = ".shp";
   public static final String BTGFilesEXT = "*.btg.gz";
   public static final String TERR_TERRAIN = "Terrain";
   public static final String TERR_OBJECTS = "Objects";
   public static final String TERR_AIRPORTS = "Airports";
   public static final String TERR_DIRINDEX = ".dirindex";
   public static final int PATH_PrefixLEN = "path:".length();
   public static final int STAMP_PrefixLEN = "stamped:".length();
   public static final int STAMP_LineMinLEN = "stamped:1970-01-01".length();

   public static final Pattern UNITTILE_PATTERN = Pattern.compile("([ew])(\\p{Digit}{3})([ns])(\\p{Digit}{2})");
   public static final Pattern HECTOTILE_PATTERN = Pattern.compile("([ew])([0-1][0-9]0)([ns])([0-9]0)");
   public static final Pattern STAMP_PATTERN = Pattern.compile("(^Z:)(.+)(:)([0-9a-f]{40})(:)([0-9]+)");
   public static final Pattern TILE_TERR_PATTERN = Pattern.compile("(^f:)(.+)(:)([0-9a-f]{40})(:)([0-9]+)");
   public static final Pattern TILE_DIR_PATTERN = Pattern.compile("(^d:)(.+)(:)([0-9a-f]{40})");
   public static final Pattern TILE_AIRP_PATTERN = Pattern.compile("(^f:)([A-Z0-9]{4})(.btg.gz:)(.+)");
   public static final Pattern AIRP_FILE_PATTERN = Pattern.compile("([A-Z0-9]{4})(.btg.gz)");
   public static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("HH'h' mm'm' ss'sec'");
   public static final DateTimeFormatter SHORT_DATETIME = DateTimeFormatter.ofPattern("dd MMMM yyyy, HH'h' mm'm' ss'sec'");


   public static final int SHPHeaderSize = 100;			// Size in Bytes of the .shp MAIN Header
   public static final int SHPRecHeadSize = 8;			// Size in Bytes of each .shp RECORD Header
   public static final int BagMBRPos = 36;			// Offset in Bytes in the MAIN Header where the MBR (Minimum bounding rectangle) is located
   public static final int READ4KBucketSize = 1024 * 4;	// 4 kBytes, used to read the shape files
   public static final int partCountPos = 36;			// Offset in a .shp record where the NUMBER of PARTS is given
   public static final int pointCountPos = 40;			// Offset in a .shp record where the TOTAL NUMBER of POINTS is given

   public static final double[] standardESRI = {-180, -90, 180, 90}; // leftX, bottomY, rightX, topY, in degrees. NOTE: In fact, longitude +180° is never
										   // used, neither latitude +90° (indeed converted into -180° and -90° respectively)
   public static final double minNotNullDimension = 0.000001;	   // For zero comparison
   public static final double minUsableDimension = 45;		   // In Esri degrees
   public static final double imagingFactor = 6.0;			   // Expansion of the standard ESRI width-height to the imaging width-height (Abs. values)
   public static final int zoomedRegionMidDim = 23;			   // Region square half width, in degrees - Must be an Integer
   public static final int zoomedRegionDim = 2 * zoomedRegionMidDim;// Region square full width, in degrees - Must be an even Integer

   public static final Color selectingBackgroundColor = Color.TAN;
   public static final Color selectingForegroundColor = Color.BLUEVIOLET;
   public static final Color selectingCrossColor = Color.LIGHTSLATEGREY;
   public static final Color focusBoxColor = Color.CRIMSON;
   public static final Color downloadGridColor = Color.SLATEGREY;
   public static final Color downloadCrossColor = Color.DARKSLATEGREY;
   public static final Color snapshotFillColor = Color.POWDERBLUE;
   public static final Color packedFillColor = Color.GREEN;
   public static final Color populatedFillColor = Color.YELLOWGREEN;
   public static final Color selectedFillColor = Color.ORCHID;
   public static final Color toLoadFillColor = Color.CADETBLUE;
   public static final Color toClearFillColor = Color.RED;

   // Tile Freshness
   public static final Color NODIRINDEX_CELL = Color.rgb(250, 230, 250, 0.6);
   public static final Color OUTOFSIGHT_CELL = Color.rgb(240, 240, 10, 0.6);
   public static final Color QUITEOLDER_CELL = Color.rgb(170, 220, 40, 0.8);
   public static final Color ABITOLDER_CELL = Color.rgb(90, 200, 80, 0.8);
   public static final Color QUITECRISP_CELL = Color.rgb(0, 170, 140, 0.8);
}
