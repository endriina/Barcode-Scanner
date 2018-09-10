package com.praksa.endrina.barcodescener;

import android.app.Activity;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class ListViewAdapter extends BaseAdapter {

    Activity context;
    ArrayList<String> barkodovi;



    public ListViewAdapter(Activity context, ArrayList<String> barkodovi) {
        super();
        this.context = context;
        this.barkodovi = barkodovi;

    }

    public int getCount() {
        // TODO Auto-generated method stub
        return barkodovi.size();
    }

    public Object getItem(int position) {
        // TODO Auto-generated method stub
        return null;
    }

    public long getItemId(int position) {
        // TODO Auto-generated method stub
        return 0;
    }

    private class ViewHolder {
        TextView txtViewBarkod;

        TextWatcher textWatcher;
    }

    public View getView(final int position, View convertView, ViewGroup parent) {
        // TODO Auto-generated method stub
        final ViewHolder holder;
        LayoutInflater inflater = context.getLayoutInflater();

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.lista_item, null);
            holder = new ViewHolder();
            holder.txtViewBarkod = (TextView) convertView.findViewById(R.id.barcode);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }




        holder.txtViewBarkod.setText(barkodovi.get(position));

        return convertView;


    }

}