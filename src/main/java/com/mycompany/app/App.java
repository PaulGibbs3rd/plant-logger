package com.mycompany.app;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;

import com.esri.arcgisruntime.location.NmeaLocationDataSource;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.ArcGISFeature;
import com.esri.arcgisruntime.data.FeatureTable;
import com.esri.arcgisruntime.data.ServiceFeatureTable;

import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.FeatureEditResult;
import com.esri.arcgisruntime.data.FeatureQueryResult;
import com.esri.arcgisruntime.data.QueryParameters;
import com.esri.arcgisruntime.data.Field;
import com.esri.arcgisruntime.data.Attachment;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.SpatialReference;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.control.Button;

import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Calendar;
import java.io.IOException;


public class App extends Application {

    // TextArea to display output in the GUI
    private TextArea textArea;  

    // I2C addresses and bases for the seesaw device
    private static final int SEESAW_STATUS_BASE = 0x00;
    private static final int SEESAW_STATUS_TEMP = 0x04;
    private static final int SEESAW_TOUCH_BASE = 0x0F;
    private static final int SEESAW_TOUCH_CHANNEL_OFFSET = 0x10;
    //I2C for GPS Address
    private static final int GPS_I2C_ADDRESS = 0x10;
    
    //record counter
    private int counter = 0;
    private boolean isCapturing = false; // Add this flag to indicate if capturing is ongoing
    
    //vars for sensor readings
    private int capread;
    private float tempC;
    NmeaLocationDataSource nmeaLocationDataSource;
    //vars for feature publishing
    private int wkid;
    private double pointX;
    private double pointY;
    private double pointZ;
    private Date datePub;
    private Date nextDatePub;
    private String img;
    private String imgPath;
    
    private Feature selected;

    private Timer loggingTimer; // timer for logging data to the feature service
    private ServiceFeatureTable reportTable;
    private ServiceFeatureTable pointTable;

    public static void main(String[] args) {
        launch(args);  // Launch the JavaFX application
    }

    public boolean begin(I2CDevice device) throws IOException {
        // Check if device is reachable
        return device.getAddress() == 0x36;
    }

    public float getTemp(I2CDevice device) throws IOException {
        try {
            // Prepare to read from the temperature register
            byte[] regAddress = {(byte) SEESAW_STATUS_BASE, (byte) SEESAW_STATUS_TEMP};
            device.write(regAddress, 0, 2);

            // Wait before reading|
            Thread.sleep(10);

            // Read temperature data
            byte[] buf = new byte[4];
            device.read(buf, 0, 4);

            // Convert the read bytes to temperature
            int ret = ((buf[0] & 0xFF) << 24) | ((buf[1] & 0xFF) << 16) | ((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
            return (1.0f / (1 << 16)) * ret;

        } catch (InterruptedException e) {
            // Handle exceptions
            return -1.0f;
        }
    }

    public int touchRead(I2CDevice device, int pin) throws IOException {
        try {
            // Prepare the address to read touch from
            byte[] regAddress = {(byte) SEESAW_TOUCH_BASE, (byte) (SEESAW_TOUCH_CHANNEL_OFFSET + pin)};
            
            int ret = 65535;  // Initialize with an error value
            byte[] buf = new byte[2];

            // Retry mechanism for reliability
            for (int retry = 0; retry < 5; retry++) {
                device.write(regAddress, 0, 2);

                // Delay to wait for the device to process
                Thread.sleep(3 + retry);

                // Read touch data
                int bytesRead = device.read(buf, 0, 2);
                if (bytesRead == 2) {
                    // Convert the read bytes to touch value
                    ret = ((buf[0] & 0xFF) << 8) | (buf[1] & 0xFF);
                    break;
                }
            }
            return ret;

        } catch (InterruptedException e) {
            // Handle exceptions
            return -1;
        }
    }

    //When this runs a picture will be taken then we will call uploadRecordToFeatureTable();
    public void captureImageWithLibcamera() {
   
        if (isCapturing) {
            return; // Exit if a capture operation is ongoing
        }

        isCapturing = true; // Set the flag to true before starting capture

        Path tempDir = null;
        Process process = null;
        try {
            // Create temporary directory
            tempDir = Files.createTempDirectory("imageCapture");
            img = "captured_image" + counter + ".jpg";
            imgPath = tempDir.toString() + "/" + img;
            System.out.println("Image saved to " + imgPath);
            // Build the command string
            String cmd = "libcamera-still -o " + imgPath;
            
            // Execute the command
            process = Runtime.getRuntime().exec(cmd);
            process.waitFor();
            counter++;  // Increment counter for the next image

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
            // Optionally, re-interrupt the thread if needed
            Thread.currentThread().interrupt();
        } finally {
            isCapturing = false;  // Reset the flag
            
            uploadRecordToFeatureTable();
            
            if (tempDir != null) {
                // Delete temporary directory and its contents
                //deleteDirectory(tempDir.toFile());
            }
        }

    // Recursive method to delete directory and its contents
    public static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    //Runs after a picture is taken, attributes are collected and a record is created.
    private void uploadRecordToFeatureTable(){
        pointTable = new ServiceFeatureTable("https://services.arcgis.com/Wl7Y1m92PbjtJs5n/ArcGIS/rest/services/RPI1/FeatureServer/5");
        pointTable.loadAsync();
        pointTable.addDoneLoadingListener(()-> {
            if(pointTable.getLoadStatus() == LoadStatus.LOADED){
                
                SpatialReference spatialReference = SpatialReference.create(wkid);
                Point point = new Point(pointX, pointY, pointZ, spatialReference);
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(datePub);
                
                Map<String, Object> attributes = new HashMap<>();
                attributes.put("NAME", "Report" + counter);
                attributes.put("datePub",calendar);
                attributes.put("TEMP", tempC);
                attributes.put("CAPACITANCE", capread);
                //create the feature
                ArcGISFeature pointFeature = (ArcGISFeature)pointTable.createFeature(attributes, point);
                Path imagePath = Paths.get(imgPath);
                        //if image exists at that path
                        if (Files.exists(imagePath)) {
                            try{
                                //save image to imageBytes variable
                                byte[] imageBytes = Files.readAllBytes(imagePath);
                                System.out.println("Can edit:" + pointFeature.canEditAttachments());
                                ListenableFuture<Attachment> attachmentFuture = pointFeature.addAttachmentAsync(imageBytes, "image/jpg", imgPath);
                                attachmentFuture.addDoneListener(() ->{
                                    System.out.println("Attatchment on point");
                                });
                            }catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                //add point feature to the point table
                ListenableFuture<Void> addFuture = pointTable.addFeatureAsync(pointFeature);
                //when done adding the point to the table
                addFuture.addDoneListener(() -> {
                    //apply the edits to the point table 
                    ListenableFuture<List<FeatureEditResult>> applyEditsFuture = pointTable.applyEditsAsync();
                    //when the edits are done 
                    applyEditsFuture.addDoneListener(() -> {
                        try {
                            // Get the Object ID of the newly added feature
                            long objectId = applyEditsFuture.get().get(0).getObjectId();
                            System.out.println("OID: " + objectId);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                });
            }else {
                System.err.println("Error loading ServiceFeatureTable:");
            }
    });

    
    //----------------------------------------------------------------
    
    @Override
    public void start(Stage stage) throws Exception {
        textArea = new TextArea();  // Initialize TextArea
        textArea.setEditable(false);  // Disable editing
        Button captureButton = new Button("Capture Image");
        captureButton.setOnAction(event -> captureImageWithLibcamera());

        StackPane root = new StackPane();
        root.getChildren().addAll(textArea, captureButton); 

        Scene scene = new Scene(root, 600, 400);
        stage.setTitle("Moisture and Temperature");  // Set window title
        stage.setScene(scene);  // Set scene
        stage.show();  // Display the window

        // Initialize I2C bus and device
        I2CBus bus = I2CFactory.getInstance(I2CBus.BUS_1);
        //seesaw sensor
        I2CDevice device = bus.getDevice(0x36);
        //gps sensor
        I2CDevice gpsDevice = bus.getDevice(GPS_I2C_ADDRESS);
        // Check if the device is available
        if (!begin(device)) {
            textArea.appendText("ERROR! seesaw not found\n");
            return;
        }
        
                
        nmeaLocationDataSource = new NmeaLocationDataSource();
        // Add this outside the AnimationTimer, preferably right after starting nmeaLocationDataSource
        textArea.appendText("Run later now listening for changes on listener location\n");
        nmeaLocationDataSource.addLocationChangedListener(listener -> {
            // Run later to ensure it is on the JavaFX Application Thread
            
            javafx.application.Platform.runLater(() -> {
            textArea.appendText("Location Changed Event Triggered.\n");
            if (listener.getLocation() != null) {
                datePub = Calendar.getInstance().getTime();
                pointX = listener.getLocation().getPosition().getX();
                pointY = listener.getLocation().getPosition().getY();
                pointZ = listener.getLocation().getPosition().getZ();
                wkid = listener.getLocation().getPosition().getSpatialReference().getWkid();
                
                textArea.appendText("Time: " + datePub + "\n");
                //textArea.appendText("Velocity: " + listener.getLocation().getVelocity() + "\n");
                //textArea.appendText("Position: " + listener.getLocation().getPosition() + "\n");
                textArea.appendText("X: " + pointX + " Y: " + pointY + " Z: " + pointZ + " SR: " + wkid + "\n");
                textArea.appendText("Temperature: " + tempC + " \n");
                textArea.appendText("Capacitance Read: " + capread + "\n");
                 
                
            } else {
                textArea.appendText("Location object is null.\n");
            }
            
            });
        });

        var nmeaFuture = nmeaLocationDataSource.startAsync().get(); // wait for it to start
        textArea.appendText("NMEA Location Data Source started successfully.\n");
       


        // Animation timer to read and update values
        new AnimationTimer() {
            long lastUpdate = 0;
            long interval = 1000_000_000; // 1 second in nanoseconds
            byte[] buffer = new byte[128]; // Buffer to store NMEA data
            @Override
            public void handle(long now) {
                if(now - lastUpdate >= interval){ 
                  lastUpdate = now;  
                    try {
                        tempC = getTemp(device);
                        capread = touchRead(device, 0);
                        int capMax = 1020;
                        int bytesRead = gpsDevice.read(buffer, 0, buffer.length);
                        String nmeaSentence ="";
                        
                        if (bytesRead > 0) {
                            nmeaSentence = new String(buffer, 0, bytesRead);
                            nmeaLocationDataSource.pushData(buffer);                        
                        }
                        //if touching sensor and not already taking a picture  
                        if (capread > capMax && !isCapturing) {
                            captureImageWithLibcamera() ;  // Capture an image if capacitance > capMax
                        }
                    } catch (IOException e) {
                        // Handle I/O errors
                        textArea.appendText("Failed to read from device: " + e.getMessage() + "\n");
                    }
                }
            }
        }.start();

        
    }
}
