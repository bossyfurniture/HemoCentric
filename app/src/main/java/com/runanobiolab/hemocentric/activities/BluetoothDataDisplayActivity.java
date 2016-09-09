package com.runanobiolab.hemocentric.activities;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


import com.androidplot.util.Redrawer;
import com.androidplot.xy.BarFormatter;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYStepMode;
import com.github.mikephil.charting.charts.LineChart;
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
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class BluetoothDataDisplayActivity extends ActionBarActivity {


    //private final String BLE_MAC_ADDRESS = "20:C3:8F:D5:35:06"; old BLE Module (broken)
    private final String BLE_MAC_ADDRESS = "74:DA:EA:B2:67:9A"; // HM-10
    //private final String BLE_MAC_ADDRESS = "00:07:80:D1:26:E3"; // ble112

    //UI ELEMENTS
    private EditText inputData;
    private TextView displayData;
    private Button sendData;
    private TextView pointsData;
    private TextView peakData;
    private Button analyzeData;
    private Button Graph;
    private Button historyBtn;
    private XYPlot mainPlot;
    private LineChart rtChart;

    //TESTING (temporary)
    private final String filename = "arddata_raw.txt";

    //for uiNewValueForCharacteristic
    private static StringBuilder dataStream_sb = new StringBuilder();
    private static String dataStream = "";
    private static boolean thresReached = false;
    private static int numPeaks = 0;
    private static int numPoints = 0;
    private static int const_threshold = DToR(.5);

    private static boolean dataIsSavedBeforeRestart = false;
    static boolean b = false;
    private static ArrayList<Number> XVals;
    private static ArrayList<Number> YVals;
    private static SimpleXYSeries xyseries1;
    private static LinkedBlockingQueue<Double> lbq;


    //BLE STUFF
    private BluetoothGattService service;
    private BluetoothGattCharacteristic characteristic;
    private BluetoothHelper helper;

    private static double[] doubleData;
    private static String storageDirectory;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_bluetooth_data_display);
        setContentView(R.layout.test_layout);

        /*
        //Text fields and buttons
        //inputData = (EditText)findViewById(R.id.input_data_field);
        //displayData = (TextView)findViewById(R.id.display_data_view);
        pointsData = (TextView)findViewById(R.id.display_points_view);
        sendData = (Button)findViewById(R.id.send_data_btn);
        peakData = (TextView)findViewById(R.id.display_peaks_view);
        analyzeData = (Button)findViewById(R.id.analyze_btn);
        Graph = (Button)findViewById(R.id.Graph);
        historyBtn = (Button)findViewById(R.id.history_btn);
        */

        //test layout
        pointsData = (TextView)findViewById(R.id.display_points_view);
        sendData = (Button)findViewById(R.id.Button_measure);
        peakData = (TextView)findViewById(R.id.TextView_peak_count);
        analyzeData = (Button)findViewById(R.id.analyze_btn);
        Graph = (Button)findViewById(R.id.Graph);
        historyBtn = (Button)findViewById(R.id.Button_history);
        rtChart = (LineChart)findViewById(R.id.LineChart_rtchart);


        //setting up graph parameters
        rtChart.setDescription("description");
        rtChart.setNoDataTextDescription("no data description");

        rtChart.setDragEnabled(true);
        rtChart.setScaleEnabled(false);
        rtChart.setDrawGridBackground(false);
        rtChart.setPinchZoom(false);
        rtChart.setTouchEnabled(true);

        rtChart.setBackgroundColor(Color.LTGRAY);
        LineData ld = new LineData();
        ld.setValueTextColor(Color.WHITE);


        rtChart.setData(ld);


        // checking for correct file storage setup
        //TODO: change to one central filename
        final File file_bytes = makeExternalFile(filename);
        if(file_bytes != null) {
            peakData.setText("Ext. Storage: " + file_bytes.toString());
        }else peakData.setText("No Ext. Storage. (necessary to function)");

        // making bluetooth helper
        helper = new BluetoothHelper(BluetoothDataDisplayActivity.this,new BleWrapperUiCallbacks.Null(){

            // Called whenever receives a packet notification from the BLE Module, right?
            // seems to run in a separate thread (not UI)
            @Override
            public void uiNewValueForCharacteristic(BluetoothGatt gatt,
                                                    BluetoothDevice device, BluetoothGattService service,
                                                    BluetoothGattCharacteristic ch, final String strValue, int intValue,
                                                    byte[] rawValue, String timestamp) {

                boolean b = Looper.myLooper() == Looper.getMainLooper();
                String byteString =  bytesToHex(rawValue);
                Log.e("BDDA", b + "Notification(" + rawValue.length + ") = [" + byteString + "]");

                // temporarily storing the points for later saving to text file
                dataStream_sb.append(byteString);

                Number[] packet = new Number[rawValue.length];

                b = (lbq == null);

                // real-time plotting & checking for peaks
                for(int i=0; i < rawValue.length; i++){
                    byte byt = rawValue[i];
                    int val = byt & 0xFF;

                    if(!b){
                        try{
                            lbq.put((double)val);
                        }catch(InterruptedException e){
                            Log.e("Bdda", "Interrupted!");
                        }
                    }


                    // checking whether peak
                    if(val > const_threshold && !thresReached){
                        thresReached = true;
                    }else if(val < const_threshold && thresReached){
                        thresReached = false;
                        numPeaks++;
                    }
                    numPoints++;

                }

                runOnUiThread(new Runnable(){
                    @Override
                    public void run(){
                        LineData data = rtChart.getData();

                        if (data != null) {

                            ILineDataSet set = data.getDataSetByIndex(0);
                            // set.addEntry(...); // can be called as well

                            if (set == null) {
                                set = createSet();
                                data.addDataSet(set);
                            }

                            if(lbq.size() > 50) {
                                updateSet(set);
                                data.notifyDataChanged();

                                // let the chart know it's data has changed
                                rtChart.notifyDataSetChanged();

                                // limit the number of visible entries
                                rtChart.setVisibleXRangeMaximum(120);
                                // rtChart.setVisibleYRange(30, AxisDependency.LEFT);

                                // move to the latest entry
                                rtChart.moveViewToX(data.getEntryCount() + 20);
                                rtChart.invalidate();
                            }
                        }else Log.e("BDDA", "Data null");

                        pointsData.setText("Num Points: " + numPoints);
                    }
                });

                Log.e("BDDA", "Peaks: " + numPeaks + "; Points: " + numPoints);
                Log.e("Bdda", "lbq size: " + lbq.size());

                //updating graph
                //feedMultiple(packet);

                //TODO: implement handler for this



                //updating graph
                /*
                if(b) {
                    mainPlot.setDomainBoundaries(numPoints - 50, numPoints + 50, BoundaryMode.FIXED);
                    //mainPlot.redraw();
                    Log.e("BDDA", "Graph Redrawn");
                }
                if(!b) b = true; else b=false;
                */



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

                    numPeaks = 0;
                    numPoints = 0;
                    lbq = new LinkedBlockingQueue<>();

                    /*
                    runOnUiThread(new Runnable(){
                        int counter = 0;
                        @Override
                        public void run(){
                            while(counter < 10) {
                                if (!lbq.isEmpty()) counter++;
                                else{
                                    try{
                                        double val = lbq.take();
                                        addEntry(val);

                                    }catch(InterruptedException e){
                                        //
                                    }

                                }
                            }
                        }
                    });*/

                    helper.writeDataToCharacteristic(characteristic, dataBytes);
                    Log.e("BDDA", "Character written to helper");

                    //start up graph things

                }else { //for testing, create data
                    createData();
                    Log.e("BDDA", "Note: USE BLE is off, making test file");

                }

            }
        });


        analyzeData.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){

                //temp
                /*
                mainPlot.setDomainBoundaries(numPoints - 50, numPoints + 50, BoundaryMode.FIXED);
                mainPlot.redraw();
                */

                double minPeakThreshold = .5;
                double peakFallRatio = .9;
                double[] data = null;
                boolean parse = true;
                boolean delete = true;


                data = parseData(file_bytes, parse, delete);
                if(data != null){
                    int numMaxes = newFindPeak(data, minPeakThreshold);
                    /* Old Find Peak
                    double[] smoothed;
                    smoothed = filterData(detrend(data));
                    smoothed = filterData(data);
                    boolean[] isPeak = findPeaks(smoothed, minPeakThreshold, peakFallRatio);
                    int numMaxes = countPeaks(isPeak);
                    */

                    peakData.setText("Num Peaks Found: " + numMaxes + "\nUsing " + data.length + " received points.");
                    doubleData = data;
                }else{
                    peakData.setText("No Data Found");

                    rtChart.invalidate();

                }

            }
        });


        //Open the Graph activity
        /*
        Graph.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                Intent intent = new Intent(BluetoothDataDisplayActivity.this, Graph.class);
                if(doubleData != null) {
                    intent.putExtra("all the data", doubleData);
                    startActivity(intent);
                }else{
                    //TODO: show the last data
                    Log.e("BDDA", "graph activity error: doubleData is null");
                }
            }
        });*/

        //Opens a list of available data sets
        //TODO: fix the external directory creation process (make ext. file)
        historyBtn.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                Intent intent = new Intent(BluetoothDataDisplayActivity.this, DataHistory.class);
                intent.putExtra("data_directory", storageDirectory);// will not use yet
                startActivity(intent);
            }
        });

    }

    private void updateSet(ILineDataSet set){
        for(int i=0; i<50; i++) {
            double d = 0;
            boolean b = false;
            try {
                d = lbq.take();
                b = true;
            } catch (InterruptedException e) {

            }
            if(b) set.addEntry(new Entry(set.getEntryCount(),(float)d));
        }

    }


    private LineDataSet createSet() {

        LineDataSet set = new LineDataSet(null, "Dynamic Data");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(4f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }


    private void addEntry(Double d){

        LineData data = rtChart.getData();

        if (data != null) {

            ILineDataSet set = data.getDataSetByIndex(0);
            // set.addEntry(...); // can be called as well

            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            //if(list == null) Log.e("BDDA", "LIST IS NULL");
            else data.addEntry(new Entry(set.getEntryCount(), d.floatValue()), 0);
            data.notifyDataChanged();

            // let the chart know it's data has changed
            rtChart.notifyDataSetChanged();

            // limit the number of visible entries
            rtChart.setVisibleXRangeMaximum(120);
            // rtChart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            rtChart.moveViewToX(data.getEntryCount()+20);

            // this automatically refreshes the chart (calls invalidate())
            // rtChart.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }
    }
    private void addEntry(Number[] list){

        LineData data = rtChart.getData();

        if (data != null) {

            ILineDataSet set = data.getDataSetByIndex(0);
            // set.addEntry(...); // can be called as well

            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            if(list == null) Log.e("BDDA", "LIST IS NULL");
            else
            for(Number val : list) {
                data.addEntry(new Entry(set.getEntryCount(), val.floatValue()), 0);
            }
            data.notifyDataChanged();

            // let the chart know it's data has changed
            rtChart.notifyDataSetChanged();

            // limit the number of visible entries
            rtChart.setVisibleXRangeMaximum(120);
            // rtChart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            rtChart.moveViewToX(data.getEntryCount()+20);

            // this automatically refreshes the chart (calls invalidate())
            // rtChart.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }
    }

    private Thread thread;
    private void feedMultiple(Number[] arr) {

        final Number[] list = arr;

        if (thread != null)
            thread.interrupt();

        final myRunnable runnable = new myRunnable(list) {

            /*
            @Override
            public void run() {
                addEntry(list);
            }
            */
        };

        thread = new Thread(new Runnable() {

            @Override
            public void run() {

                // Don't generate garbage runnables inside the loop.
                runOnUiThread(runnable);


                try {
                    //Thread.sleep(25);
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        });

        thread.start();
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


    // Lightweight but still a good estimate of peaks.
    // Triggers on the rising and falling edge to detect a peak.
    // Threshold can be set.
    public static int newFindPeak(double[] data, double threshold){
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

    // Converts the int received from the Arduino in to its corresponding double value
    // Raw To Double
    // Maps (0, 255) to (-5, 5)
    public static double RToD(int raw){ return (2.0*((double)raw*(5.0/256)) - 5.0); }
    public static int DToR(double dbl){ return ((int)Math.ceil((dbl + 5) * 256.0 / 5 / 2)); }

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




    /**
     * At the moment, none of the functions below are necessary, mainly due to
     * the current threshold method is sufficient for detecting beads. This may change in
     * the future if processing the data becomes necessary to aid in accurate detection.
     */

    /*
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

}

class myRunnable2 implements Runnable{
    private double param;

    public myRunnable2(double param){
        this.param = param;
    }

    @Override
    public void run(){}

}
class myRunnable implements Runnable{
    private Number[] param;

    public myRunnable(Number[] param){
        this.param = param;
    }

    @Override
    public void run(){}

}
