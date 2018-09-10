package com.praksa.endrina.barcodescener;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.JsonReader;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;


public class MainActivity extends AppCompatActivity {
    ArrayList<String> lista = new ArrayList<>();
    SharedPreferences preferences;
    SharedPreferences.Editor editor;
    Set<String> set = new HashSet<String>(F);
    Integer broj = 0;
    ListViewAdapter adapter;
    ListView listView;
    public static final String storage = "storage";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listView = (ListView) findViewById(R.id.listaLayout);
        adapter = new ListViewAdapter(this, lista);
        listView.setAdapter(adapter);
        preferences = getSharedPreferences(storage, Context.MODE_PRIVATE);
        set = preferences.getStringSet("lista", null);
        if (set != null && set.size() > 0) {
            lista.addAll(set);
            adapter.notifyDataSetChanged();
        }




        //TODO u klasi MultiTrackerActivity treba pronaći metodu koja procesuira barkod, u toj klasi definirati novu listu kao ArrayList i puniti ju sa skeniranim podacima.
        //Kada se klikne back, tu napunjenu listu sa skeniranim barkodovima poslati u ovaj activity i prikazati podatke na ekran

    }


    public void zapocniSkeniranje(View v) {
        Intent intent= new Intent(MainActivity.this,MultiTrackerActivity.class);
       startActivity(intent);

      /*  lista.add("kliknuto puta: "+broj);
        broj++;
        adapter.notifyDataSetChanged();
*/




    };

    @Override
    protected void onRestart() {
        super.onRestart();
        set = preferences.getStringSet("lista", null);
        if (set != null && set.size() > 0) {
            lista.addAll(set);
            adapter.notifyDataSetChanged();
        }
    }

}