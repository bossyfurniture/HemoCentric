package com.runanobiolab.hemocentric.activities;

import android.content.Intent;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.androidplot.xy.*;
import com.runanobiolab.hemocentric.R;

import java.util.ArrayList;
import java.util.List;

public class Graph extends AppCompatActivity {
    //PLOTTING
    private static final int SAMPLE_HISTORY = 1000;
    private XYPlot plot;
    private SimpleXYSeries liadata;
    double[] doubleData;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (getIntent().hasExtra("all the data")) {
            doubleData= getIntent().getDoubleArrayExtra("all the data");
        } else {
            throw new IllegalArgumentException("Activity cannot find  extras " + "all the data");
        }
        plot = (XYPlot)findViewById(R.id.plot);
        plotSetup(plot);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml
        int id = item.getItemId();

        if(id==android.R.id.home){
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

    private void plotSetup(XYPlot plot){
        //plotting
        plot.centerOnRangeOrigin(0);
        liadata = new SimpleXYSeries("Sensor Data");
        //plot.setRangeBoundaries(-5, 5, BoundaryMode.FIXED);
        plot.setRangeBoundaries(-5.0, 5.0, BoundaryMode.FIXED);
        //plot.setDomainBoundaries(0, 11000, BoundaryMode.FIXED);
        plot.getGraphWidget().getDomainTickLabelPaint().setColor(Color.WHITE);
        plot.getGraphWidget().getRangeTickLabelPaint().setColor(Color.BLACK);
        plot.getGraphWidget().getRangeOriginLinePaint().setColor(Color.BLACK);
        plot.getGraphWidget().getRangeOriginTickLabelPaint().setColor(Color.BLACK);
        plot.getRangeLabelWidget().getLabelPaint().setColor(Color.BLACK);
        plot.addSeries(liadata, new LineAndPointFormatter(Color.rgb(100, 100, 200), null, null, null));
        plot.setDomainStepValue(11);
        plot.setRangeStep(XYStepMode.INCREMENT_BY_VAL, 1);
        //plot.setDomainLabel("Sample Index");
        plot.setRangeLabel("Voltage [V]");
        plot.getDomainLabelWidget().pack();
        plot.getRangeLabelWidget().pack();
        //plot(plot,doubleData);
    }


    private void plot(XYPlot plot, double[] doubleData){

        List<Double> notGood = new ArrayList<Double>(10000);
        for(double d : doubleData) notGood.add(d);
        SimpleXYSeries raw = new SimpleXYSeries(notGood, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Sensor Data");
        plot.addSeries(raw, new LineAndPointFormatter(Color.rgb(100, 100, 200), null, null, null));
        plot.redraw();
    }
}
