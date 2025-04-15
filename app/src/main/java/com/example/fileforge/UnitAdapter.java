package com.example.fileforge;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class UnitAdapter extends ArrayAdapter<String> {

    private String[] shortNames;

    public UnitAdapter(Context context, int resource, String[] fullNames, String[] shortNames) {
        super(context, resource, fullNames);
        this.shortNames = shortNames;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView view = (TextView) super.getView(position, convertView, parent);
        view.setText(shortNames[position]);
        view.setTextSize(20);
        return view;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        TextView view = (TextView) super.getDropDownView(position, convertView, parent);
        view.setText(getItem(position));
        view.setTextSize(18);
        return view;
    }
}