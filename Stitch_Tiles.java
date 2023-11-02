import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.measure.Calibration;
import ij.plugin.ImagesToStack;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.plugin.FolderOpener;
import ij.plugin.ImageCalculator;

//import ch.epfl.biop.operetta.OperettaManager; //Instead, I coped over the OperattaManager, because otherwise namespace errors if Fiji already has this plugin installed

import java.awt.Point;
import ome.xml.model.Well;
import ome.xml.model.WellSample;


public class Stitch_Tiles implements PlugIn {
	
	//Global variables:
	static String currentChannelPath;
	static String imgPath;
	static String xmlPath;
	static String outputPath;
	static int stitchingChannel;
	static int nChannels;
	static int nPlanes;
	static boolean doFFC; //do Flat Field Correction (Using Basic Plugin)
	static boolean startedFromMacro = false;
	
	/*
	 * THE THREE METHODS BELOW ARE DIFFERNET OPTIONS FOR ENTRY POINTS.
	 * THEY ARE INVOKED EITHER:  
	 * 1) THOUGH THE IMAGEJ PLUGINS MENU
	 * 2) THROUGH A (STARTUP) MACRO (AUTOMATICALLY CALLED WHEN IMAGEJ STARTS UP) (FIJI\plugins\Scripts\Plugins\AutoRun)
	 * 3) FROM A IDE, LIKE ECLIPSE
	 */
	
	
	//This "run" method is the one that starts when starting the plugin from inside ImageJs menu
	@Override
	public void run(String arg) {
		IJ.log("Started from Plugins menu inside ImageJ");
		String status = setupVariablesAndStart();
		//return status; //Not possible when run from here	
		}

	//This method is the one we start from the macro file in the AutoRun folder
	public static String startFromMacro(String imgPath_input, String outputPath_input, String xmlPath_input, String stitchingChannel_input, String nChannels_input, String nPlanes_input, String doFFC_input) {
		IJ.log("Called from (startup) macro");
		System.out.println("Called from (startup) macro");
		
		startedFromMacro = true;
		
		imgPath = imgPath_input;	
		xmlPath = xmlPath_input;
		outputPath = outputPath_input;
		stitchingChannel = Integer.valueOf(stitchingChannel_input);
		nChannels = Integer.valueOf(nChannels_input); 
		nPlanes = Integer.valueOf(nPlanes_input);
		doFFC = Boolean.parseBoolean(doFFC_input);
		
		/*
		try {
			FileUtils.writeStringToFile(new File(outputPath + "test.txt"), "Hello File"); //Debug test
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		*/
		
		String status = setupVariablesAndStart();
		
		return "done";
	}
	
	// For debugging from Eclipse IDE:
	public static void main(String[] args) {
		//new ij.ImageJ(); //Enable to show the ImageJ GUI during debugging from Eclipse
		IJ.log("Started from IDE");
		
		String status = setupVariablesAndStart();
		//return status; //Not possible when run from here
		}

	
	/*
	 * All three entry points converge on this method:
	 */

	public static String setupVariablesAndStart() {
		if (startedFromMacro == false) {
			//Set global values:
			/*
			imgPath = "/home/anders/filespin-bioformats-fiji-experiments/Operetta-StitchTilesPlugin/test-fixtures/SARP-Alexa-Images/";	
			xmlPath = imgPath + "Index_orig.idx.xml"; //TODO: not hardcoded name -> find xml
			outputPath = "/home/anders/OUTPUT_Operetta_StitchTiles/";
			*/
			//imgPath = "/home/anders/filespin-bioformats-fiji-experiments/Operetta-StitchTilesPlugin/test-fixtures/SARP-Alexa-Images/";
			imgPath = "C:\\CODE REPOS\\UpWork\\New_repo_Stitch_tiles\\filespin-bioformats-fiji-experiments\\Operetta-StitchTilesPlugin\\test-fixtures\\incorrect-stitch-107-fields\\";
			xmlPath = imgPath + "Index.idx.xml"; //TODO: not hardcoded name -> find xml
			outputPath = "C:\\CODE REPOS\\UpWork\\New_repo_Stitch_tiles\\OUTPUT\\";

			doFFC = false;
			stitchingChannel = 1;
			nChannels = 4; 
			nPlanes = 3;
			
		}

		File file = new File(imgPath);
		if (!file.isDirectory()) {
			IJ.log("ERROR: Input path not valid: " + imgPath);
			return "ERROR: Input path not valid: " + imgPath;
		}
		
		if (customListFiles(imgPath, ".tiff", false).length < 1) {
			IJ.log("ERROR: No images in path: " + imgPath);
			return "ERROR: No images in path: " + imgPath;		
		}
		
		file = new File(xmlPath);
		if (!file.isFile()) {
			IJ.log("ERROR: xmlPath not valid: " + xmlPath);
			return "ERROR: xmlPath not valid: " + xmlPath;
		}
		
		IJ.log("doFCC: " + Boolean.toString(doFFC));
		
		doProcessing();
		return "Done setupVariablesAndStart";
	}
	
	
	
	
	
	
	
	/*
	 * IMAGE PROCESSING CODE UNDER HERE
	 */
	
	//Start separate thread to speed up processing
	public static void doProcessing() {
	Thread t = new Thread(new Runnable() {
		@Override
		public void run() {
			//doProcessing_2: Creates outputs: 
			//Z projections images in separate channel folders, 
			//and TileConfigurations.txt in the stitching channel folder.
			//Does FFC if enabled as option
			doProcessing_2(); 
			
			
			/*After doProcessing_2:
			 *Do stitching on the stitching channel first - use resulting coords (text file) to stitch (without analysis) the other channels: 
			 */
			
			String fn = outputPath + "/TileConfiguration.txt";
			
			//run stitching plugin on stitching channel - This also outputs a coord file with the computed new coords (TileConfiguration.registered.txt)
			IJ.run("Grid/Collection stitching", "type=[Positions from file] order=[Defined by TileConfiguration] directory=[" + outputPath + String.valueOf(stitchingChannel) + "] layout_file=TileConfiguration.txt fusion_method=[Linear Blending] regression_threshold=0.30 max/avg_displacement_threshold=2.50 absolute_displacement_threshold=3.50 compute_overlap computation_parameters=[Save computation time (but use more RAM)] image_output=[Write to disk] output_directory=[" + outputPath + "]");
			
			//Rename output stitched image file
			boolean success = new File(outputPath + "img_t1_z1_c1").renameTo(new File(outputPath + "channel_" + String.valueOf(stitchingChannel) + ".tiff"));
			
			//Copy resulting coord file to the other channels, and run non-computational stitching:
        	try {
        		for (int i = 1; i <= nChannels; i++) {
        			if (i != stitchingChannel) {
        				String chn = String.valueOf(i);
            			Files.copy(Paths.get(outputPath + String.valueOf(stitchingChannel) + "/TileConfiguration.registered.txt"), Paths.get(outputPath + chn + "/TileConfiguration.txt"), StandardCopyOption.REPLACE_EXISTING);
            			//I removed "compute_overlap" argument below, so stitching channel result are used for all other channels (important for consisten stitching across channels.
            			IJ.run("Grid/Collection stitching", "type=[Positions from file] order=[Defined by TileConfiguration] directory=[" + outputPath + chn + "] layout_file=TileConfiguration.txt fusion_method=[Linear Blending] regression_threshold=0.30 max/avg_displacement_threshold=2.50 absolute_displacement_threshold=3.50 computation_parameters=[Save computation time (but use more RAM)] image_output=[Write to disk] output_directory=[" + outputPath + "]");
            			//Rename output stitched image file
            			success = new File(outputPath + "img_t1_z1_c1").renameTo(new File(outputPath + "channel_" + chn + ".tiff"));
        			}	
        		}
        	} catch (IOException e) {
				e.printStackTrace();
			}
        	//Combine all channels into a stack, and save
        	Opener ijOpener = new Opener();
        	
        	ImagePlus[] resultImages = new ImagePlus[nChannels];
    		for (int i = 1; i <= nChannels; i++) {
    			String chn = String.valueOf(i);
    			resultImages[i - 1] = ijOpener.openImage(outputPath + "channel_" + chn + ".tiff"); //open from disk
    		}
    		ImagePlus imp = ImagesToStack.run(resultImages);
    		IJ.run(imp, "Properties...", "channels=" + String.valueOf(nChannels) + " slices=1");
    		FileSaver ijSaver = new FileSaver(imp);
			ijSaver.saveAsTiff(outputPath + "Final_Result.tiff"); 
		}
	});
	t.start();
	}

	
	
	public static void doProcessing_2() {
		try {
		Opener ijOpener = new Opener();
		int logCounter = 0;
		
		IJ.log("PROCESSING START...");
		IJ.log("Folder input = " + imgPath);

		//Create x channel folders (above imgPath) and copy the files into appropriate folder
		for (int i = 1; i <= nChannels; i++) {
			String chn = String.valueOf(i);
			File[] files = customListFiles(imgPath, ".tiff", false);
			String currentChannelPath = null;
			
			for(File f:files){
	        	
				if (f.getName().contains("-ch" + chn)) {	//MATCH FILENAME WITH CHANNEL NAME
					//Get source, destination paths
					currentChannelPath = outputPath + "/" + chn + "/";
					String fileDestination = currentChannelPath + f.getName();
					File tmp = new File(fileDestination);
					//Make the target dir if not exist
					tmp.mkdirs(); 
		        	//Copy files:
		        	try {
						Files.copy(Paths.get(f.getCanonicalPath()), Paths.get(fileDestination), StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
	        }
			if (currentChannelPath == null) {
				System.out.println("[ERROR] currentChannelPath == null. Is the nChannels parameter set correctly? ");
				System.err.println("[ERROR] currentChannelPath == null. Is the nChannels parameter set correctly? ");
}
			IJ.log("Copy for channel " + chn + " is complete. Starting Z-projection...");
			//Copy for one channel is complete. Do Z-projection and rename:
			File[] filesOfOneChannel = customListFiles(currentChannelPath, ".tiff", true); //true = do custom sort, since we're renaming based on order of array
			logCounter = 0;
			int saveNameCounter = 0;
			for(File f:filesOfOneChannel){
				logCounter++;
				IJ.log("Z-projecting: file " + logCounter + " of " + filesOfOneChannel.length + ", channel " + chn + " of " + nChannels);
				System.out.println("Z-projecting: file " + logCounter + " of " + filesOfOneChannel.length + ", channel " + chn + " of " + nChannels);
				
				
				if (f.getName().contains("p01")) {	//MATCH FILENAME WITH PLANE NAME. We start the processing each time we find plane=1
					saveNameCounter++;
					String[] sameField = new String[nPlanes];	
					//Fill sameField with paths to all plane images
					for (int p = 1; p <= nPlanes; p++) {
						String pp = String.valueOf(p);
						String zeroPadded = "p" + ("00" + pp).substring(pp.length());
						sameField[p - 1] = f.getCanonicalFile().toString().replace("p01", zeroPadded);
					}
					//Initialize a ImageJ stack from the top image (p=1):
					ImagePlus topImage = ijOpener.openImage(sameField[0]); //open from disk
					ImageStack ijStack = new ImageStack(topImage.getWidth(), topImage.getHeight()); //initialize stack dimensions
					ijStack.addSlice(topImage.getProcessor()); //add top image to stack
					for (int p = 1; p < nPlanes; p++) { //p starts at 1, so we ignore Topimage, but add the remaining images
						ImagePlus tmp = ijOpener.openImage(sameField[p]);
						ijStack.addSlice(tmp.getProcessor());
					}
					//Do Z-projection on the stack
					ImagePlus tmpImage = new ImagePlus("tmp", ijStack);
					ZProjector projector = new ZProjector();
					ImagePlus projectionResult = projector.run(tmpImage, "max");
					
					
					//Save the result as tiff and jpg
					FileSaver ijSaver = new FileSaver(projectionResult);
					//String outputName = f.getName().replace(".tiff", "_MAX_ZPROJ.tiff"); //For using image filenames instead
					//ijSaver.saveAsTiff(currentChannelPath + outputName); //For using image filenames instead
					ijSaver.saveAsTiff(currentChannelPath + String.valueOf(saveNameCounter) + ".tiff"); //For using image filenames instead
					new File(currentChannelPath + "/jpg/").mkdir();
					ijSaver.saveAsJpeg(currentChannelPath + "/jpg/" + String.valueOf(saveNameCounter) + ".jpg"); //For using image filenames instead

					
					//Delete individual z-plane files (not original, only those copied)
					for (String planeFile : sameField ) {
						Files.delete(Paths.get(planeFile));
					}
				}	
			}
			IJ.log("Completed z-projection for channel" + chn);
			
			//Optimization - embed the FFC in the previous processing step
			if (doFFC) {
				IJ.log("Doing Flat field correction for channel " + chn);
				System.out.println("Doing FFC for channel " + chn);
				//call Octave/Matlab -  creates flatfield.tif in currentChannelPath
				Runtime r = Runtime.getRuntime();
				Process p;
				try {
					String command = "octave ~/filespin-bioformats-fiji-experiments/matlab_basic/make_flatfield.m";
					String argument = currentChannelPath;
					p = r.exec(command + " " + argument);
					p.waitFor();
					BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
					BufferedReader bb = new BufferedReader(new InputStreamReader(p.getErrorStream()));
					String line = "";
					while ((line = b.readLine()) != null) {
					  System.out.println(line);
					}
					while ((line = bb.readLine()) != null) {
						  System.out.println(line);
						}
					b.close();
					bb.close();
				} 
				catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				//Use the generated flat field image to correct all images.
				ImagePlus flatfield = IJ.openImage(currentChannelPath + "flatfield.txt");
				
				File[] nonCorrectedFiles = customListFiles(currentChannelPath, ".tiff", false);
				for(File f:nonCorrectedFiles){
					if (f.getName().contains("flatfield")) {
							continue;
					}
					ImagePlus nonCorrectedField = ijOpener.openImage(f.getCanonicalPath());
					Calibration calibration = nonCorrectedField.getCalibration();
					//Do correction with image calculator
					ImageCalculator ic = new ImageCalculator();
					ImagePlus correctedField = ic.run("Divide create", nonCorrectedField, flatfield);
					correctedField.setCalibration(calibration);
					
					//Save the result as tiff and jpg
					FileSaver ijSaver = new FileSaver(correctedField);
					String filenameNoExtension = correctedField.getShortTitle().replace("Resultof",  "");
					ijSaver.saveAsTiff(currentChannelPath + filenameNoExtension + ".tiff"); //For using image filenames instead
					new File(currentChannelPath + "/jpg/").mkdir();
					ijSaver.saveAsJpeg(currentChannelPath + "/jpg/" + filenameNoExtension + ".jpg"); //For using image filenames instead
					}
				//Delete flatfield image
				File f = new File(currentChannelPath + "flatfield.txt");
				f.delete();
				//Close all open imgs
				WindowManager.closeAllWindows();
				IJ.log("DONE Doing Flat field correction for channel " + chn);
				System.out.println("DONE Doing FFC for channel " + chn);
			}
			//FFC DONE!
			
		if (Integer.valueOf(chn) == stitchingChannel) {
			IJ.log("Channel " + chn + " is the stitching channel. Doing stitching, and generating resulting stitching coords for use in the other channels.");
			createPositionFile(chn);
		}
			
			
		}
		IJ.log("DONE ALL PROCCESSING!");
	} catch (IOException e) {
		e.printStackTrace();
	}
}

	
	public static void createPositionFile(String chn) {
		StringBuilder sb = new StringBuilder(); //For the TileConfigurations file
		sb.append("dim = 2\n"); //sb.append("dim = 2" + System.getProperty("line.separator"));
		
        File id = new File( xmlPath);

        OperettaManager op = new OperettaManager.Builder( )
                .setId( id )
                .doProjection( true )
                .setSaveFolder( new File( "Ignore_me_Delete_me" ) )
                .build( );

        int count = op.getMetadata().getImageCount();
        System.out.println(count);
        
        Well well = op.getAvailableWells().get(0);
        
        // Get the positions for each field (called a sample by BioFormats) in this well
        List<WellSample> fields = well.copyWellSampleList( );

        Point topleft = op.getTopLeftCoordinates( fields );
        int downscale = 1;
        
        // Out of these coordinates, keep only those that are intersecting with the bounds
        Roi bounds = null;
        final List<WellSample> adjusted_fields = op.getIntersectingFields( fields, bounds );


        adjusted_fields.stream( ).forEachOrdered( field -> {
            // sample subregion should give the ROI coordinates for the current sample that we want to read
            Roi subregion = op.getFieldSubregion( field, bounds, topleft );

            final Point pos = op.getFieldAdjustedCoordinates( field, bounds, subregion, topleft, downscale );
            /*
            System.out.println(String.format("Sample Position: %d, %d", pos.x, pos.y));
            System.out.println(field.getID());
            System.out.println(field.getIndex());
            System.out.println(field.getLinkedImage());
            System.out.println(imageFiles[field.getIndex().getValue()]);
            System.out.println(" ");
            */
            
            //String imgFileName = imageFiles[field.getIndex().getValue()].getName(); //For using image filenames instead
            String imgFileName = String.valueOf(field.getIndex().getValue() + 1) + ".tiff";
            String posX = String.valueOf(pos.x);
            String posY = String.valueOf(pos.y);
            
            
			sb.append(imgFileName + "; ; (" + posX + ", " + posY + ")\n"); //sb.append(imgFileName + "; ; (" + posX + ", " + posY + ")" + System.getProperty("line.separator"));
        });
        
        
        try {
			Files.write(Paths.get(outputPath + stitchingChannel + "/TileConfiguration.txt"), sb.toString().getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	

	
	//Method to list files in a path	
	public static File[] customListFiles(String path, String extension, Boolean doCustomSort) {
		File file = new File(path);
	    File[] files = file.listFiles(new FilenameFilter() {
	        @Override
	        public boolean accept(File dir, String name) {
	            if(name.toLowerCase().endsWith(extension)){
	                return true;
	            } else {
	                return false;
	            }
	        }
	    });
	    
	    if (!doCustomSort) {
	    	Arrays.sort(files);
	    } else {
	    	//From calling line: "Copy for one channel is complete. Do Z-projection and rename".
	    	//I.E. we have to sort to fix this problem::
	    	/*
	    	 * It's the soring function. It sorts fils like this: 5,6,7,8,9,10,101,102,103,104,105,106,107,11,12,13,14
					Or something similar
				Then later, the jave code goes though that sorted list of files and renames them accordingly: 5.tiff,6.tiff,7.tiff,8.tiff,9.tiff,10.tiff,11.tiff,12.tiff,13.tiff
	    	 */
	
		    int nFields =  files.length / nPlanes;
		    //System.out.println(nFields);
		    //System.out.println("nFields");
		    
		    
		    File[] customSorted = new File[files.length];
		    //Match filename by field, in ascending order:
		    int matchCounter = 0;
		    for (int field = 1; field <= nFields; field++) {
		    	String fieldString;
		    	if (field < 10) 
		    		{fieldString = "f0" + Integer.toString(field) + "p"; } 
		    	else 
		    		{fieldString = "f" + Integer.toString(field) + "p"; }
		    	
		    	
		    	//Match filename by plane, in ascending order:
		    	for (int plane = 1; plane <= nPlanes; plane++) {
			    	String planeString;
			    	if (plane < 10) 
			    		{planeString = "p0" + Integer.toString(plane) + "-c"; } 
			    	else 
			    		{planeString = "p" + Integer.toString(plane)+ "-c"; }
			    	
			    	
		    		
			    	//Check all the files and find a match with current field/planes values
			    	for (int i = 0; i < files.length; i++) {
			    		String filename = files[i].getName();
			    		if (filename.contains(fieldString) && filename.contains(planeString)) {
			    			/*
			    			System.out.println(files[i]);
			    			System.out.println(fieldString);
			    			System.out.println(planeString);
			    			System.out.println(i);
			    			System.out.println(matchCounter);
			    			System.out.println("**********");
			    			System.out.println(" ");
			    			*/
			    			customSorted[matchCounter] = files[i];
			    			matchCounter++;
			    		}
		    		
			    	}
		    	}
		    }
		    files = customSorted;
		 }

	    return files;
	}
	
	
	
}














