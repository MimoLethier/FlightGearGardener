/*
*  Written by Mimo on 05-Oct-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: HTTP Services to download the Terrasync FILES...
*  ---------------
*   Notes :
*/

package javwep.mimo.gardenLoader;


import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import logWriter.CleverLogger;
import toolException.ToolException;


public class HTTPAgent
 {
   // Private Resources
   private static final int BUCKET_SIZE = 4096;
   private final byte[] bucket;
   private final CleverLogger logClerc;
   private boolean debugON;


   // Public Resources



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//			MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   public HTTPAgent( CleverLogger theSyncThreadLog, boolean theDebug )
    {
	logClerc = theSyncThreadLog;
	bucket = new byte[BUCKET_SIZE];
	debugON = theDebug;
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//			MAINPROGRAMSECTION  1 - SERVICES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Load a ".dirindex" content into an ArrayList - ArrayList.length - 2 is expected to give the number of data files
   //
   public int loadDirindexAt(URL theSource, ArrayList<String> theDest) throws ToolException
    {
	theDest.clear();
	try {
	   HttpURLConnection httpBroker = (HttpURLConnection) theSource.openConnection();
	   for (int i = 0; i < 3; i++) {
		if (httpBroker.getResponseCode() != HttpURLConnection.HTTP_OK) {
		   getAPauseOf(1000 + (i * 500));
		   logIt("        <---> Open Lookup for .dirindex: retry " + (i+1) + " ...");
		   httpBroker = (HttpURLConnection) theSource.openConnection();
		} else {
		   break;
		}
	   }
	   int brokerStatus = httpBroker.getResponseCode();
	   if (brokerStatus != HttpURLConnection.HTTP_OK) {
		throw new ToolException(" <!> Got an HTTP Error after 3 trials: " + brokerStatus, brokerStatus);
	   }

	   // Check Content length
	   int downloadedBytesCnt = httpBroker.getContentLength();
	   if ( downloadedBytesCnt == 0 ) {
		return 0;
	   }

	   // ...else open source and copy per line...
	   try (  BufferedReader inStream = new BufferedReader ( new InputStreamReader( httpBroker.getInputStream() ) ) ){

		while ( inStream.ready() ) {
		   theDest.add(inStream.readLine());
		}
	   }
	   logIfVerbose("        --> Downloaded Dirindex: " + theDest.size() + " lines, for a total of "
						   + downloadedBytesCnt + " bytes from");
	   logIfVerbose("            ..." + theSource.toExternalForm().substring(18));
	   httpBroker.disconnect();
	   return downloadedBytesCnt;

	} catch (IOException ex) {
	   throw new ToolException(" <!> IO Exception during download: " + ex.getMessage());
	}
   }


   //  HTTP  file Download: download content whose length is > 0, providing the complete source URL and destination path
   //
   public long getFileContent(URL theSource, Path theTarget) throws ToolException
    {
	long downloadedBytesCnt;

	try {
	   HttpURLConnection httpBroker = (HttpURLConnection) theSource.openConnection();
	   int brokerStatus = httpBroker.getResponseCode();

	   if (brokerStatus != HttpURLConnection.HTTP_OK) {
		throw new ToolException(" <!> Got HTTP Error: " + brokerStatus);
	   }

	   // Check Content length
	   downloadedBytesCnt = httpBroker.getContentLength();
	   if ( downloadedBytesCnt == 0 ) {
		logIfVerbose(" <!> ZERO Content Length for " + theTarget.toString() + " ; File NOT created.");
		return 0;
	   }

	   // ...else open source and Destination stream, then copy per buckets...
	   try (  InputStream inStream = httpBroker.getInputStream() ;
		   FileOutputStream outStream = new FileOutputStream(theTarget.toFile()) ) {

		int readCnt;
		while ( (readCnt = inStream.read(bucket)) != -1 ) {
		   outStream.write(bucket, 0, readCnt);
		}
	   }
	   httpBroker.disconnect();
	   return downloadedBytesCnt;

	} catch (IOException ex) {
	   throw new ToolException(" <!> IO Exception during FILE download: " + ex.getMessage());
	}
   }





// wwwwwwwwwwwwwwwwwwwwwwwwwww
//				@MainSection  3 - SPECIFIC UTILITIES code
// wwwwwwwwwwwwwwwwwwwwwwwwwww




// wwwwwwwwwwwwwwwwwwwwwwwwwww
//				@MainSection  3 - GENERIC UTILITIES code
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Logger utility
   private void logIt(String theMessage)
    {
	logClerc.add(theMessage);
   }

   private void logIfVerbose(String theMessage)
    {
	if ( debugON ) {
	   logClerc.add(theMessage);
	}
   }

   private void logFlush()
    {
	logClerc.writeDisk();
   }

   // Timer utility
   //
   public void getAPauseOf(int milliSec)
    {
	try {
	   Thread.sleep(milliSec);
	} catch (InterruptedException e) {
	}
   }
}
