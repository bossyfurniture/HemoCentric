package com.runanobiolab.hemocentric.activities;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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

public class Graph2 extends AppCompatActivity {

    private LineChart chart;
    private TextView points;
    private TextView peaks;
    private double[] graphData;
    private int pts = 0;
    private int pks = 0;
    private String filename = "File";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph2);

        chart = (LineChart)findViewById(R.id.LineChart_historychart);
        points = (TextView)findViewById(R.id.TextView_history_pts);
        peaks = (TextView)findViewById(R.id.TextView_history_pks);

        graphData = getIntent().getDoubleArrayExtra("all the data");
        pts = getIntent().getIntExtra("points", 0);
        pks = getIntent().getIntExtra("peaks", 0);
        points.setText("Points: " + pts);
        peaks.setText("Peaks: " + pks);
        
        filename = getIntent().getStringExtra("filename");
        filename = filename.substring(filename.lastIndexOf("/") + 1);



        if(graphData == null){
            chart.clear();
        }else{

            //graph param setup
            initChart();

            //adding data
            LineData data = chart.getData(); //getting all chart datasets
            if(data != null) {
                ILineDataSet set = data.getDataSetByIndex(0); //getting first data set
                if (set == null) { //first time, init dataset
                    set = createSet();
                    data.addDataSet(set);
                }

                // adding data points
                if(graphData.length > 50000) Log.e("BDDA", "Large Data Set (50k+)! Crash possible");
                for(double d : graphData) set.addEntry(new Entry(set.getEntryCount(),(float)d));

                data.notifyDataChanged();
                chart.notifyDataSetChanged();

                // show whole dataset, if not too many points
                // limit the number of visible entries
                //increasing lowers performance
                chart.setVisibleXRangeMaximum(3000);
                chart.moveViewToX(data.getEntryCount());

                //chart.invalidate(); //updates chart
            }else Log.e("Graph2", "Data was null");



        }

    }

    private void initChart(){
        chart.setDescription("Data Display: " + filename);
        chart.setNoDataTextDescription("Invalid File or Data Format");

        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        //chart.setScaleEnabled(false);
        chart.setDrawGridBackground(true);
        chart.setPinchZoom(true);
        chart.setScaleYEnabled(false);
        chart.setBackgroundColor(Color.LTGRAY);

        chart.getAxisLeft().setAxisMaxValue(5f);
        chart.getAxisLeft().setAxisMinValue(-5f);
        chart.getAxisRight().setAxisMaxValue(5f);
        chart.getAxisRight().setAxisMinValue(-5f);
        /*
        LimitLine ll = new LimitLine(.5f, "Peak Threshold");
        ll.setLineColor(Color.RED);
        ll.setLineWidth(1f);
        ll.setTextColor(Color.BLACK);
        ll.setTextSize(12f);
        chart.getAxisLeft().addLimitLine(ll);
        */

        LineData ld = new LineData();
        chart.setData(ld);

    }



    //create/change the appearance of the data set
    private LineDataSet createSet() {

        LineDataSet set = new LineDataSet(null, "[placeholder]");
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
