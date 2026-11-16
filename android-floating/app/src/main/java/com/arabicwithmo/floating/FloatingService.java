package com.arabicwithmo.floating;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class FloatingService extends Service {
    public static final String ACTION_SHOW="com.arabicwithmo.floating.SHOW";
    public static final String ACTION_STOP="com.arabicwithmo.floating.STOP";
    public static final String ACTION_STATUS="com.arabicwithmo.floating.OVERLAY_STATUS";
    private static final String CHANNEL="mo_floating_teleprompter";
    private static final int NOTIFICATION_ID=7611;

    private WindowManager manager;
    private WindowManager.LayoutParams params;
    private LinearLayout root;
    private TextView scriptText, titleText;
    private ScrollView scrolling;
    private Button playButton;
    private ScriptCatalog catalog;
    private SharedPreferences settings;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean playing=false;
    private float scrollFraction=0f;
    private int currentScript=1, fontSp=27;
    private float speedDp=42f;

    private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }
    private Button control(String text, Runnable click) {
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(13);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(4),0,dp(4),0);
        b.setMinimumWidth(dp(36));
        b.setMinWidth(dp(36));
        b.setMinimumHeight(dp(36));
        b.setMinHeight(dp(36));
        b.setBackground(background(0xFF252A35,9));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(39));
        lp.setMargins(dp(2),dp(1),dp(2),dp(1));
        b.setLayoutParams(lp);
        b.setOnClickListener(v->click.run());
        return b;
    }

    @Override public void onCreate() {
        super.onCreate();
        settings=getSharedPreferences("floating",MODE_PRIVATE);
        catalog=new ScriptCatalog(this);
        manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        currentScript=settings.getInt("script",75);
        fontSp=settings.getInt("font",27);
        speedDp=settings.getFloat("speed",42f);
    }

    private Notification makeNotification() {
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Floating teleprompter",NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending=PendingIntent.getActivity(this,12,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,FloatingService.class).setAction(ACTION_STOP);
        PendingIntent stopPending=PendingIntent.getService(this,13,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("ArabicWithMo Floating Teleprompter")
            .setContentText("Your script is floating over other apps")
            .setContentIntent(pending)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete,"Stop",stopPending)
            .build();
    }

    private void reportOverlayStatus(boolean created,String detail) {
        Intent event=new Intent(ACTION_STATUS);
        event.setPackage(getPackageName());
        event.putExtra("created",created);
        event.putExtra("message",detail);
        sendBroadcast(event);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && ACTION_STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if(!Settings.canDrawOverlays(this)) {
            reportOverlayStatus(false,"Display over other apps permission denied. Check Huawei Dropzone.");
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            Notification notification=makeNotification();
            if(Build.VERSION.SDK_INT>=34) {
                startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else startForeground(NOTIFICATION_ID,notification);
            int script=(intent==null)?settings.getInt("script",75):intent.getIntExtra("script",settings.getInt("script",75));
            if(root==null) showOverlay();
            selectScript(script);
            // WindowManager.addView returning successfully does not guarantee the
            // compositor actually displayed the overlay. Check attachment before
            // telling the user to open the native camera.
            final LinearLayout currentRoot=root;
            handler.postDelayed(()->{
                if(currentRoot!=null && currentRoot==root && currentRoot.isAttachedToWindow()){
                    reportOverlayStatus(true,"Android overlay attached.");
                }else{
                    reportOverlayStatus(false,"Floating window did not attach. Check Huawei Dropzone / background popup permissions.");
                    stopSelf();
                }
            },400);
        } catch(RuntimeException ex) {
            Log.e("MoFloatingPrompter","Failed to create system overlay",ex);
            reportOverlayStatus(false,ex.getClass().getSimpleName()+": "+String.valueOf(ex.getMessage()));
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void showOverlay() {
        DisplayMetrics display=getResources().getDisplayMetrics();
        int width=Math.min(display.widthPixels-dp(12),settings.getInt("width",dp(330)));
        int height=Math.min(display.heightPixels-dp(120),settings.getInt("height",dp(300)));
        params=new WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;
        params.x=Math.max(0,settings.getInt("x",dp(16)));
        params.y=Math.max(0,settings.getInt("y",dp(100)));

        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(5),dp(5),dp(5),dp(5));
        root.setBackground(background(0xEC11151D,16));
        root.setElevation(dp(10));

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        titleText=new TextView(this);
        titleText.setTextColor(0xFFFFB347);
        titleText.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        titleText.setTextSize(13);
        titleText.setText("⠿  Mo · Drag here");
        titleText.setSingleLine(true);
        titleText.setPadding(dp(10),dp(7),dp(6),dp(7));
        header.addView(titleText,new LinearLayout.LayoutParams(0,dp(41),1f));
        attachMove(titleText);
        header.addView(control("✕",()->stopSelf()));
        root.addView(header);

        LinearLayout buttons=new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.addView(control("◀",()->changeScript(-1)));
        playButton=control("▶",this::toggle);
        buttons.addView(playButton);
        buttons.addView(control("▶|",()->changeScript(1)));
        buttons.addView(control("A−",()->changeFont(-2)));
        buttons.addView(control("A+",()->changeFont(2)));
        buttons.addView(control("−",()->changeSpeed(-7)));
        buttons.addView(control("+",()->changeSpeed(7)));
        HorizontalScrollView tools=new HorizontalScrollView(this);
        tools.setFillViewport(false);
        tools.setHorizontalScrollBarEnabled(false);
        tools.addView(buttons);
        root.addView(tools,new LinearLayout.LayoutParams(-1,dp(43)));

        scrolling=new ScrollView(this);
        scrolling.setFillViewport(false);
        scrolling.setVerticalScrollBarEnabled(false);
        scriptText=new TextView(this);
        scriptText.setTextColor(Color.WHITE);
        scriptText.setTextSize(fontSp);
        scriptText.setGravity(Gravity.CENTER_HORIZONTAL);
        scriptText.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        scriptText.setLineSpacing(dp(6),1.12f);
        scriptText.setPadding(dp(10),dp(40),dp(10),dp(100));
        scrolling.addView(scriptText,new ScrollView.LayoutParams(-1,-2));
        root.addView(scrolling,new LinearLayout.LayoutParams(-1,0,1f));

        LinearLayout footer=new LinearLayout(this);
        footer.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        TextView hint=new TextView(this);
        hint.setText("Drag corner to resize  •  Swipe text to scroll");
        hint.setTextColor(0xFFADB7C5);
        hint.setTextSize(10);
        footer.addView(hint,new LinearLayout.LayoutParams(0,dp(30),1f));
        TextView grip=new TextView(this);
        grip.setText("◢");
        grip.setTextColor(0xFFFFB347);
        grip.setTextSize(24);
        grip.setGravity(Gravity.CENTER);
        footer.addView(grip,new LinearLayout.LayoutParams(dp(42),dp(38)));
        attachResize(grip);
        root.addView(footer);
        manager.addView(root,params);
    }

    private void attachMove(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float startX,startY;
            int oldX,oldY;
            @Override public boolean onTouch(View v,MotionEvent e) {
                switch(e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX=e.getRawX();startY=e.getRawY();
                        oldX=params.x;oldY=params.y;return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x=Math.max(0,oldX+Math.round(e.getRawX()-startX));
                        params.y=Math.max(0,oldY+Math.round(e.getRawY()-startY));
                        updateWindow();return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        storeGeometry();return true;
                }
                return false;
            }
        });
    }

    private void attachResize(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float startX,startY;
            int w,h;
            @Override public boolean onTouch(View v,MotionEvent e) {
                switch(e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX=e.getRawX();startY=e.getRawY();
                        w=params.width;h=params.height;return true;
                    case MotionEvent.ACTION_MOVE:
                        params.width=Math.max(dp(205),Math.min(getResources().getDisplayMetrics().widthPixels-dp(8),
                            w+Math.round(e.getRawX()-startX)));
                        params.height=Math.max(dp(185),Math.min(getResources().getDisplayMetrics().heightPixels-dp(55),
                            h+Math.round(e.getRawY()-startY)));
                        updateWindow();return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        storeGeometry();return true;
                }
                return false;
            }
        });
    }

    private void updateWindow() {
        try { if(root!=null) manager.updateViewLayout(root,params); }
        catch(Exception ignored) {}
    }
    private void storeGeometry() {
        settings.edit().putInt("x",params.x).putInt("y",params.y)
            .putInt("width",params.width).putInt("height",params.height).apply();
    }

    private void selectScript(int id) {
        ScriptCatalog.Script script=catalog.byId(id);
        if(script==null)return;
        pause();
        currentScript=script.id;
        titleText.setText("⠿  #"+script.id+"  "+script.title);
        scriptText.setText(script.text);
        scrolling.post(()->scrolling.scrollTo(0,0));
        settings.edit().putInt("script",currentScript).apply();
    }
    private void changeScript(int delta) {
        ScriptCatalog.Script next=catalog.neighbor(currentScript,delta);
        if(next!=null) selectScript(next.id);
    }
    private void changeFont(int delta) {
        fontSp=Math.max(16,Math.min(46,fontSp+delta));
        scriptText.setTextSize(fontSp);
        settings.edit().putInt("font",fontSp).apply();
    }
    private void changeSpeed(float delta) {
        speedDp=Math.max(12,Math.min(160,speedDp+delta));
        settings.edit().putFloat("speed",speedDp).apply();
    }
    private final Runnable scrollTick=new Runnable() {
        @Override public void run() {
            if(!playing || scrolling==null)return;
            float advance=dp(speedDp)/30f+scrollFraction;
            int pixels=(int)advance;
            scrollFraction=advance-pixels;
            scrolling.scrollBy(0,pixels);
            int max=Math.max(0,scriptText.getHeight()-scrolling.getHeight());
            if(scrolling.getScrollY()>=max && max>0)pause();
            else handler.postDelayed(this,33);
        }
    };
    private void toggle() {
        if(playing)pause();
        else {
            playing=true;scrollFraction=0;playButton.setText("Ⅱ");
            handler.post(scrollTick);
        }
    }
    private void pause() {
        playing=false;
        handler.removeCallbacks(scrollTick);
        if(playButton!=null)playButton.setText("▶");
    }

    @Override public void onDestroy() {
        if(root!=null && root.isAttachedToWindow()) reportOverlayStatus(false,"Floating overlay stopped.");
        pause();
        if(root!=null) {
            try { manager.removeView(root); } catch(Exception ignored) {}
            root=null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
