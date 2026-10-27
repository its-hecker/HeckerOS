// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Native, theme-independent NetLab chrome. Icons are drawn as vectors at any density. */
final class NetLabStyle {
    static final int BLACK=0xff000000, PANEL=0xff191919, BODY=0xff101010;
    static final int WHITE=0xfffafafa, MUTED=0xffb3b3b3, ORANGE=0xffff7900;
    static final int WIFI=0, TIMER=1, COPY=2, LOCK=3, REFRESH=4, SEARCH=5, CUT=6, DEVICE=7, ANDROID=8;
    static final int APPLE=9, WINDOWS=10, LINUX=11, MEDIA=12, ROUTER=13, UNKNOWN=14;
    private NetLabStyle() {}

    static GradientDrawable shape(Activity a,int color,int radius) {
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);
        shape.setCornerRadius(Ui.dp(a,radius));return shape;
    }
    static TextView text(Activity a,CharSequence value,int sp,boolean bold) {
        TextView label=new TextView(a);label.setText(value);label.setTextSize(sp);
        label.setTextColor(WHITE);label.setIncludeFontPadding(false);
        if(bold)label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        label.setPadding(0,Ui.dp(a,6),0,Ui.dp(a,6));return label;
    }
    static LinearLayout column(Activity a) {
        LinearLayout column=new LinearLayout(a);column.setOrientation(LinearLayout.VERTICAL);return column;
    }
    static LinearLayout row(Activity a) {
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);return row;
    }
    static LinearLayout card(Activity a) {
        LinearLayout card=column(a);card.setPadding(Ui.dp(a,12),Ui.dp(a,8),Ui.dp(a,12),Ui.dp(a,8));
        card.setBackground(shape(a,PANEL,8));card.setClipToOutline(true);
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.bottomMargin=Ui.dp(a,10);
        card.setLayoutParams(size);return card;
    }
    static Button button(Activity a) {
        Button button=new Button(a);button.setAllCaps(false);button.setTextSize(14);
        button.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{MUTED,WHITE}));
        button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(Ui.dp(a,48));button.setMinimumHeight(Ui.dp(a,48));
        button.setPadding(Ui.dp(a,12),Ui.dp(a,8),Ui.dp(a,12),Ui.dp(a,8));button.setStateListAnimator(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),shape(a,0xff292929,6),null));
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.bottomMargin=Ui.dp(a,6);button.setLayoutParams(size);
        return button;
    }
    static ImageButton icon(Activity a,int type,CharSequence description,View.OnClickListener action) {
        ImageButton button=new ImageButton(a);button.setImageDrawable(new Icon(type));
        // ImageButton's default CENTER keeps a 24 px drawable tiny on high-density phones.
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setPadding(Ui.dp(a,8),Ui.dp(a,8),Ui.dp(a,8),Ui.dp(a,8));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),null,shape(a,WHITE,24)));
        button.setContentDescription(description);button.setTooltipText(description);button.setOnClickListener(action);
        button.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a,48),Ui.dp(a,48)));return button;
    }
    static SeekBar speed(Activity a) {
        SeekBar speed=new SeekBar(a);speed.setMax(12);speed.setSplitTrack(false);
        speed.setMinHeight(Ui.dp(a,20));speed.setMaxHeight(Ui.dp(a,20));
        GradientDrawable base=shape(a,0xff363636,6),fill=shape(a,ORANGE,6);
        base.setSize(Ui.dp(a,180),Ui.dp(a,20));fill.setSize(Ui.dp(a,180),Ui.dp(a,20));
        ClipDrawable progress=new ClipDrawable(fill,Gravity.START,ClipDrawable.HORIZONTAL);
        LayerDrawable track=new LayerDrawable(new Drawable[]{base,progress});
        track.setId(0,android.R.id.background);track.setId(1,android.R.id.progress);speed.setProgressDrawable(track);
        GradientDrawable thumb=shape(a,WHITE,2);thumb.setSize(Ui.dp(a,3),Ui.dp(a,20));speed.setThumb(thumb);
        speed.setThumbOffset(0);speed.setPadding(Ui.dp(a,4),Ui.dp(a,14),Ui.dp(a,4),Ui.dp(a,14));
        speed.setLayoutParams(new LinearLayout.LayoutParams(0,Ui.dp(a,48),1));return speed;
    }

    static Drawable brandMark(String vendor){
        String name=vendor.toLowerCase(java.util.Locale.ROOT),mark="";
        if(name.contains("xiaomi"))mark="MI";
        else if(name.contains("samsung"))mark="S";
        else if(name.contains("google"))mark="G";
        else if(name.contains("oneplus"))mark="1+";
        else if(name.contains("nothing"))mark="N";
        else if(name.contains("huawei"))mark="H";
        else if(name.contains("motorola"))mark="M";
        else if(name.contains("oppo"))mark="OP";
        else if(name.contains("vivo"))mark="v";
        else if(name.contains("realme"))mark="R";
        else if(name.contains("microsoft"))mark="MS";
        if(mark.isEmpty())return new Icon(UNKNOWN);
        final String label=mark;
        return new Drawable(){
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            public void draw(Canvas c){
                paint.setColor(WHITE);paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(getBounds().height()*(label.length()>1?.65f:.8f));Paint.FontMetrics font=paint.getFontMetrics();
                c.drawText(label,getBounds().exactCenterX(),getBounds().exactCenterY()-(font.ascent+font.descent)/2,paint);
            }
            public void setAlpha(int value){paint.setAlpha(value);invalidateSelf();}
            public void setColorFilter(ColorFilter value){paint.setColorFilter(value);invalidateSelf();}
            public int getOpacity(){return PixelFormat.TRANSLUCENT;}
            public int getIntrinsicWidth(){return 24;}
            public int getIntrinsicHeight(){return 24;}
        };
    }

    static final class Icon extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int type;
        Icon(int type){this.type=type;}
        @Override public void draw(Canvas c) {
            int saved=c.save();c.translate(getBounds().left,getBounds().top);
            c.scale(getBounds().width()/24f,getBounds().height()/24f);
            paint.setColor(WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);
            paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            Path p=new Path();
            switch(type) {
                case WIFI:case CUT:
                    paint.setStyle(Paint.Style.FILL);p.moveTo(12,22);p.lineTo(.5f,6.8f);
                    p.cubicTo(7,-.1f,17,-.1f,23.5f,6.8f);p.close();c.drawPath(p,paint);
                    if(type==CUT){paint.setStyle(Paint.Style.STROKE);paint.setColor(BLACK);paint.setStrokeWidth(5);c.drawLine(2,1,22,23,paint);
                        paint.setColor(WHITE);paint.setStrokeWidth(1.8f);c.drawLine(2,1,22,23,paint);}break;
                case TIMER:
                    c.drawCircle(12,14,8,paint);c.drawLine(9,1,15,1,paint);c.drawLine(12,1,12,4,paint);
                    c.drawLine(18.5f,5.5f,20,4,paint);c.drawLine(12,8,12,14,paint);break;
                case COPY:
                    p.moveTo(16,2);p.lineTo(4,2);p.lineTo(4,18);c.drawPath(p,paint);
                    c.drawRoundRect(8,6,21,23,1,1,paint);break;
                case LOCK:
                    c.drawArc(7,1,17,15,180,180,false,paint);c.drawRoundRect(4.5f,9,19.5f,23,1,1,paint);
                    c.drawCircle(12,15,1.5f,paint);c.drawLine(12,17,12,19,paint);break;
                case REFRESH:
                    c.drawArc(3,3,21,21,40,305,false,paint);p.moveTo(21,3);p.lineTo(21,10);p.lineTo(14,10);
                    c.drawPath(p,paint);c.drawLine(12,7,12,13,paint);c.drawLine(12,13,16,15,paint);break;
                case SEARCH:
                    c.drawCircle(9,9,7,paint);c.drawOval(6,2,12,16,paint);c.drawLine(2,9,16,9,paint);
                    c.drawLine(4,5,14,5,paint);c.drawLine(4,13,14,13,paint);c.drawLine(15,15,23,23,paint);break;
                case ANDROID:
                    c.drawLine(6,3,3,0,paint);c.drawLine(18,3,21,0,paint);paint.setStyle(Paint.Style.FILL);
                    c.drawArc(1,2,23,24,180,180,true,paint);paint.setColor(PANEL);
                    c.drawCircle(7,9,1.3f,paint);c.drawCircle(17,9,1.3f,paint);break;
                case APPLE:
                    paint.setStyle(Paint.Style.FILL);p.moveTo(12,6);p.cubicTo(7,2,2,7,4,13);p.cubicTo(5,18,7,23,10,21);
                    p.cubicTo(12,20,13,20,15,21);p.cubicTo(18,23,20,18,21,16);p.cubicTo(16,14,16,9,20,7);
                    p.cubicTo(17,3,14,5,12,6);p.close();c.drawPath(p,paint);c.drawOval(12,0,16,5,paint);break;
                case WINDOWS:
                    paint.setStyle(Paint.Style.FILL);c.drawRect(2,3,10.5f,10.5f,paint);c.drawRect(13,2,23,10.5f,paint);
                    c.drawRect(2,13,10.5f,20.5f,paint);c.drawRect(13,13,23,22,paint);break;
                case LINUX:
                    c.drawOval(5,7,19,22,paint);c.drawOval(8,1,16,12,paint);c.drawCircle(10,5,0.5f,paint);c.drawCircle(14,5,0.5f,paint);
                    p.moveTo(10,8);p.lineTo(12,10);p.lineTo(14,8);c.drawPath(p,paint);c.drawLine(4,23,10,23,paint);c.drawLine(14,23,20,23,paint);break;
                case MEDIA:
                    c.drawRoundRect(1,4,23,20,1,1,paint);c.drawLine(7,1,12,4,paint);c.drawLine(17,1,12,4,paint);
                    p.moveTo(10,8);p.lineTo(16,12);p.lineTo(10,16);p.close();c.drawPath(p,paint);break;
                case ROUTER:
                    c.drawRoundRect(1,13,23,22,2,2,paint);c.drawLine(5,13,5,3,paint);c.drawLine(19,13,19,3,paint);
                    c.drawCircle(6,18,0.5f,paint);c.drawCircle(10,18,0.5f,paint);c.drawCircle(14,18,0.5f,paint);break;
                case UNKNOWN:
                    c.drawCircle(12,12,10,paint);c.drawArc(9,5,16,12,190,270,false,paint);c.drawLine(12,12,12,15,paint);c.drawCircle(12,19,.5f,paint);break;
                default:
                    c.drawRoundRect(3,2,21,18,1,1,paint);c.drawLine(12,18,12,22,paint);c.drawLine(8,22,16,22,paint);
            }
            c.restoreToCount(saved);
        }
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        @Override public int getIntrinsicWidth(){return 24;}
        @Override public int getIntrinsicHeight(){return 24;}
    }
}
