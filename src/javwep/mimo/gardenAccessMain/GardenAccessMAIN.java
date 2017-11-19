/*
*  Written by Mimo on 24-août-2017
*
*  CONTEXT	: Preparation of a revisited TERRAMASTER utility to download FLIGHTGear Scenery data
*  PURPOSE	: Understanding the TerraMaster architecture while building a FXML MMI
*  ROLE	: MMI Scene setup - MAIN FXML entrance
*  ---------------
*   Notes : Uses SIMPLE SCENE FRAMER to render a customized window frame setup - See project Javgrip for details and further customization
*/

package javwep.mimo.gardenAccessMain;


import aSimpleSceneFramer.SimpleFramer;
import java.net.URL;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import popChooser.Chooser;


public class GardenAccessMAIN extends Application
 {

   //   Private Resources
   private Pane mainDecor;
   private static Stage mainStage;
   private SimpleFramer sceneFramer;
   private FXMLLoader mainLoader;
   private GardenAccessCTRL mainController;
   private Chooser notifier;
   private final URL styleScript = getClass().getResource("GardenAccessSTYLE.css");
   private final URL decorScript = getClass().getResource("GardenAccessGUI.fxml");




    @Override
    public void start( Stage primaryStage ) throws Exception
    {
	mainStage = primaryStage;

	// Load the USER APPLICATION SCENE CONTENT
	mainLoader = new FXMLLoader(decorScript);
	mainDecor = (AnchorPane) mainLoader.load();
	mainController = mainLoader.getController();

	// Decorate the STAGE and SCENE FRAME
	mainStage.setX(400);
	mainStage.setY(100);
	sceneFramer = new SimpleFramer(mainStage, mainDecor);
	sceneFramer.allowNamePlate("LIGHT ASYNCHRONOUS TERRASYNC DOWNLOADER");
	sceneFramer.allowIconifyability();
	sceneFramer.allowExitRequest();

	// Take care of EXIT requests (never fired if the Exit request is not allowed)
	sceneFramer.exitTrigger.setOnAction( mainController::onExitAsked );

	// Get the resulting DECORATED SCENE and apply the CSS styles
	Scene scene = sceneFramer.getDecoratedScene();
	scene.getStylesheets().add(styleScript.toExternalForm());

	// Go for display
	mainStage.setScene(scene);
	mainStage.show();

	//   Popup notifier creation (running on its own Stage)
	notifier = new Chooser(mainStage);

	// Rendez-vous with the real business...
	mainController.getAlive(notifier, mainStage);
   }


   // mainStage accessor for other modules, if needed
   public static Stage getHomeStage()
    {
	return mainStage;
   }


   // Legacy...
   public static void main( String[] args )
    {
	launch(args);
   }
}
