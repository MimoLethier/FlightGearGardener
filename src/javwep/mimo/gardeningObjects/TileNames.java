/*
*  Written by Mimo on  26-sept-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: Handling the Access to Terrasync-specific folders and data files  by name ( "e000s00" patterns, etc...)
*  ---------------
*   Notes : HECTOTILE designates the intermediate 10° by 10° boxes corresponding to the parent folder of usual  1° by 1° Tiles folders.
*		char[] arrays are used extensively because they are reusable at no cost, and did prove quite efficient for degree-name translations
*/

package javwep.mimo.gardeningObjects;


public class TileNames
 {
   // Private Resources
   private static char[] tenLONGLAT = new char[6], uniLONGLAT = new char[6];
   private static char[] tileNAME = new char[7];
   private static int uniLongit, tenLongit, uniLatit, tenLatit, tmpLon, tmpLat;

   // Public Resources
   public static final int tileSubpathLength = "/w020n40/w012n45".length();



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww


// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  1 -  Services
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Translates GRIDCELL into Terrasync TILENAME (for instance "w012n45". Note that negative Long translate to "w", positive to "e" prefix. Idem for "s" versus "n")
   //
   public static String getTileNameForCell( GridCell theCell )
    {
	tmpLon = theCell.getEsriLeftX();
	tmpLat = theCell.getEsriBottomY();

	if ( tmpLon >= 180 || tmpLat >= 90 ) { return "§NotEsrI";  }
	if ( tmpLon < 0 ) {
	   if ( tmpLon < -180 ) { return "§NotEsrI"; }
	   uniLongit = -tmpLon;
	   tileNAME[0] = 'w';
	} else {
	   uniLongit = tmpLon;
	   tileNAME[0] = 'e';
	}
	if ( tmpLat < 0 ) {
	   if ( tmpLat < -90 ) { return "§NotEsrI"; }
	   uniLatit = -tmpLat;
	   tileNAME[4] = 's';
	} else {
	   uniLatit = tmpLat;
	    tileNAME[4] = 'n';
	}

	uniLONGLAT = Integer.toString(100000 + (100 * uniLongit) + uniLatit ).toCharArray();
	tileNAME[1] = uniLONGLAT[1];
	tileNAME[2] = uniLONGLAT[2];
	tileNAME[3] = uniLONGLAT[3];
	tileNAME[5] = uniLONGLAT[4];
	tileNAME[6] = uniLONGLAT[5];

	return new String(tileNAME);
   }


   // Translates GRIDCELL into Terrasync HECTOTILENAME (for instance "w020n40").
   //
   public static String getHectoTileNameForCell( GridCell theCell )
    {
	tmpLon = theCell.getEsriLeftX();
	tmpLat = theCell.getEsriBottomY();

	if ( tmpLon >= 180 || tmpLat >= 90 ) { return "§NotEsrI";  }
	if ( tmpLon < 0 ) {
	   if ( tmpLon < -180 ) { return "§NotEsrI"; }
	   tenLongit = 9 - tmpLon;
	   tileNAME[0] = 'w';
	} else {
	   tenLongit = tmpLon;
	   tileNAME[0] = 'e';
	}
	if ( tmpLat < 0 ) {
	   if ( tmpLat < -90 ) { return "§NotEsrI"; }
	   tenLatit = 9 - tmpLat;
	   tileNAME[4] = 's';
	} else {
	   tenLatit = tmpLat;
	    tileNAME[4] = 'n';
	}

	tenLONGLAT = Integer.toString(100000 + (100 * tenLongit) + tenLatit ).toCharArray();
	tileNAME[1] = tenLONGLAT[1];
	tileNAME[2] = tenLONGLAT[2];
	tileNAME[3] = '0';
	tileNAME[5] = tenLONGLAT[4];
	tileNAME[6] = '0';

	return new String(tileNAME);
   }


   // Returns a CharArray[17] in the form of "/w020n40/w012n45", in URL form...
   //
   public static boolean foundUrlSubpathForCell( char[] theResult, GridCell theCell )
    {
	tmpLon = theCell.getEsriLeftX();
	tmpLat = theCell.getEsriBottomY();

	if ( theResult.length != tileSubpathLength || tmpLon >= 180 || tmpLat >= 90 ) { return false;  }
	if ( tmpLon < 0 ) {
	   if ( tmpLon < -180 ) { return false; }
	   uniLongit = -tmpLon;
	   tenLongit = 9 - tmpLon;
	   theResult[1] = 'w';
	   theResult[9] = 'w';
	} else {
	   uniLongit = tmpLon;
	   tenLongit = tmpLon;
	   theResult[1] = 'e';
	   theResult[9] = 'e';
	}
	if ( tmpLat < 0 ) {
	   if ( tmpLat < -90 ) { return false; }
	   uniLatit = -tmpLat;
	   tenLatit = 9 - tmpLat;
	   theResult[5] = 's';
	   theResult[13] = 's';
	} else {
	   uniLatit = tmpLat;
	   tenLatit = tmpLat;
	   theResult[5] = 'n';
	   theResult[13] = 'n';
	}
	uniLONGLAT = Integer.toString(100000 + (100 * uniLongit) + uniLatit ).toCharArray();
	tenLONGLAT = Integer.toString(100000 + (100 * tenLongit) + tenLatit ).toCharArray();
	theResult[0] = '/';
	theResult[2] = tenLONGLAT[1];
	theResult[3] = tenLONGLAT[2];
	theResult[4] = '0';
	theResult[6] = tenLONGLAT[4];
	theResult[7] = '0';
	theResult[8] = '/';
	theResult[10] = uniLONGLAT[1];
	theResult[11] = uniLONGLAT[2];
	theResult[12] = uniLONGLAT[3];
	theResult[14] = uniLONGLAT[4];
	theResult[15] = uniLONGLAT[5];

	return true;
   }


   // Transforms a URL subpath of Tile into a local file subpath...
   //
   public static boolean urlGotMutedToPath( char[] theIn, char[] theOut, char theSepar )
    {
	if ( theOut.length != theIn.length ) { return false;  }

	for (int i = 0; i < theIn.length; i++) {
	   if ( theIn[i] == '/') {
		theOut[i] = theSepar;
	   } else {
		theOut[i] = theIn[i];
	   }
	}
	return true;
   }

}
