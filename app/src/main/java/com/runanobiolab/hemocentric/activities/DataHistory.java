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

import com.runanobiolab.hemocentric.R;

import java.io.File;
import java.util.ArrayList;

public class DataHistory extends AppCompatActivity {

    private static ArrayAdapter adapter;
    private static File filearr[];
    private static String filenames[];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_history);

        createAdapter();
        adapter = new ArrayAdapter<String>(this, R.layout.activity_listview, filenames); //fix
        ListView listView = (ListView) findViewById(R.id.mobile_list);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int selected, long id) {
                Log.e("BDDA", "Item Clicked: " + selected);
                // start the graph activity with the selected data
                Intent intent = new Intent(DataHistory.this, Graph2.class);
                double[] dataset = BluetoothDataDisplayActivity.parseData(filearr[selected], false, false);
                intent.putExtra("all the data", dataset);
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
        if(filearr != null) Log.e("BDDA", "Files found: " + filearr.length);
        else Log.e("BDDA", "No files found!");


    }













}
