# FlightGearGardener
A JavaFX Asynchronous SyncManager for FlightGear Terrasync scenery data

1. Purposes

The global purpose behind this Application was to use JavaFX to produce (yet another) asynchronous 
downloader for FlightGear Terrasync Scenery, a kind of light version of the well known TerraMaster 
tool, but based on the DNS/HTTP download principles, and using JavaFX instead of Swing. 

This is just a first trial, to be regarded as a reasonably usable output of a coding exercize... 
(covering controlled synchronization only for TERRAIN, OBJECTS and AIRPORTS, with additional but 
limited support for browsing a Terrasync server and downloading (piecewise!) Building, Models, 
Pylons..., pretty anything found there, indeed). 

It has evolved as a learning workbench, for me and possibly other Java developers interrested 
in geting some grip over things like geographic data handling (although there is no use of 
advanced technology like GeoTools)... Considering that the most time-consuming operations are 
Network and MMI interactions, considering also the asynchronous (out-of-flight) nature 
of this application, design and coding where focused mainly on comfortable use, intuitive 
interactions, and readable, reusable internal structures, not maximum speed (using systematically 
.dirindex files and SHA-1 signatures is however delivering quite satisfying results).

While certainly not a model of Java OO development (using less than 25 specific classes clearly 
points in the direction of a persistent "procedural" bias), and possibly for that reason too,
I see FlightGearGardener also as a (very humble!) celebration of James Goslin's marvelously 
clever, robust, expressive, predictable, well-balanced and, all in one, pleasurable creation...


2. Usage and rights

Beyond the java code written specifically for FlightGearGardener, there are only four externaly
sourced stuff:
	- the Java API provided by the jdk1.8.0_102 and Netbeans 8.2, of course;
	- NaturalEarth Shapefiles (ne_110m_admin_0_countries) : 
			http://www.naturalearthdata.com/about/terms-of-use/ ;
	- Brian Wellington's dnsJava-2.1.7 library : 
			http://www.dnsjava.org/dnsjava-current/README ;
	- Thomas Diewald work on reading ShapeFiles :
			http://thomasdiewald.com/blog .
	
Concerning Thomas Diewald, his work was effectively the main avenue enabling me to understand quickly 
how to handle and render ESRI ShapeFiles in Java. Though I eventually did not use his (much more 
ambitious and fully engineered) code, it's a pleasure to recognize his seminal impact. Moreover, 
his blog is a real delight for those loving computer graphics and algorithmic fancies rendering...

Concerning Brian Wellingtonlet's dnsjava, let's mention the following license notice, as requested:
	"dnsjava is placed under the BSD license.  Several files are also under
	additional licenses; see the individual files for details.

	Copyright (c) 1999-2005, Brian Wellington
	All rights reserved.

	Redistribution and use in source and binary forms, with or without
	modification, are permitted provided that the following conditions are met:

		* Redistributions of source code must retain the above copyright notice,
		  this list of conditions and the following disclaimer.
		* Redistributions in binary form must reproduce the above copyright notice,
		  this list of conditions and the following disclaimer in the documentation
		  and/or other materials provided with the distribution.
		* Neither the name of the dnsjava project nor the names of its contributors
		  may be used to endorse or promote products derived from this software
		  without specific prior written permission.

	THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
	ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
	WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
	DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
	ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
	(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
	LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
	ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
	(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
	SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE."

	
Obviously, FlightGearGardener, being a public-garden hobby, does not provide any more
warranties about anything. 

Since human intellectual productions mostly result from encountering, understanding, and 
reassembling other human intellectual productions, there is no constraint put on reusing 
FlightGearGardener specific code and so keeping the wheels turning, as long as this does not 
concur to freedom limitation, ecological or human harm, or commercial profits.


3. Install

See AppGeneralReadme.
