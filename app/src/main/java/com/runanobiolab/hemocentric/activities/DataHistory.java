package com.runanobiolab.hemocentric.activities;

import android.content.Intent;
import android.os.Environment;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.runanobiolab.hemocentric.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DataHistory extends AppCompatActivity {

    private static ArrayAdapter adapter;
    private static File filearr[];
    private static String filenames[];
    //private static LineChart summaryChart;
    private static BarChart summaryChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_history);

        //TODO: Set up graph! (maybe make not a line chart)
        //summaryChart = (LineChart) findViewById(R.id.LineChart_summary_statistics);
        summaryChart = (BarChart) findViewById(R.id.LineChart_summary_statistics);
        summaryChart.setNoDataText("Not implemented yet (measurement summary/statistics)");
        List<BarEntry> entries = new ArrayList<BarEntry>();
        int[] arr = new int[10];
        for(int i=0; i<arr.length; i++){
            arr[i] = (int) (Math.random() * 15 + Math.random() * 25);
            entries.add(new BarEntry(i + 1,(int) (Math.random() * 15 + Math.random() * 25)));
        }
        BarDataSet dataSet = new BarDataSet(entries, "Label"); // add entries to dataset
        BarData barData = new BarData(dataSet);
        summaryChart.setDescription("Sample History Statistics Summary(not implemented yet)");
        summaryChart.setData(barData);
        summaryChart.invalidate();


        createAdapter();
        adapter = new ArrayAdapter<String>(this, R.layout.activity_listview, filenames); //fix
        ListView listView = (ListView) findViewById(R.id.mobile_list);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int selected, long id) {
                Log.d("BDDA", "Item Clicked: " + selected);
                // start the graph activity with the selected data
                Intent intent = new Intent(DataHistory.this, Graph2.class);
                double[] dataset = BluetoothDataDisplayActivity.parseData(filearr[selected], false, false);
                intent.putExtra("filename", filearr[selected].toString());
                intent.putExtra("all the data", dataset);
                intent.putExtra("points", dataset.length);
                intent.putExtra("peaks", BluetoothDataDisplayActivity.getDataSetPeaks(filearr[selected]));
                startActivity(intent);
            }
        });
    }


    // populates the adapter with files in the folder.
    private static void createAdapter(){
        ArrayList list = new ArrayList<String>();
        String path = Environment.getExternalStorageDirectory().toString() + "/HemoCentric/"; //to be replaced
        File f = new File(path);
        filearr = f.listFiles();
        filenames = f.list();
        if(filearr != null) Log.d("BDDA", "Files found: " + filearr.length);
        else Log.e("BDDA", "No files found!");


    }













}
