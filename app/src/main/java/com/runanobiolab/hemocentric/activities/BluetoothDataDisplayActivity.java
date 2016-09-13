package com.runanobiolab.hemocentric.activities;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.runanobiolab.hemocentric.R;
import com.runanobiolab.hemocentric.bluetooth.BluetoothHelper;
import com.runanobiolab.hemocentric.bluetooth.interfaces.BleWrapperUiCallbacks;
import com.runanobiolab.hemocentric.bluetooth.utils.BleDefinedUUIDs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class BluetoothDataDisplayActivity extends ActionBarActivity {


    //private final String BLE_MAC_ADDRESS = "20:C3:8F:D5:35:06"; old BLE Module (broken)
    private final String BLE_MAC_ADDRESS = "74:DA:EA:B2:67:9A"; // HM-10
    //private final String BLE_MAC_ADDRESS = "00:07:80:D1:26:E3"; // ble112

    //UI ELEMENTS
    private Button sendData;
    private TextView pointsData;
    private TextView peakData;
    private Button Graph;
    private Button historyBtn;
    private Button saveMeasurement;
    private LineChart rtChart;

    //TESTING (temporary)
    private final String filename = "arddata_raw.txt";

    //internal data structures/variables
    private static StringBuilder dataStream_sb;// = new StringBuilder();
    private static ArrayList<Byte> dataStream; //possibly use instead of string builder
    private static int numPeaks = 0;
    private static int numPoints = 0;
    private static boolean thresReached = false;
    private static double const_threshold = .5; //voltage threshold
    private static boolean dataIsSavedBeforeRestart = false;
    private static LinkedBlockingQueue<Double> lbq;
    private static String storageDirectory;
    private static final int queueRefreshThreshold = 50;
    private static final int plotEveryXPoints = 1; //not implemented

    //BLE STUFF
    private BluetoothGattService service;
    private BluetoothGattCharacteristic characteristic;
    private BluetoothHelper helper;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_data_display);

        //mapping ui elements
        pointsData = (TextView)findViewById(R.id.TextView_point_count);
        sendData = (Button)findViewById(R.id.Button_measure);
        peakData = (TextView)findViewById(R.id.TextView_peak_count);
        Graph = (Button)findViewById(R.id.Graph);
        historyBtn = (Button)findViewById(R.id.Button_history);
        saveMeasurement = (Button)findViewById(R.id.Button_save_measure);

        //setting up graph & parameters
        rtChart = (LineChart)findViewById(R.id.LineChart_rtchart);
        rtChart.setDescription("Real-Time Data Display");
        rtChart.setNoDataTextDescription("No Data");

        rtChart.setTouchEnabled(true);
        rtChart.setDragEnabled(true);
        //rtChart.setScaleEnabled(false);
        rtChart.setPinchZoom(true);
        rtChart.setScaleYEnabled(false);
        rtChart.setDrawGridBackground(true);
        rtChart.setBackgroundColor(Color.LTGRAY);

        rtChart.getAxisLeft().setAxisMaxValue(5f);
        rtChart.getAxisLeft().setAxisMinValue(-5f);
        rtChart.getAxisRight().setAxisMaxValue(5f);
        rtChart.getAxisRight().setAxisMinValue(-5f);
        LimitLine ll = new LimitLine(.5f, "Peak Threshold");
        ll.setLineColor(Color.RED);
        ll.setLineWidth(1f);
        ll.setTextColor(Color.BLACK);
        ll.setTextSize(12f);
        rtChart.getAxisLeft().addLimitLine(ll);

        LineData ld = new LineData();
        rtChart.setData(ld);


        // checking for correct file storage setup
        //TODO: change to one central filename
        //TODO: change peakdata to something else or remove
        final File file_bytes = makeExternalFile(filename);
        if(file_bytes != null) {
            //peakData.setText("Ext. Storage: " + file_bytes.toString());
        }else Log.e("BDDA","No Ext. Storage!");//peakData.setText("No Ext. Storage. (necessary to function)");

        // making bluetooth helper
        helper = new BluetoothHelper(BluetoothDataDisplayActivity.this,new BleWrapperUiCallbacks.Null(){

            // Called whenever receives a packet notification from the BLE Module, right?
            // seems to run in a separate thread (not UI)
            @Override
            public void uiNewValueForCharacteristic(BluetoothGatt gatt,
                                                    BluetoothDevice device, BluetoothGattService service,
                                                    BluetoothGattCharacteristic ch, final String strValue, int intValue,
                                                    byte[] rawValue, String timestamp) {

                //A string representation of the hex bytes
                String byteString =  bytesToHex(rawValue);
                Log.d("BDDA", "Notification(" + rawValue.length + ") = [" + byteString + "]");

                // temporarily storing the points for later saving to text file
                if(dataStream_sb != null) dataStream_sb.append(byteString);


                // real-time plotting & checking for peaks
                boolean Q = (lbq == null);
                for(int i=0; i < rawValue.length; i++){
                    byte byt = rawValue[i];
                    double val = RToD(byt & 0xFF);

                    if(!Q){
                        try{
                            lbq.put(val);
                        }catch(InterruptedException e){
                            Log.e("BDDA_notif", "Interrupted!");
                        }
                    }else Log.e("BDDA_notif", "Q is null");


                    // checking whether peak
                    if(val > const_threshold && !thresReached) thresReached = true;
                    else if(val < const_threshold && thresReached){
                        thresReached = false;
                        numPeaks++;
                    }
                    numPoints++;

                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        LineData data = rtChart.getData(); //getting all chart datasets

                        if (data != null) {

                            ILineDataSet set = data.getDataSetByIndex(0); //getting first data set

                            if (set == null) { //first time, init dataset
                                set = createSet();
                                data.addDataSet(set);
                            }

                            if (lbq.size() > queueRefreshThreshold) { //threshold for updating graph
                                updateSet(set); //adds points to set

                                // let the chart know it's data has changed
                                data.notifyDataChanged();
                                rtChart.notifyDataSetChanged();

                                // limit the number of visible entries
                                //increasing lowers performance
                                rtChart.setVisibleXRangeMaximum(1000);

                                // move to the latest entry
                                rtChart.moveViewToX(data.getEntryCount());
                                //rtChart.invalidate(); //updates chart
                            }
                        } else Log.e("BDDA_notif", "Line Chart data null");

                        pointsData.setText("Num Points: " + numPoints);
                        peakData.setText("Num Peaks: " + numPeaks);
                    }
                }); //end runOnUiThread

                Log.d("BDDA", "Peaks: " + numPeaks + "; Points: " + numPoints+ "; QSize: " + lbq.size());

            }

            @Override
            public void uiGotNotification(BluetoothGatt gatt, BluetoothDevice device,
                                          BluetoothGattService service,
                                          BluetoothGattCharacteristic characteristic) {

                Log.d("BDDA", "Got notificiation");

            }

            @Override
            public void uiAvailableServices(BluetoothGatt gatt, BluetoothDevice device,
                                            List<BluetoothGattService> services) {

                Log.d("BDDA", "Got services");

                for(BluetoothGattService s : services){
                    if(s.getUuid().equals(BleDefinedUUIDs.Service.CUSTOM_SERVICE)){
                        service = s;
                        Log.d("BDDA", "Found our service");
                        helper.getCharacteristicsForService(s);
                    }
                }


            }
            @Override
            public void uiCharacteristicForService(BluetoothGatt gatt,
                                                   BluetoothDevice device, BluetoothGattService service,
                                                   List<BluetoothGattCharacteristic> chars) {


                Log.d("BDDA", "Got characteristics");

                for(BluetoothGattCharacteristic chara : chars){

                    if(chara.getUuid().equals(BleDefinedUUIDs.Characteristic.CUSTOM_CHARACTERISTIC)){

                        Log.d("BDDA", "Found our characteristic");

                        characteristic = chara;
                        helper.setNotificationForCharacteristic(chara,true);

                    }

                }

            }

            @Override
            public void uiDeviceConnected(BluetoothGatt gatt, BluetoothDevice device) {

                Log.d("BDDA", "device connected");
                Log.e("BDDA", "device connected");

                helper.getSupportedServices();

            }

            @Override
            public void uiDeviceDisconnected(BluetoothGatt gatt, BluetoothDevice device) {

                Log.d("BDDA", "device disconnected");

            }

            @Override
            public void uiSuccessfulWrite(BluetoothGatt gatt, BluetoothDevice device,
                                          BluetoothGattService service, BluetoothGattCharacteristic ch,
                                          String description) {

                Log.d("BDDA,", "success! " + description);

            }
            @Override
            public void uiFailedWrite(BluetoothGatt gatt, BluetoothDevice device,
                                      BluetoothGattService service, BluetoothGattCharacteristic ch,
                                      String description) {
                Log.d("BDDA,", "fail! " + description);

            }

        });

        helper.initialize();

        helper.connect(BLE_MAC_ADDRESS);




        //TODO: disable send for x seconds, also make it reset counters & save data if needed
        sendData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                boolean useBLE = true;
                if(useBLE) {
                    String data = "a"; //sent to the BLE module to start data collection

                    byte[] dataBytes = data.getBytes(); //{(byte)integer};

                    // initializing structures for data measurement
                    if(rtChart!= null && rtChart.getData()!=null)
                        rtChart.getData().clearValues(); //clear data
                    numPeaks = 0;
                    numPoints = 0;
                    lbq = new LinkedBlockingQueue<>();
                    dataStream_sb = new StringBuilder(20000); //TODO: need to save data

                    //sending something to BT to begin measurement
                    helper.writeDataToCharacteristic(characteristic, dataBytes);
                    Log.d("BDDA", "Sent notification to bluetooth");

                }else { //for testing, create some data (does not do anything)
                    createData();
                    Log.e("BDDA", "Note: USE BLE is off, making test file");

                }

            }
        });


        //Opens a list of available data sets
        //TODO: fix the external directory creation process (make ext. file)
        historyBtn.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                Intent intent = new Intent(BluetoothDataDisplayActivity.this, DataHistory.class);
                //intent.putExtra("data_directory", storageDirectory);// will not use yet
                startActivity(intent);
            }
        });

        saveMeasurement.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                //TODO: Make save data set
                /*
                Thread t;
                t = new Thread(new Runnable(){
                    @Override
                    public void run(){
                        saveToFile();
                    }
                });
                t.start();
        */
                saveToFile();
            }
        });




    }

    private void updateSet(ILineDataSet set){
        for(int i=0; i<queueRefreshThreshold; i++) {
            double d = 0;
            boolean b = false;
            try {
                d = lbq.take();
                b = true;
            } catch (InterruptedException e) {
                Log.e("BDDA_updateSet", "Interrupted");

            }
            if(b) set.addEntry(new Entry(set.getEntryCount(),(float)d));
        }

    }





    //create/change the appearance of the data set
    private LineDataSet createSet() {

        LineDataSet set = new LineDataSet(null, "Dynamic Data");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(ColorTemplate.getHoloBlue());
        //set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(1f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }

    public static File makeExternalFile(String filename) {
        File file;
        try {
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                storageDirectory = Environment.getExternalStorageDirectory().toString() + "/HemoCentric";
                File docsFolder = new File(storageDirectory);
                boolean isPresent = true;
                if (!docsFolder.exists()) {
                    isPresent = docsFolder.mkdir();
                }
                if (isPresent) {
                    file = new File(docsFolder.getAbsolutePath(),filename);
                } else {
                    Log.e("BDDA", "Cannot Create Directory "+ storageDirectory +".");
                    file = null;
                }
            } else {
                file = null;
                Log.e("BDDA", "Cannot Create File.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            file = null;
        }
        return file;
    }

    //TODO: try starting a thread that calls uiNewValueForCharacteristic
    public void createData() {
        BufferedWriter bw;
        File test_file = makeExternalFile("test_file");

        try {
            if(test_file != null) {
                bw = new BufferedWriter(new FileWriter(test_file, true), 1000);
                for (int i=0; i<1000; i++) {
                    bw.write((i%130) + "\n"); //converts each byte into an unsigned int (0-255)
                }
                bw.flush();
                bw.close();
            }

        }
        catch (Exception e){
            e.printStackTrace();
        }

    }



    /**
     * Converts the int received from the Arduino in to its corresponding double value
     * Raw To Double
     * Maps (0, 255) to (-5, 5)
     *
     */
    public static double RToD(int raw){ return (2.0*((double)raw*(5.0/256)) - 5.0); }
    public static int DToR(double dbl){ return ((int)Math.ceil((dbl + 5) * 256.0 / 5 / 2)); }


    public static void saveToFile(){
        if(dataStream_sb==null || numPoints==0 || dataStream_sb.toString().equals(""))
            return; //no data to save
        String s = dataStream_sb.toString();
        int pts = numPoints;
        int pks = numPeaks;
        if(pts != s.length()){
            pts = Math.min(pts, s.length()/2);
            if(pts > Integer.MAX_VALUE/2) pts = Integer.MAX_VALUE/2;
            Log.e("BDDA", "Number of points to save: " +  pts + "\n");
        }
        double[] darr = new double[pts];
        for(int i=0; i<pts; i++){
            char[] carr = {s.charAt(2*i), s.charAt(2*i + 1)}; //taking string 2B at a time
            int a = Integer.parseInt(new String(carr),16); //parsing as hex
            //if(i%100 == 0) Log.e("BDDA", i + ":" + a + "\n");
            if(a > 255 || a < 0) Log.e("BDDA", "out-of-range hex value produced");
            darr[i] = RToD(a); //converting raw int to double
        }

        //String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new java.util.Date());
        String timeStamp = new SimpleDateFormat("yyyyMMMdd-h:mma").format(new java.util.Date());
        File file_double = makeExternalFile(timeStamp + ".txt");
        BufferedWriter bw;

        try {
            bw = new BufferedWriter(new FileWriter(file_double, true),1000);
            bw.write("Data Points: " + pts + "\n");
            bw.write("Meas. Peaks: " + pks + "\n");
            bw.write("====[raw data below]====\n");

            for(double d : darr){
                bw.write(d + "\n");
            }
            bw.flush();
            bw.close();
        }catch (Exception e){
            e.printStackTrace();
        }finally { }


    }

    /**
     * This code works on a complete data-set.
     * It can parse the data from digital(int) -> voltage(double)
     * It returns the data in a double array, and also returns it for use in the app
     */
    public static double[] parseData(File f, boolean parse, boolean delete){
        // Reading Input, customize according to source
        //return null;

        if(f!= null && f.exists()) {
            // Reading Input
            FileInputStream fStream;
            BufferedReader bReader = null;
            int points = 0;
            ArrayList<Number> tempData = new ArrayList<Number>(10000);

            try{
                fStream = new FileInputStream(f);
                bReader = new BufferedReader(new InputStreamReader(fStream));

                bReader.readLine(); //tossing file header lines
                bReader.readLine();
                bReader.readLine();

                // Reading ints from file
                while(bReader.ready()){
                    if(parse) tempData.add(Integer.parseInt(bReader.readLine()));
                    else      tempData.add(Double.parseDouble(bReader.readLine()));
                    points++;
                }
            }catch(Exception e){
                System.out.println("Exception caught in first try/catch");
                System.out.println(e.getMessage());
            }finally{
                try{bReader.close();}
                catch(Exception ex){/*ignore*/}
            }

            // converting to the output double[]
            double[] data = new double[points];
            if(parse) { // 2.0*((double)tempData[i]*(5.0/256)) - 5.0;
                for (int i = 0; i < points; i++) data[i] = RToD(tempData.get(i).intValue());
            }else{
                for (int i = 0; i < points; i++) data[i] = tempData.get(i).doubleValue();
            }

            // saving it as a new double text file (and possibly deleting raw)
            if(parse){ // only need to do this if parsed from raw file
                String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new java.util.Date());
                File file_double = makeExternalFile(timeStamp + "(double).txt");
                BufferedWriter bw;

                try {
                    bw = new BufferedWriter(new FileWriter(file_double, true),1000);
                    for(double d : data){
                        bw.write(d + "\n");
                    }
                    bw.flush();
                    bw.close();
                }catch (Exception e){
                    e.printStackTrace();
                }finally {
                    if(delete) f.delete(); //delete the raw data file of int
                    else{
                        //TODO: change arddata_raw file to also have timestamp (for clarity)
                        //File rename = new File("");

                    }
                }
            }
            return data;
        }else{
            Log.e("BDDA", "File does not exist.");
            return null;
        }

    }

    public static int getDataSetPeaks(File f){
        if(f!= null && f.exists()) {
            // Reading Input
            FileInputStream fStream;
            BufferedReader bReader = null;

            try {
                fStream = new FileInputStream(f);
                bReader = new BufferedReader(new InputStreamReader(fStream));
                int pks = 0;

                bReader.readLine(); //tossing file header lines
                String s = bReader.readLine();
                s = s.substring(13);
                pks = Integer.parseInt(s,10);

                return pks;
            } catch(Exception e) {
                return 0;
            }
        }
        return 0;

    }


    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static int byteArrayToInt(byte[] b)
    {
        return   b[3] & 0xFF |
                (b[2] & 0xFF) << 8 |
                (b[1] & 0xFF) << 16 |
                (b[0] & 0xFF) << 24;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_bluetooth_data_display, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}


    /**
     * At the moment, none of the functions below are necessary, mainly due to
     * the current threshold method is sufficient for detecting beads. This may change in
     * the future if processing the data becomes necessary to aid in accurate detection.
     */


    /*
    // Lightweight but still a good estimate of peaks.
    // Triggers on the rising and falling edge to detect a peak.
    // Threshold can be set.
    public static int findPeaksByThreshold(double[] data, double threshold){
        boolean aboveThreshold = false;
        int peaksFound = 0;

        for(int i=0; i<data.length; i++){
            if(data[i] > threshold) aboveThreshold = true;
            else if(aboveThreshold && data[i]<threshold){
                peaksFound++;
                aboveThreshold = false;
            }
        }
        return peaksFound;
    }

    // Usage:
    //     double[] smoothedData = filterData(detrend(parseData(rawData)));
    //     boolean[] peakArray = findPeaks(smoothedData, peakThreshold, dropFactor=0);

    // This function is not necessary at the moment, as the analog LIA does
    // a good job of removing baseline drift.
    public static double[] detrend(double[] data){

        int points = data.length;
        double[] detrended = new double[points];

        // Removes trend from the dataset - helpful for peak-finding [(data) - (baseline)]
        // One-tenth the total sample size seems to be a good window size
        final int trendWindow = (int)(points/10);

        double sum = 0;
        for(int point = 0; point < points; point++){
            if(point < trendWindow){
                sum += data[point];
                detrended[point] = (data[point]) - (sum / (point+1));
            }else{
                sum = sum + data[point] - data[point - trendWindow];
                detrended[point] =(data[point]) - (sum / trendWindow);
            }
        }
        return detrended;
    }

    public static double[] filterData(double[] data){

        //Moving Sum smoothing (not the fastest way, but simple and without use of ext. libs)
        // n-point (equally weighted) causal moving average
        // y[n] = (s[n] + s[n-1] + ...)/n;
        // goes through twice, one pass for each number in sampleRates

        // These values can be tinkered with to change the characteristics of the smoothing
        // Lower number = high-freq smoothing; Higher number = low-freq smoothing
        // Values chosen depend on the sampling frequency of the Arduino (about 800-1000 Hz)
        final int[] sampleRates = {20};
        int passes = sampleRates.length;

        int points = data.length;
        double[] movingAvg = new double[points];
        double[] tempArray;
        tempArray = data.clone();

        for(int pass=0; pass<passes; pass++){

            if(pass > 0) tempArray = movingAvg.clone();

            double sum = 0;
            for(int point = 0; point < points; point++){
                if(point < sampleRates[pass]){
                    sum += tempArray[point];
                    movingAvg[point] = sum / (point+1);
                }else{
                    sum = sum + tempArray[point] - tempArray[point - sampleRates[pass]];
                    movingAvg[point] = sum / sampleRates[pass];
                }
            }
        }
        return movingAvg;
    }

    // overloaded function
    // This function uses the characteristic positive peak followed by negative peak signature of
    // an actual peak vs a noise spike, allowing for detection closer to the noise margin.
    public static boolean[] findPeaks(double[] data, double minPeakThreshold){ return findPeaks(data, minPeakThreshold, 0); }
    public static boolean[] findPeaks(double[] data, double minPeakThreshold, double peakFallRatio){
        int[] peak = {-1, -1}; // {max index (if not -1), min index (resume search there)}
        boolean[] maximums = new boolean[data.length];
        for(int i=0; i<data.length; i++){
            if(data[i] > minPeakThreshold){
                peak = checkForPeaks(data, i, peakFallRatio);
                if(peak[0] > -1){
                    maximums[peak[0]] = true;
                    i = peak[1];
                }
            }
        }
        return maximums;
    }

    private static int[] checkForPeaks(double[] data, int index, double peakFallRatio){
        double max = 0; double min = 0;
        int maxdex = 0; int mindex = 0;
        boolean foundMax = false;
        boolean foundMin = false;
        int counter = index;
        while(!foundMax && counter<data.length){
            if(data[counter] >= max) max = data[counter];
            else{
                foundMax = true;
                maxdex = counter;
                min = data[maxdex];
            }
            if(counter > index + 1000000){ System.out.println("Error: Could not find max."); break; }
            counter++;
        }
        while(!foundMin && counter<data.length){
            if(data[counter] <= min) min = data[counter];
            else{
                foundMin= true;
                mindex = counter;
                min = data[mindex];
            }
            if(counter > index + 1000000){ System.out.println("Error: Could not find min."); break; }
            counter++;
        }
        int[] result = {-1,-1};
        if(max-min > peakFallRatio*max){
            result[0] = maxdex;
            result[1] = mindex;
        }
        return result;
    }

    public static int countPeaks(boolean[] peaks){
        int numMaxes = 0;
        for(int i=0; i<peaks.length; i++){
            if(peaks[i]) numMaxes++;
        }
        return numMaxes;
    }
    */

