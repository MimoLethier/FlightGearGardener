/*
*  Written by Mimo on 16-Oct-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: An object retaining the geometry of RECTANGLES used to manage Geographic coordinates  AND screen windows, to render shapes on
*		   JavaFX.scene.canvas.Canvas
*  ---------------
*    Notes : Supports negative values resulting from the coordinate systems and the vertical orientations (UP for geographic, DOWN for screen coordinates).
*		In line with usual representation, RectBoxes are anchored on their leftmost-bottommost (x, y) corner, with Vertical-UP orientation. This is
*		reflected by POSITIVE heights for geographic surfaces. However, for Screen-mapped boxes, like Canvases, the Height attribute will be
*		NEGATIVE (width, by contrast, is always >= 0) . Scaling factors will be signed when going from geography to screen and vice-versa.
*		Note also that when describing "tile" shapes, RectBoxes left- and bottom-sides are considered IN the shape, while right- and top-sides
*		 are considered as limit values, outside the tile.
*/

package javwep.mimo.gardeningObjects;



public class RectBox
 {
   // Private Resources
   private double leftSideX, bottomSideY;	// A RectBox may be relocated...
   private final double width, height;		// ... but NOT resized !

   // Public Resources


// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   public RectBox( double leftMostX, double bottomMostY, double right2LeftWidth, double bottom2TopHeight )
    {
	leftSideX = leftMostX;
	bottomSideY = bottomMostY;
	width = ( right2LeftWidth >= 0 ) ? right2LeftWidth : -right2LeftWidth;	   // Width is never < 0.
	height = bottom2TopHeight;
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  1. SERVICES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   public double leftX()
    {
	return leftSideX;
   }

   public double bottomY()
    {
	return bottomSideY;
   }

   public double absWidth()   // This is the width ABSOLUTE value (for uniformity, of course)
    {
	return Math.abs(width);
   }

   public double absHeight()   // This is the height ABSOLUTE value
    {
	return Math.abs(height);
   }

   public double width()	// identical to absWidth
    {
	return width;
   }

   public double height()  // This is the SIGNED height, indeed
    {
	return height;
   }

   public double rightX()	   // the rightmost limit
    {
	return leftSideX + width;
   }

   public double topY()	   // the topmost limit
    {
	return bottomSideY + height;
   }

   public boolean isVerticalDOWN()	// means  that Y is growing from TOP to BOTTOM (the usual computer SCREEN orientation)
    {
	return height < 0;
   }

   public void moveAt( double theX, double theY )
    {
	leftSideX = theX;
	bottomSideY = theY;
   }

   public void setDisabled()
    {
	moveAt( Double.NaN, 0.0);
   }

   public boolean isDisabled()
    {
	return Double.isNaN(leftSideX);
   }

}
