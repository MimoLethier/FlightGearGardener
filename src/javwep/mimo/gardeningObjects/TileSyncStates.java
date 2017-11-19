/*
*  Written by Mimo on 16-sept-2017
*
*  CONTEXT	:  Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: Definition of the various "STATE" of TILEs, as seen from the Gardener viewpoint.
*  ---------------
*    Notes : The 3 last "states" are in fact indicating a Tile selected for a specific ACTION, not a proper state !
*/

package javwep.mimo.gardeningObjects;


public enum TileSyncStates {

   UGLY	((byte) -1),	  //  NOT USABLE (Out of ESRI boundaries...)
   UNKNOWN	((byte)  0),   // Just born, usable but unqualified
   BROKEN	((byte)  1),	  //  Get some ERROR during Current Action
   ORPHAN	((byte)  2),    // Found NO corresponding Folder in the Terrasync Server (may be a result of download error !), or nothing related
					// to Flightgear in this Folder...
   POPULATED((byte)  4),    // Found NO dirindex, but at least some  "9999999.btg.gz"  files...
   PACKED_1 ((byte)  5),    // Found dirindex with LastModifTime MORE than  4 * GAP days ago
   PACKED_2 ((byte)  6),    // Found dirindex with LastModifTime LESS than  4 * GAP days ago
   PACKED_3 ((byte)  7),    // Found dirindex with LastModifTime LESS than  2 * GAP days ago
   PACKED_4 ((byte)  8),    // Found dirindex with LastModifTime LESS than GAP days ago
   TOCLEAR	((byte) 20),    // Selected for Removing
   TOSYNC	((byte) 21),    // Selected for SYNC
   TOLOAD	((byte) 22);    // Selected for LOAD


   public final byte code;

   TileSyncStates( byte theCode )
    {
	code = theCode;
   }

   public static TileSyncStates getStateByCode( byte theCode )
    {
	for ( TileSyncStates aState : TileSyncStates.values() ) {
	   if ( aState.code == theCode ) {
		return aState;
	   }
	}
	return UGLY;
   }
}
