package com.arabicwithmo.floating;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends Activity {
    private ScriptCatalog catalog;
    private ArrayAdapter<ScriptCatalog.Script> adapter;
    private final ArrayList<ScriptCatalog.Script> choices = new ArrayList<>();
    private TextView status;
    private Button showButton;
    private Button confirmButton;
    private Button homeButton;
    private int selectedScript;
    private int awaitingPermissionScript = -1;
    private boolean overlayCreated = false;
    private boolean confirmedVisible = false;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver overlayReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!FloatingService.ACTION_STATUS.equals(intent.getAction())) return;
            boolean created = intent.getBooleanExtra("created", false);
            String message = intent.getStringExtra("message");
            overlayCreated = created;
            confirmedVisible = false;
            if (confirmButton != null) confirmButton.setEnabled(created);
            if (homeButton != null) homeButton.setEnabled(false);
            if (status != null) {
                if (created) status.setText(
                    "Android created the floating script window. Check that you can see it ON THIS SCREEN, then tap step 3. " +
                    "If you cannot see it, enable Huawei Dropzone permission.");
                else status.setText("Floating script DID NOT start. " +
                    (message == null ? "Check overlay/Dropzone permission and retry." : message));
            }
        }
    };

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

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
        selectedScript = getSharedPreferences("floating", MODE_PRIVATE).getInt("script",75);
        catalog = new ScriptCatalog(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(8));
        root.setBackgroundColor(Color.rgb(13,17,23));

        TextView heading = label("ArabicWithMo · Floating Teleprompter",21);
        heading.setTypeface(null,1);
        root.addView(heading);
        root.addView(label("This is an Android overlay, NOT an in-app camera. First make the script float over this screen. Then go Home and open the native Huawei Camera app.",13));

        status=label("Step 1: Allow display over other apps, and Huawei Dropzone if available.",14);
        root.addView(status);
        Button permission=button("1. Overlay Permission Settings");
        permission.setOnClickListener(v -> requestOverlayPermission());
        root.addView(permission);

        showButton=button("2. SHOW FLOATING SCRIPT  #" + selectedScript);
        showButton.setOnClickListener(v -> openOverlay(selectedScript));
        root.addView(showButton);

        confirmButton=button("3. I CAN SEE THE FLOATING SCRIPT");
        confirmButton.setEnabled(false);
        confirmButton.setOnClickListener(v -> {
            if (!overlayCreated) {
                status.setText("No floating window reported yet. Tap SHOW FLOATING SCRIPT first.");
                return;
            }
            confirmedVisible=true;
            homeButton.setEnabled(true);
            status.setText("Great. Tap Go Home, then open the Huawei Camera icon yourself. " +
                "If the window disappears only inside Camera, Huawei/Android may be hiding overlays in that app.");
        });
        root.addView(confirmButton);

        homeButton=button("4. GO HOME → OPEN HUAWEI CAMERA");
        homeButton.setEnabled(false);
        homeButton.setOnClickListener(v -> {
            if (!confirmedVisible) {
                status.setText("First confirm the floating script is visible over this screen.");
                return;
            }
            Intent home=new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
        });
        root.addView(homeButton);

        EditText search=new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search 111 scripts or tap one to load it");
        search.setHintTextColor(Color.LTGRAY);
        search.setTextColor(Color.WHITE);
        root.addView(search);

        ListView list=new ListView(this);
        adapter=new ArrayAdapter<ScriptCatalog.Script>(this, android.R.layout.simple_list_item_1, choices) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView t=(TextView)super.getView(position,convertView,parent);
                t.setTextColor(Color.WHITE);
                t.setTextSize(16);
                t.setPadding(dp(12),dp(9),dp(12),dp(9));
                return t;
            }
        };
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
        list.setOnItemClickListener((parent,view,position,id) -> {
            selectedScript=choices.get(position).id;
            showButton.setText("2. SHOW FLOATING SCRIPT  #"+selectedScript);
            openOverlay(selectedScript);
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){filter(s.toString());}
            @Override public void afterTextChanged(Editable s){}
        });
        filter("");

        Button stop=button("STOP FLOATING WINDOW");
        stop.setOnClickListener(v -> {
            startService(new Intent(this,FloatingService.class).setAction(FloatingService.ACTION_STOP));
            overlayCreated=false;confirmedVisible=false;
            confirmButton.setEnabled(false);homeButton.setEnabled(false);
            status.setText("Stopped. Tap SHOW FLOATING SCRIPT to begin again.");
        });
        root.addView(stop);
        root.addView(label("Huawei tip: Settings → Apps → Permissions → Dropzone → allow Mo Floating Prompter. " +
            "If it vanishes when Camera opens, also allow the app to run in the background.",12));
        setContentView(root);
        if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},100);
        }
        updatePermissionText();
    }

    private void filter(String query) {
        choices.clear();
        String q=query.trim().toLowerCase(Locale.ROOT);
        ArrayList<ScriptCatalog.Script> all=new ArrayList<>(catalog.all());
        Collections.reverse(all);
        for(ScriptCatalog.Script script:all) {
            if(q.isEmpty() || script.title.toLowerCase(Locale.ROOT).contains(q)
                || script.text.toLowerCase(Locale.ROOT).contains(q)
                || String.valueOf(script.id).equals(q)) choices.add(script);
        }
        adapter.notifyDataSetChanged();
    }

    private void updatePermissionText() {
        if(status==null || overlayCreated) return;
        status.setText(Settings.canDrawOverlays(this)
            ? "Overlay permission granted. Next: tap SHOW FLOATING SCRIPT. " +
                "Huawei may also require Dropzone permission."
            : "Step 1: Grant Display over other apps permission, then tap SHOW FLOATING SCRIPT.");
    }

    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:"+getPackageName())));
            } catch(Exception ignored) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            }
        } else {
            status.setText("Android overlay permission is already granted. If the script is missing, check Huawei Dropzone.");
        }
    }

    private void openOverlay(int scriptId) {
        if (!Settings.canDrawOverlays(this)) {
            awaitingPermissionScript=scriptId;
            requestOverlayPermission();
            return;
        }
        overlayCreated=false;
        confirmedVisible=false;
        confirmButton.setEnabled(false);
        homeButton.setEnabled(false);
        status.setText("Starting system-wide floating script #"+scriptId+"…");
        getSharedPreferences("floating",MODE_PRIVATE).edit().putInt("script",scriptId).apply();
        Intent intent=new Intent(this,FloatingService.class);
        intent.setAction(FloatingService.ACTION_SHOW);
        intent.putExtra("script",scriptId);
        try {
            if (Build.VERSION.SDK_INT>=26) startForegroundService(intent);
            else startService(intent);
        } catch (RuntimeException ex) {
            status.setText("Overlay service could not start: "+
                ex.getClass().getSimpleName()+". Try Huawei Dropzone and background-app permissions.");
        }
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter=new IntentFilter(FloatingService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT>=33) {
            registerReceiver(overlayReceiver,filter,Context.RECEIVER_NOT_EXPORTED);
        } else registerReceiver(overlayReceiver,filter);
        receiverRegistered=true;
    }

    @Override protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(overlayReceiver);
            receiverRegistered=false;
        }
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        updatePermissionText();
        if(awaitingPermissionScript>0 && Settings.canDrawOverlays(this)) {
            int script=awaitingPermissionScript;
            awaitingPermissionScript=-1;
            openOverlay(script);
        }
    }
}
