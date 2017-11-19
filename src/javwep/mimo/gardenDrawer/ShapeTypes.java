/*
*  Written by Mimo on 27-août-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: Definition of the various TYPES of Shapes used in MAPs. See : https://en.wikipedia.org/wiki/Shapefile
*  ---------------
*    Notes : This code is quite directly adapted from Thomas Diewald ShapeFileReader. Please visit  http://thomasdiewald.com/blog
*		diewald_shapeFileReader :  a Java Library for reading ESRI-shapeFiles (*.shp, *.dfb, *.shx).
*		Copyright (c) 2012 Thomas Diewald : "This source is free software; you can redistribute it and/or modify it under the terms of the GNU
*			General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later
*			version. This code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
*			warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
*			A copy of the GNU General Public License is available on the World Wide Web at <http://www.gnu.org/copyleft/gpl.html>.
*			You can also obtain it by writing to the Free Software Foundation,  Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA."
*/

package javwep.mimo.gardenDrawer;


import toolException.ToolException;


public enum ShapeTypes {

   NullShape    (  0, false, false ),   //  Null_Shape : value = 0, no Fields

   Point        (  1, false, false ),	// Point : value = 1, fields : Xcoord, YCoord
   PointZ       ( 11,  true,  true ),  // PointZ : value = 11, fields :  Xcoord, YCoord, ZCoord (Opt. M)
   PointM       ( 21,  false, true ),  // PointM : value = 21, fields :  Xcoord, YCoord, MValue

   PolyLine     (  3, false, false ),	// Polyline : value = 3, fields : MBR, Number of parts, Number of points, Parts, Points
   PolyLineZ    ( 13,  true,  true ),	// PolylineZ : value = 13, fields : MBR, Number of parts, Number of points, Parts, Points, Z range, Z arra,...
   PolyLineM    ( 23,  false, true ),	// PolylineM : value = 23, fields : MBR, Number of parts, Number of points, Parts, Points,...

   Polygon      (  5, false, false ),	// Polygone : value = 5, fields : MBR, Number of parts, Number of points, Parts, Points
   PolygonZ     ( 15,  true,  true ),	// PolylineZ : value = 15, fields : MBR, Number of parts, Number of points, Parts, Points, Z range, Z arra,...
   PolygonM     ( 25,  false, true ),	// PolylineM : value = 25, fields : MBR, Number of parts, Number of points, Parts, Points,...

   MultiPoint   (  8, false, false ),	// Multipoint : value = 8, fields : MBR, Number of points, Points
   MultiPointZ  ( 18,  true,  true ),	// MultipointZ : value = 18, fields : MBR, Number of points, Points, Z range, Z array
   MultiPointM  ( 28,  false, true ),	// MultipointM : value = 28, fields : MBR, Number of points, Points

   MultiPatch   ( 31,  true,  true );	// Multipatch : value = 31, fields : MBR, Number of parts, Number of points, Parts, Part types, Points, Z range, Z array...



   // Properties
   private int codeValue;
   private boolean has_z_values, has_m_values;


   // Construction
   ShapeTypes( int theCode, boolean theZFlag, boolean theMFlag)
    {
	codeValue = theCode;
	has_z_values = theZFlag;
	has_m_values = theMFlag;
   }


   // Access to Props
   public int getCodeValue() { return codeValue; }
   public boolean hasZvalues() { return has_z_values; }
   public boolean hasMvalues() { return has_m_values; }


   // Access to type by ID
   public static ShapeTypes getTypeByCode(int theCode) throws ToolException
    {
	for ( ShapeTypes aType : ShapeTypes.values() ) {
	   if ( aType.codeValue == theCode ) {
		return aType;
	   }
	}
	throw new ToolException("No such Shape Type!");
   }


   // Validators
   public boolean isTypeOfPolygon()
    {
      return ( this == Polygon | this == PolygonM | this == PolygonZ );
   }

   public boolean isTypeOfPolyLine()
    {
      return ( this == PolyLine | this == PolyLineM | this == PolyLineZ );
   }

   public boolean isTypeOfPoint()
    {
      return ( this == Point | this == PointM | this == PointZ );
   }

   public boolean isTypeOfMultiPoint()
    {
      return ( this == MultiPoint | this == MultiPointM | this == MultiPointZ );
   }

}
