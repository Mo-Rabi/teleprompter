package com.arabicwithmo.floating;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;

import java.util.ArrayList;
import java.util.Collections;

public class MainActivity extends Activity {
    private ScriptCatalog catalog;
    private ArrayAdapter<ScriptCatalog.Script> adapter;
    private final ArrayList<ScriptCatalog.Script> choices = new ArrayList<>();
    private TextView status;
    private int awaitingPermissionScript = -1;

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView label(String value, float size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setPadding(dp(8), dp(8), dp(8), dp(8));
        return t;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13,17,23));
        getWindow().setNavigationBarColor(Color.rgb(13,17,23));
        catalog = new ScriptCatalog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(8));
        root.setBackgroundColor(Color.rgb(13,17,23));

        TextView heading = label("ArabicWithMo · Floating Teleprompter",22);
        heading.setTypeface(null,1);
        root.addView(heading);
        root.addView(label("Keep your script floating above the native Huawei Camera app. Move, resize, and scroll it without recording any text on the video.",14));
        status = label("",14);
        root.addView(status);
        Button permission = button("Allow display over other apps");
        permission.setOnClickListener(v -> requestOverlayPermission());
        root.addView(permission);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search all 111 scripts...");
        search.setHintTextColor(Color.LTGRAY);
        search.setTextColor(Color.WHITE);
        root.addView(search);

        ListView list = new ListView(this);
        list.setDividerHeight(dp(1));
        adapter = new ArrayAdapter<ScriptCatalog.Script>(this, android.R.layout.simple_list_item_1, choices) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView text = (TextView) super.getView(position, convertView, parent);
                text.setTextColor(Color.WHITE);
                text.setTextSize(16);
                text.setPadding(dp(12), dp(12), dp(12), dp(12));
                return text;
            }
        };
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
        list.setOnItemClickListener((parent,view,position,id) -> openOverlay(choices.get(position).id));

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { filter(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });
        filter("");

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER);
        Button camera = button("Open native camera");
        camera.setOnClickListener(v -> {
            try { startActivity(new Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)); }
            catch(Exception ex) { status.setText("Open your phone's Camera app manually. The floating script remains on top."); }
        });
        footer.addView(camera,new LinearLayout.LayoutParams(0,dp(55),1f));
        Button stop = button("Stop overlay");
        stop.setOnClickListener(v -> startService(new Intent(this,FloatingService.class).setAction(FloatingService.ACTION_STOP)));
        footer.addView(stop,new LinearLayout.LayoutParams(0,dp(55),1f));
        root.addView(footer);
        setContentView(root);

        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},100);
        }
        updatePermissionText();
    }

    private void filter(String query) {
        choices.clear();
        String q = query.trim().toLowerCase(java.util.Locale.ROOT);
        ArrayList<ScriptCatalog.Script> all = new ArrayList<>(catalog.all());
        Collections.reverse(all); // newest scripts first
        for(ScriptCatalog.Script script:all) {
            if(q.isEmpty() || script.title.toLowerCase(java.util.Locale.ROOT).contains(q)
                || script.text.toLowerCase(java.util.Locale.ROOT).contains(q)
                || String.valueOf(script.id).equals(q)) choices.add(script);
        }
        adapter.notifyDataSetChanged();
    }

    private void updatePermissionText() {
        if(status!=null) status.setText(Settings.canDrawOverlays(this)
            ? "✓ Overlay permission granted. Tap a script to float it over Camera."
            : "Step 1: Allow 'Display over other apps'. Then tap a script.");
    }

    private void requestOverlayPermission() {
        if(!Settings.canDrawOverlays(this)) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:"+getPackageName())));
            } catch(Exception ignored) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            }
        } else updatePermissionText();
    }

    private void openOverlay(int scriptId) {
        if(!Settings.canDrawOverlays(this)) {
            awaitingPermissionScript=scriptId;
            requestOverlayPermission();
            return;
        }
        getSharedPreferences("floating",MODE_PRIVATE).edit().putInt("script",scriptId).apply();
        Intent intent = new Intent(this,FloatingService.class);
        intent.setAction(FloatingService.ACTION_SHOW);
        intent.putExtra("script",scriptId);
        if(Build.VERSION.SDK_INT>=26) startForegroundService(intent);
        else startService(intent);
        status.setText("✓ Floating script #"+scriptId+" started. Open the native camera. Drag the bar to move it.");
    }

    @Override protected void onResume() {
        super.onResume();
        updatePermissionText();
        if(awaitingPermissionScript>0 && Settings.canDrawOverlays(this)) {
            int id=awaitingPermissionScript;
            awaitingPermissionScript=-1;
            openOverlay(id);
        }
    }
}
