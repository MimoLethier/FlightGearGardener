/*
*  Written by Mimo on 05-Oct-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: NAPTR Services to locate the Terrasync servers...
*  ---------------
*   Notes :  This uses DNSJAVA :  "dnsjava is placed under the BSD license.  Several files are also under additional licenses; see the individual files for details."
*				Copyright (c) 1999-2005, Brian Wellington - All rights reserved.
*				Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
*				conditions are met:
*						* Redistributions of source code must retain the above copyright notice,
*						  this list of conditions and the following disclaimer.
*						* Redistributions in binary form must reproduce the above copyright notice,
*						  this list of conditions and the following disclaimer in the documentation
*						  and/or other materials provided with the distribution.
*						* Neither the name of the dnsjava project nor the names of its contributors
*						  may be used to endorse or promote products derived from this software
*						  without specific prior written permission.
*				THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
*				WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
				PARTICULAR PURPOSE ARE DISCLAIMED. ..."
*		See the AppGeneralReadMe file for complete disclaimer.
*/

package javwep.mimo.gardenLoader;


import appBasicToolKit.AppGroundKit;
import java.net.MalformedURLException;
import java.net.URL;
import logWriter.CleverLogger;
import org.xbill.DNS.*;
import toolException.ToolException;


public class DNSAgent
 {
   // Private Resources
   private final CleverLogger logClerc;
   private final String popLookupDnsName, excludedSite;
   private Lookup dnsScout;
   private Record[] answer;

   // Public Resources



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//			MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   public DNSAgent( AppGroundKit theKit )
    {
	logClerc = theKit.logClerc;
	popLookupDnsName = theKit.ctxtClerc.get("Terrasync_DNSLookupName", "");
	excludedSite = theKit.ctxtClerc.get("Terrasync_DNSExcluded", "§NotA;Site§").isEmpty()
				         ? "§NotA;Site§" : theKit.ctxtClerc.get("Terrasync_DNSExcluded", "§NotA;Site§");
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//			MAINPROGRAMSECTION  1 - SERVICES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Find Sources and returns the currently "prefered" one...
   //
   public URL findPreferedSource() throws ToolException
    {
	if ( popLookupDnsName.isEmpty() ) {
	   logIt("   <!> Terrasync POPs Marker not found in Dictionary: please check.");
	   throw new ToolException("Terrasync POPs Marker not found in Dictionary; unable to proceed.");
	}

	int order =1024, pref = 1024;
	String target = "";

	try {
	   logIt("   >>> Looking for Terrasync POPs using DNS lookup at " + popLookupDnsName + ", Type.ANY, DClass.IN)");
	   logFlush();
	   dnsScout = new Lookup(popLookupDnsName, Type.ANY, DClass.IN);
	   dnsScout.run();
	   if ( dnsScout.getResult() != Lookup.SUCCESSFUL ) {
		logIt("   <> Lookup Error - Status: " + dnsScout.getErrorString());
		throw new ToolException("Error running LOOKUP: Probably due to NO PUBLIC NETWORK CONNECTION !");
	   }
	   logIt("   <> Lookup was Successfull...");
	   answer = dnsScout.getAnswers();
	   for (int i = 0; i < answer.length; i++) {
		logIt("      -> Result " + i + " : " + answer[i].rdataToString() );
		NAPTRRecord record = (NAPTRRecord) answer[i];
		if ( ! record.getRegexp().startsWith("!^.*$!") || ! record.getRegexp().endsWith("!") ) { continue; 	}
		// Exclude a site which seems a bit underperforming - No more than ONE for now !
		if ( record.getRegexp().contains(excludedSite) ) { continue; 	}
		if ( record.getOrder() < order ) {
		   order = record.getOrder();
		   pref = record.getPreference();
		   target = record.getRegexp();
		} else if ( record.getOrder() == order ) {
		   if ( record.getPreference() < pref ) {
			pref = record.getPreference();
			target = record.getRegexp();
		   }
		}
	   }
	   logIt("    ---> Retained Target: " + target);
	   logFlush();
	   return new URL(target.substring(6, target.length() - 1));

	} catch (TextParseException ex) {
	   logIt("   <!> Parse Exception: " + ex.getMessage());
	   logFlush();
	   throw new ToolException("Parse Exception: " + ex.getMessage());
	} catch (MalformedURLException ex) {
	   logIt("   <!> Invalid URL Exception: " + ex.getMessage());
	   logFlush();
	   throw new ToolException("Invalid URL Exception: " + ex.getMessage());
	}
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
