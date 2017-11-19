/*
*  Created by Mimo on 08-sept.-2017
*
*  CONTEXT :	Implementation of Applications based on Geographic Information...
*  PURPOSE :	Managing the handling of graphical representations of geogrphic "SHAPES" ( i.e. painting/zooming polystrokes)
*  ROLE :	Utilities to manage the main MAP IMAGING process : graphical surface, snapshot image, zoom
*		   Manage an internal, non-displayable Canvas to support the imaging process (input, output, transformation...)
*  ---------------
*    Notes :
*/

package javwep.mimo.gardenDrawer;


import javwep.mimo.gardeningObjects.AdHoc;
import javwep.mimo.gardeningObjects.RectBox;
import java.io.IOException;
import java.nio.file.Path;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javax.imageio.ImageIO;
import toolException.ToolException;


public class ImagingCanvas
 {
   // Private Resources
   private final Canvas imagingSurface;
   private final GraphicsContext imagingBrush;
   private final double imageWidth, imageHeight;
   private WritableImage imagePixels = null;

   // Public Resources



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  0 -  CONSTRUCTION, INITIALIZATION, and TERMINATION
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   public ImagingCanvas( double theImageWidth, double theImageHeight )
    {
	imagingSurface = new Canvas( theImageWidth, theImageHeight );
	imagingBrush = imagingSurface.getGraphicsContext2D();
	imagingBrush.setGlobalBlendMode(BlendMode.EXCLUSION);
	imagingBrush.setFill(AdHoc.selectingBackgroundColor);
	imagingBrush.setStroke(AdHoc.selectingForegroundColor);
	imageWidth = theImageWidth;
	imageHeight = theImageHeight;
   }



// wwwwwwwwwwwwwwwwwwwwwwwwwww
//		MAINPROGRAMSECTION  1. SERVICES
// wwwwwwwwwwwwwwwwwwwwwwwwwww

   // Set the draw COLOR
   //
   public void setStrokeColor(Color theColor)
    {
	imagingBrush.setStroke(theColor);
   }

   // Reset the Canvas
   //
   public void reset()
    {
	imagingBrush.clearRect( 0, 0, imageWidth, imageHeight );
   }


   // Returns this Canvas GarphicsContext
   //
   public GraphicsContext getImagingBrush()
    {
	return imagingBrush;
   }


   // Write a ROW of Shape Points to the Imaging board
   //
   public void writeShapePoints( double[][] theRows, boolean isClosed )
    {
	if ( isClosed ) {
	   imagingBrush.fillPolygon(theRows[0], theRows[1], theRows[0].length);
	   imagingBrush.strokePolygon(theRows[0], theRows[1], theRows[0].length);
	} else {
	   imagingBrush.strokePolyline(theRows[0], theRows[1], theRows[0].length);
	}
   }


   // Take a SNAPSHOT of the current state of the Canvas and store/returns an Image
   //
   public boolean shapshotISTaken()
    {
	try {
	   SnapshotParameters params = new SnapshotParameters();
	   params.setFill(AdHoc.snapshotFillColor);
	   imagePixels = imagingSurface.snapshot(params, null);
	   return true;

	} catch (Exception err) {
	   return false;
	}

   }


   // Returns the Snapshot IMAGE
   //
   public WritableImage getImage()
    {
	return imagePixels;
   }


   // Paint a rectangular part of this Snapshot Image to another Canvas Graphic Context (implementing basic Resize)
   //
	//   public void paintImagePart( GraphTangle theSource, GraphicsContext theTarget) throws ToolException
   public void paintImagePart( RectBox theSource, GraphicsContext theTarget) throws ToolException
    {
	if ( imagePixels == null ) {
	   throw new ToolException("NO Snapshot available yet.");
	}
	theTarget.drawImage(imagePixels, theSource.leftX(), theSource.topY(), theSource.absWidth(), theSource.absHeight(),
							   0, 0, theTarget.getCanvas().getWidth(), theTarget.getCanvas().getHeight());
   }


   // Write the Snapshot Image to a file
   //
   public void writeImage( Path theFile ) throws ToolException
    {
	try {
	   ImageIO.write(SwingFXUtils.fromFXImage(imagePixels, null), "png", theFile.toFile());

	} catch (IOException err) {
	   throw new ToolException("ERROR WRITING the MapImage: " + err.getMessage());
	}
   }

   public double getImageWidth()
    {
	return imageWidth;
   }

   public double getImageHeight()
    {
	return imageHeight;
   }

   public WritableImage getImagePixels()
    {
	return imagePixels;
   }

}
