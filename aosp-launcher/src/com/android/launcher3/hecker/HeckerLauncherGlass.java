// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.app.WallpaperManager;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import com.android.launcher3.LauncherFiles;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

/** Launcher-owned containers only. Wallpaper decoding and frosting never run on the UI thread. */
public final class HeckerLauncherGlass {
    private static final WeakHashMap<View,Session> SESSIONS=new WeakHashMap<>();
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    public static void attach(View workspace){detach(workspace);Session s=new Session(workspace);SESSIONS.put(workspace,s);s.start();}
    public static void detach(View workspace){Session s=SESSIONS.remove(workspace);if(s!=null)s.close();}
    public static void offset(View workspace, float offset) {
        Session session = SESSIONS.get(workspace);
        if (session == null || !Float.isFinite(offset)) return;
        float clamped = Math.max(0, Math.min(1, offset));
        if (session.offset != clamped) {
            session.offset = clamped;
            session.invalidate();
        }
    }

    private static final class Session implements ViewTreeObserver.OnGlobalLayoutListener,SharedPreferences.OnSharedPreferenceChangeListener {
        private static final String[] CORNERS = {"tl", "tr", "br", "bl"};
        final WeakReference<View> workspace;
        final SharedPreferences prefs;
        final Context app;
        final int wallpaperScaleResId;
        final WeakHashMap<View,Drawable> originals=new WeakHashMap<>();
        final WeakHashMap<View,Glass> panels=new WeakHashMap<>();
        Bitmap wallpaper,frosted;
        boolean stopped,queued;
        boolean targetsDirty;
        int generation;
        float offset=.5f;
        int mode, frostRadius, tint, border, styleRevision;
        float opacity;
        boolean foldersEnabled;
        final int[] cornerDp = new int[CORNERS.length];
        final BroadcastReceiver changed=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){load();}};
        final Runnable refresh=this::refresh;
        final Runnable refrost=this::refrost;
        final Runnable scanAfterLayout = () -> {
            if (stopped || mode == 0) return;
            View view = workspace.get();
            if (view != null) scan(view.getRootView(), new int[]{0});
        };
        int option(String key,int fallback,int min,int max){try{return Math.max(min,Math.min(max,prefs.getInt("hecker_glass_"+key,fallback)));}catch(ClassCastException bad){return fallback;}}
        Session(View view) {
            workspace = new WeakReference<>(view);
            app = view.getContext().getApplicationContext();
            prefs = app.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
            wallpaperScaleResId = view.getResources().getIdentifier(
                    "config_wallpaperMaxScale", "dimen", "android");
            readStyle();
        }

        private int stringOption(String key, int fallback, int max) {
            try {
                return Math.max(0, Math.min(max,
                        Integer.parseInt(prefs.getString(key, Integer.toString(fallback)))));
            } catch (NumberFormatException | ClassCastException bad) {
                return fallback;
            }
        }

        private boolean targetEnabled(String target) {
            boolean fallback = !"dock".equals(target);
            try {
                return prefs.getBoolean("hecker_glass_" + target, fallback);
            } catch (ClassCastException bad) {
                return fallback;
            }
        }

        private void readStyle() {
            mode = stringOption("hecker_glass", 1, 2);
            opacity = option("opacity", 72, 0, 100) / 100f;
            frostRadius = option("frost", 18, 0, 32);
            tint = option("tint", 0xff30232e, Integer.MIN_VALUE, Integer.MAX_VALUE);
            border = option("border", 1, 0, 4);
            foldersEnabled = targetEnabled("folders");
            for (int i = 0; i < cornerDp.length; i++) {
                cornerDp[i] = stringOption("hecker_glass_corner_" + CORNERS[i], 28, 48);
            }
            styleRevision++;
        }

        void refresh() {
            queued = false;
            if (stopped) return;
            readStyle();
            if (targetsDirty) {
                targetsDirty = false;
                restore();
                View view = workspace.get();
                if (view != null) {
                    View root = view.getRootView();
                    if (mode != 0) scan(root, new int[]{0});
                    root.invalidate();
                }
            }
            invalidate();
        }

        void start(){
            View v=workspace.get();if(v==null)return;
            v.getRootView().getViewTreeObserver().addOnGlobalLayoutListener(this);
            prefs.registerOnSharedPreferenceChangeListener(this);
            app.registerReceiver(changed,new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),Context.RECEIVER_NOT_EXPORTED);
            schedule(true);
            load();
        }
        void close(){stopped=true;generation++;MAIN.removeCallbacks(refresh);MAIN.removeCallbacks(refrost);MAIN.removeCallbacks(scanAfterLayout);prefs.unregisterOnSharedPreferenceChangeListener(this);
            app.unregisterReceiver(changed);View v=workspace.get();if(v!=null){ViewTreeObserver observer=v.getRootView().getViewTreeObserver();if(observer.isAlive())observer.removeOnGlobalLayoutListener(this);}
            restore();panels.clear();wallpaper=null;frosted=null;
        }
        void restore(){for(Map.Entry<View,Drawable> e:new ArrayList<>(originals.entrySet())){View v=e.getKey();if(v!=null&&v.getBackground() instanceof Glass)v.setBackground(e.getValue());}originals.clear();}
        void invalidate(){for(View v:new ArrayList<>(originals.keySet()))if(v!=null)v.invalidate();for(View v:new ArrayList<>(panels.keySet()))if(v!=null)v.invalidate();}
        void schedule(boolean refreshTargets) {
            if (stopped) return;
            targetsDirty |= refreshTargets;
            if (!queued) {
                queued = true;
                MAIN.post(refresh);
            }
        }

        @Override public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
            if (key == null || key.startsWith("hecker_glass")) {
                boolean refreshTargets = key == null || switch (key) {
                    case "hecker_glass", "hecker_glass_dock", "hecker_glass_folders",
                            "hecker_glass_search", "hecker_glass_widgets",
                            "hecker_glass_preview" -> true;
                    default -> false;
                };
                schedule(refreshTargets);
                if (key == null || key.equals("hecker_glass_frost")) {
                    MAIN.removeCallbacks(refrost);
                    MAIN.postDelayed(refrost, 120);
                }
            } else if (key.startsWith(HeckerStudio.PREFIX + "dock_search")
                    || key.equals(HeckerStudio.PREFIX + "google_search")
                    || key.equals(HeckerStudio.PREFIX + "search_height")) {
                schedule(true);
            }
        }

        @Override public void onGlobalLayout(){
            if (stopped || mode == 0) return;
            // Folder transitions trigger layout on every frame. Scan only after
            // the tree settles; new widgets/folders are still discovered.
            MAIN.removeCallbacks(scanAfterLayout);
            MAIN.postDelayed(scanAfterLayout, 120);
        }
        void scan(View v,int[] count){
            if(++count[0]>3000||v.getVisibility()!=View.VISIBLE)return;
            String name=v.getClass().getName(),id="";
            // generateViewId() uses 0x00xxxxxx; those are not resource-table entries.
            int resourceId = v.getId();
            try{if(resourceId != View.NO_ID && (resourceId >>> 24) != 0)id=v.getResources().getResourceEntryName(resourceId);}catch(RuntimeException ignored){}
            String target="hecker_glass_preview".equals(v.getTag())?"preview":name.endsWith(".Hotseat")?"dock":name.endsWith(".hecker.HeckerDockBar")||name.endsWith(".qsb.OseWidgetView")||id.equals("qsb_container")||id.equals("search_container_workspace")||id.equals("search_container_all_apps")?"search"
                    :v instanceof android.appwidget.AppWidgetHostView?"widgets":null;
            if(v instanceof android.appwidget.AppWidgetHostView && ((android.appwidget.AppWidgetHostView)v).getAppWidgetInfo()!=null
                    && ((android.appwidget.AppWidgetHostView)v).getAppWidgetInfo().provider.equals(new ComponentName(app,HeckerClockWidget.class))
                    && v instanceof ViewGroup && ((ViewGroup)v).getChildCount()>0 && ((ViewGroup)v).getChildAt(0) instanceof HeckerClockView
                    && !((HeckerClockView)((ViewGroup)v).getChildAt(0)).style().equals("glass"))target=null;
            if(v instanceof HeckerDockBar&&((HeckerDockBar)v).compact())target=null;
            if(target!=null&&!originals.containsKey(v)&&targetEnabled(target)){
                originals.put(v,v.getBackground());v.setBackground(new Glass(v,this));
            }
            // The widget host is a launcher target. Its child views belong to the widget.
            if(v instanceof ViewGroup && !(v instanceof android.appwidget.AppWidgetHostView)){
                ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)scan(group.getChildAt(i),count);
            }
        }
        void refrost(){
            Bitmap source=wallpaper;if(source==null||stopped)return;
            int request=++generation,radius=frostRadius;
            WORKER.execute(()->{Bitmap soft=frost(source,radius);MAIN.post(()->{if(!stopped&&request==generation){frosted=soft;invalidate();}});});
        }
        void load(){
            final int request=++generation;
            WORKER.execute(()->{
                Bitmap image=null,blur=null;
                try{
                    WallpaperManager manager=WallpaperManager.getInstance(app);
                    // A live wallpaper has no static frame we can safely sample.
                    if(manager.getWallpaperInfo()==null)try(ParcelFileDescriptor file=manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)){
                        if(file!=null){
                            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                            BitmapFactory.decodeFileDescriptor(file.getFileDescriptor(),null,bounds);
                            android.system.Os.lseek(file.getFileDescriptor(),0,android.system.OsConstants.SEEK_SET);
                            BitmapFactory.Options options=new BitmapFactory.Options();int sample=1;
                            while(Math.max(bounds.outWidth,bounds.outHeight)/sample>1024)sample*=2;
                            options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                            image=BitmapFactory.decodeFileDescriptor(file.getFileDescriptor(),null,options);
                            if(image!=null)blur=frost(image,option("frost",18,0,32));
                        }
                    }
                }catch(Exception unavailable){android.util.Log.w("HeckerLauncher","Wallpaper glass uses translucent fallback",unavailable);}
                final Bitmap source=image,soft=blur;
                MAIN.post(()->{if(stopped||request!=generation)return;wallpaper=source;frosted=soft;invalidate();});
            });
        }
    }
    /** Small, separable blur of a bounded wallpaper copy; app/widget pixels are never captured. */
    private static Bitmap frost(Bitmap source,int radius){
        if(radius==0)return source;
        float factor=Math.min(1,512f/Math.max(source.getWidth(),source.getHeight()));
        Bitmap small=Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*factor)),Math.max(1,Math.round(source.getHeight()*factor)),true);
        int w=small.getWidth(),h=small.getHeight();int[] pixels=new int[w*h],temp=new int[w*h];small.getPixels(pixels,0,w,0,0,w,h);
        for(int pass=0;pass<3;pass++){blur(pixels,temp,w,h,true,radius);blur(temp,pixels,w,h,false,radius);}
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }
    private static void blur(int[] input,int[] output,int w,int h,boolean horizontal,int radius){
        int span=radius*2+1,lines=horizontal?h:w,length=horizontal?w:h;
        for(int line=0;line<lines;line++){
            int red=0,green=0,blue=0;
            for(int k=-radius;k<=radius;k++){int pos=Math.max(0,Math.min(length-1,k));int color=input[horizontal?line*w+pos:pos*w+line];red+=Color.red(color);green+=Color.green(color);blue+=Color.blue(color);}
            for(int i=0;i<length;i++){
                output[horizontal?line*w+i:i*w+line]=Color.rgb(red/span,green/span,blue/span);
                int before=Math.max(0,i-radius),after=Math.min(length-1,i+radius+1);
                int a=input[horizontal?line*w+before:before*w+line],b=input[horizontal?line*w+after:after*w+line];
                red+=Color.red(b)-Color.red(a);green+=Color.green(b)-Color.green(a);blue+=Color.blue(b)-Color.blue(a);
            }
        }
    }
    private static Session session(View host) {
        View root = host.getRootView();
        for (Session session : SESSIONS.values()) {
            View workspace = session.workspace.get();
            if (!session.stopped && workspace != null && workspace.getRootView() == root) return session;
        }
        return null;
    }
    private static Glass panel(Session session, View host) {
        Glass result = session.panels.get(host);
        if (result == null) { result = new Glass(host, session); session.panels.put(host, result); }
        return result;
    }
    /** Called INSIDE Folder.dispatchDraw's native reveal clip, including the closing animation.
     * Folder.getBackground() returns its private gradient, not View's background. Installing a
     * second View background paints outside that clip and leaves a full-size trailing rectangle.
     */
    public static boolean drawFolder(View host, Canvas canvas, Drawable nativeBackground) {
        Session session = session(host);
        if (session == null || session.mode == 0 || !session.foldersEnabled) return false;
        Glass glass = panel(session, host); glass.setBounds(nativeBackground.getBounds());
        glass.round = false; glass.draw(canvas); return true;
    }
    public static boolean drawFolderPreview(View host, Canvas canvas, Path mask,
            int left, int top, int diameter) {
        if (host == null) return false;
        Session session = session(host);
        if (session == null || session.mode == 0 || !session.foldersEnabled) return false;
        Glass glass = panel(session, host); glass.round = false; glass.setBounds(left, top, left + diameter, top + diameter);
        int save = canvas.save(); canvas.clipPath(mask); glass.draw(canvas); canvas.restoreToCount(save);
        return true;
    }
    static boolean drawIconPlate(View host, Canvas canvas, Rect bounds) {
        if (host == null) return false;
        Session session = session(host);
        if (session == null || session.mode == 0) return false;
        Glass glass = panel(session, host); glass.round = true; glass.setBounds(bounds); glass.draw(canvas);
        return true;
    }
    private static final class Glass extends Drawable {
        final WeakReference<View> host; final Session owner;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final RectF bounds = new RectF(); final Path path = new Path();
        final Matrix matrix = new Matrix(); final int[] position = new int[2];
        final float[] radii = new float[8];
        final Rect geometryBounds = new Rect();
        int geometryRevision = -1;
        float geometryDensity;
        boolean geometryRound;
        Bitmap textureImage; BitmapShader texture; RuntimeShader liquid;
        final RectF rimBounds = new RectF(); LinearGradient rim;
        int alpha = 255; boolean round;
        private static final String PROGRAM = "uniform shader image;uniform float2 size;uniform float4 radii;"
                + "half4 main(float2 xy){float2 p=xy-size*.5;float radius=p.x < 0.0 ? (p.y < 0.0 ? radii.x : radii.w) : (p.y < 0.0 ? radii.y : radii.z);float2 q=abs(p)-size*.5+radius;"
                + "float d=length(max(q,0.))+min(max(q.x,q.y),0.)-radius;"
                + "float2 n=normalize(p+float2(.001));float edge=1.-smoothstep(0.,14.,abs(d));"
                + "half4 c=image.eval(xy+n*edge*4.);return half4(c.rgb+edge*.025,1.);}";
        Glass(View view, Session owner) { host = new WeakReference<>(view); this.owner = owner; }

        private void updateGeometry(View view, Rect frame, float density) {
            if (geometryRevision == owner.styleRevision && geometryDensity == density
                    && geometryRound == round && geometryBounds.equals(frame)) return;
            geometryRevision = owner.styleRevision;
            geometryDensity = density;
            geometryRound = round;
            geometryBounds.set(frame);
            bounds.set(frame);
            if (view instanceof HeckerDockBar) {
                float inset = Math.max(0, (bounds.height() - ((HeckerDockBar) view).visualHeight()) / 2);
                bounds.inset(0, inset);
            }
            float limit = Math.min(bounds.width(), bounds.height()) / 2;
            for (int i = 0; i < 4; i++) radii[i * 2] = radii[i * 2 + 1] = round ? limit
                    : Math.min(limit, owner.cornerDp[i] * density);
            path.reset(); path.addRoundRect(bounds, radii, Path.Direction.CW);
        }

        @Override public void draw(Canvas canvas) {
            View view = host.get();
            float opacity = owner.opacity;
            Rect frame = getBounds();
            if (view == null || alpha == 0 || opacity == 0 || frame.isEmpty()) return;
            float density = view.getResources().getDisplayMetrics().density;
            updateGeometry(view, frame, density);
            Bitmap image = owner.frostRadius > 0 ? owner.frosted : owner.wallpaper;
            if (image == null) image = owner.wallpaper;
            paint.setShader(null); paint.setStyle(Paint.Style.FILL);
            if (image != null && !image.isRecycled()) try {
                view.getLocationOnScreen(position); View root = view.getRootView();
                float w = root.getWidth(), h = root.getHeight(), zoom = 1f;
                int maxScale = owner.wallpaperScaleResId;
                if (maxScale != 0) zoom = view.getResources().getFloat(maxScale);
                float scale = Math.max(w / image.getWidth(), h / image.getHeight()) * zoom;
                matrix.setScale(scale, scale);
                matrix.postTranslate((w - image.getWidth() * scale) * owner.offset - position[0],
                        (h - image.getHeight() * scale) * .5f - position[1]);
                if (textureImage != image) {
                    textureImage = image; texture = new BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    texture.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
                }
                texture.setLocalMatrix(matrix);
                if (owner.mode == 2) {
                    if (liquid == null) liquid = new RuntimeShader(PROGRAM);
                    liquid.setInputShader("image", texture); liquid.setFloatUniform("size", bounds.width(), bounds.height());
                    liquid.setFloatUniform("radii", radii[0], radii[2], radii[4], radii[6]); paint.setShader(liquid);
                } else paint.setShader(texture);
                paint.setColor(Color.WHITE); paint.setAlpha(Math.round(alpha * opacity * .94f));
                canvas.drawPath(path, paint);
            } catch (RuntimeException unsupported) { paint.setShader(null); }
            paint.setShader(null); paint.setColor(owner.tint);
            paint.setAlpha(Math.round(alpha * opacity * (image == null ? .98f : .65f)));
            canvas.drawPath(path, paint);
            int border = owner.border;
            if (border > 0) {
                // Inset the rim so it remains inside both the reveal clip and icon plate.
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(density * border * .65f);
                if (rim == null || !rimBounds.equals(bounds)) {
                    rimBounds.set(bounds);
                    rim = new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                            new int[]{0x65fff1f3, 0x12ffffff, 0x30dfbacd}, null, Shader.TileMode.CLAMP);
                }
                paint.setShader(rim);
                paint.setAlpha(Math.round(alpha * opacity)); canvas.drawPath(path, paint);
                paint.setShader(null); paint.setStyle(Paint.Style.FILL);
            }
        }
        @Override public void setAlpha(int value) { alpha = Math.max(0, Math.min(255, value)); invalidateSelf(); }
        @Override public int getAlpha() { return alpha; }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
    private HeckerLauncherGlass(){}
}
