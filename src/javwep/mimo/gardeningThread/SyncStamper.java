/*
*  Written by Mimo on 12-Oct-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: Supporting the DIRINDEX related synchronisation operations, and local timestamping/verifications
*  ---------------
*   Notes :	Uses SHA-1 to check objects currency. CREATES A NEW FILE ( .ZsyncStamp ) to record local synchronization date and last computed
*		   dirindex signature for comparison with the just downloaded one... ZsyncStamp has no other use ; if deleted, it will be recreated at the
*		   end of the next successful LOAD/SYNC.
*		   Without this file in the folder, the dirindex validity (and folder content currency) will not be assessed globaly,
*		   but onky by comparing the content of the new downloaded dirindex  to the content of the folder, line by line...
*		   This might take more time, but may prove usefull when it is  suspected that the folder content has been manipulated!
*/

package javwep.mimo.gardeningThread;


import appContextLoader.AppCtxtManager;
import java.io.*;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import static java.time.temporal.ChronoUnit.DAYS;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javwep.mimo.gardeningObjects.AdHoc;
import logWriter.CleverLogger;


public class SyncStamper
 {
   // Private Resources
   private final static int BUCKET_SIZE = 4096;
   private MessageDigest digestor;
   private final AppCtxtManager configClerc;
   private final CleverLogger logClerc;
   private final ArrayList<String> syncStampList;
   private final StringBuilder readLine;
   private final Pattern STAMP_PATTERN;
   private Matcher doesMatch;
   private byte[] signature = null, byteBucket;
   private final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
   private final static int STAMP_PREFIXLEN = AdHoc.STAMP_PrefixLEN;
   private boolean inStartedState, signatureAvailable;

   private String stampTile, stampHash, stampFileName;
   private LocalDateTime stampLastScan, stampLastDownload;

   // Public Resources
   public final String ERR_FLAG = "<-HashError->";
   public final static int SUCCESS = 1;
   public final static int DAMAGED = 0;
   public final static int NOT_FOUND = - 1;
   public final static int NOT_READABLE = NOT_FOUND - 1;
   public final static int NO_DATA = NOT_READABLE - 1;



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   public SyncStamper( AppCtxtManager theConfig, CleverLogger theSyncThreadLog )
    {

	logClerc = theSyncThreadLog;
	configClerc = theConfig;
	signatureAvailable = false;
	syncStampList = new ArrayList<>();
	readLine = new StringBuilder();
	byteBucket = new byte[BUCKET_SIZE];
	stampFileName = configClerc.get("Terrasync_STAMPNAME", ".ZsyncStamp");
	STAMP_PATTERN = AdHoc.STAMP_PATTERN;
	try {
	   digestor = MessageDigest.getInstance("SHA-1");
	   inStartedState = true;

	} catch (NoSuchAlgorithmException err) {
	   inStartedState = false;
	}
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  1 -  SERVICES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Use this to assess "Started" state...
   public boolean hasherISStarted(){
	return inStartedState;
   }


   // Compute the SHA1 from the signatures in a Dirindex file
   //
   public String getDirindexSignature( ArrayList<String> theDirindex )
    {
	if ( ! inStartedState ) { return ERR_FLAG + "<NOT_STARTED>";  }

	return getStringArrayListSignature(theDirindex);
   }


   // Write a new or rewrite an existing ZAsyncStamp
   //
   public boolean stampFileWritten( Path theTileFolder, String theSha )
    {
	try ( PrintWriter stampOut = new PrintWriter( new BufferedWriter(
							new FileWriter( theTileFolder.resolve(stampFileName).toFile() ) ) ) ){
	   stampOut.println("stamped:" + LocalDateTime.now().toString());
	   stampOut.println("Z:" + theTileFolder.getFileName().toString()
								+ ":" + theSha + ":" + LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
	   stampOut.flush();
	   return true;

	} catch (IOException ex) {
	   return false;
	}
   }


   // Compare the SHA1 of a new dirindex ArrayList with the current Signature in the local ZsyncStamp file
   //
   public int checkRemoteDirindexOnStamp( ArrayList<String> theRemoteDirindex, Path theLocalDir )
    {
	if ( readStampTileFreshness(theLocalDir) < 0 ) { return NOT_FOUND; 	}

	if ( stampHash.equals( getStringArrayListSignature(theRemoteDirindex)) ) {
	   return SUCCESS;
	}
	return DAMAGED;
   }




// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  2 -  SPECIFIC UTILITIES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Get SHA1 signature of an Array of String
   private String getStringArrayListSignature( ArrayList<String> theList )
    {
	reset();
	for (String aLine : theList) {
	   addText(aLine);
	}
	computeHash();
	return getHash();
   }



   // Read an existing ZAsyncStamp, validate its content and read the info
   private int readStampTileFreshness( Path theTileFolder )
    {
	syncStampList.clear();
	if ( readLocalFileContent(theTileFolder.resolve(stampFileName), syncStampList) != SUCCESS ){
	   stampLastScan = LocalDateTime.MIN;
	   return -1;
	}
	if ( syncStampList.get(0).length() < AdHoc.STAMP_LineMinLEN ) { return -1; }
	if ( setLastScanDateTime( syncStampList.get(0).substring(STAMP_PREFIXLEN) ) != SUCCESS ) { return -2; }

	doesMatch = STAMP_PATTERN.matcher(syncStampList.get(1));
	if ( ! doesMatch.matches() ) { return -3; }
	stampHash = doesMatch.group(4);
	if ( setLastDownloadTime(doesMatch.group(6)) != SUCCESS ) { return -4; }
	stampTile = doesMatch.group(2);
	if ( ! theTileFolder.endsWith(stampTile) ) { return -5;	}

	return getLastScanRank();
   }


   // Load content of local .dirindex
   private int readLocalFileContent( Path theDir, ArrayList<String> theContent )
    {
	try ( BufferedReader theReader = new BufferedReader( new FileReader( theDir.toFile() ) ) ) {
	   readLine.setLength(0);
	   while ( theReader.ready() ) {
		theContent.add(theReader.readLine());
	   }
	   return SUCCESS;

	} catch (FileNotFoundException ex) {
	   return NOT_FOUND;
	} catch (IOException ex) {
	   return NOT_READABLE;
	}
   }


   // Get MD5 Hash of a file...
   //
   public String getMD5HashOfFile( Path theDir )
    {
	try ( DigestInputStream theHasher = new DigestInputStream( new FileInputStream( theDir.toFile() ) , digestor) )  {
	   while ( ( theHasher.read( byteBucket ) ) != -1 ) {   }

	} catch (IOException err) {
	   logIt("" + err.getMessage());
	   return "";
	}

	signature = digestor.digest();

	int tempVal, byteCount = signature.length;
	char[] md5Value = new char[2 * byteCount];

	for ( int i = 0 ; i < byteCount ; i++ ) {
	   tempVal = ((int) signature[i]) & 0xFF;
	   md5Value[2 * i] = HEX_CHARS[tempVal >>> 4];
	   md5Value[(2 * i) + 1] = HEX_CHARS[tempVal & 0x0F];
	}
	return new String(md5Value);
   }


   // Use this to feed text on the fly...
   private void addText(String theText)
    {
	if ( ! inStartedState ) { return; 	}
	byte[] textToHash = theText.getBytes();
	digestor.update(textToHash);
	signatureAvailable = false;
   }

   // Use this one to close the text feed and compute the hash code (... and reset the hasher...)
   private void computeHash()
    {
	if ( ! inStartedState ) { return; 	}
	signature = digestor.digest();
	signatureAvailable = true;
   }

   // Use this to get the result if any
   private String getHash()
    {
	if ( signatureAvailable ) {
	   int tempVal, byteCount;
	   byteCount = signature.length;
	   char[] md5Value = new char[2 * byteCount];

	   for ( int i = 0 ; i < byteCount ; i++ ) {
		tempVal = ((int) signature[i]) & 0xFF;
		md5Value[2 * i] = HEX_CHARS[tempVal >>> 4];
		md5Value[(2 * i) + 1] = HEX_CHARS[tempVal & 0x0F];
	   }
	   return new String(md5Value);
	} else {
	   return ERR_FLAG;
	}
   }

   // Use this one to reset the hasher in between (previous text is discarded)
   private void reset()
    {
	if ( ! inStartedState ) { return; 	}
	digestor.reset();
	signatureAvailable = false;
   }


   // Stamp AGE, in days
   private long getLastScanAge()
    {
	return DAYS.between(stampLastScan, LocalDateTime.now());
   }

   // Stamp freshness Score:
   private int getLastScanRank()
    {
	int threshold = 90;
	long tmpAge = getLastScanAge();

	for (int i = 4; i > 0; i--) {
	   if ( tmpAge < threshold ) { return i;	}
	   threshold = threshold << 1;
	}
	return 0;
   }

   // Decode LastScan Date string
   private int setLastScanDateTime( String lastScanDTStr )
    {
	try {
	   stampLastScan = LocalDateTime.parse(lastScanDTStr);
	   return SUCCESS;

	} catch (DateTimeParseException err) {
	   stampLastScan = LocalDateTime.MIN;
	   return NO_DATA;
	}
   }

   // Get / Set number of seconds for the current Stamp download time
   private String getLastDownloadTimeSeconds()
    {
	return "" + stampLastDownload.toEpochSecond(ZoneOffset.UTC);
   }

   private void setLastDownloadTime( LocalDateTime lastDownloadDT )
    {
	stampLastDownload = lastDownloadDT;
   }


   private int setLastDownloadTime( String lastDownloadSecStr )
    {
	long secondsLaps;

	try {
	   secondsLaps = Long.parseLong(lastDownloadSecStr);
	   stampLastDownload = LocalDateTime.ofEpochSecond(secondsLaps, 0, ZoneOffset.UTC);
	   return SUCCESS;

	} catch (NumberFormatException | DateTimeException err) {
		stampLastDownload = LocalDateTime.MIN;
		return NO_DATA;
	}
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//				@MainSection  3 - GENERIC UTILITIES
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
