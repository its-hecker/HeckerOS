// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared native surfaces: compact toolbar, joined cards, pill actions and icon navigation. */
final class VaultUi {
    final Context c;
    VaultUi(Context c){this.c=c;}
    int dp(float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    int color(int id){return c.getColor(id);}
    GradientDrawable shape(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    Drawable ripple(int color,float radius){return new RippleDrawable(ColorStateList.valueOf(color(R.color.vault_ripple)),shape(color,radius),null);}
    TextView text(String value,int size,boolean medium){TextView v=new TextView(c);v.setText(value);v.setTextSize(size);
        v.setTextColor(color(medium?R.color.vault_foreground:R.color.vault_secondary));
        v.setTypeface(Typeface.create(medium?"sans-serif-medium":"sans-serif",Typeface.NORMAL));v.setIncludeFontPadding(false);v.setLineSpacing(dp(2),1);return v;}
    Button button(String value,boolean primary){Button b=new Button(c);b.setText(value);b.setAllCaps(false);b.setTextSize(15);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(color(primary?R.color.vault_on_accent:R.color.vault_accent));
        b.setBackgroundTintList(null);b.setBackground(ripple(color(primary?R.color.vault_accent:R.color.vault_button),28));
        b.setStateListAnimator(null);b.setElevation(0);b.setMinHeight(dp(46));b.setMinimumHeight(dp(46));b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(dp(16),dp(10),dp(16),dp(10));b.setFilterTouchesWhenObscured(true);return b;}
    ImageButton icon(int drawable,String label,Runnable action){ImageButton v=new ImageButton(c);v.setImageResource(drawable);
        v.setImageTintList(ColorStateList.valueOf(color(R.color.vault_foreground)));v.setContentDescription(label);
        v.setBackground(ripple(color(R.color.vault_card),24));v.setPadding(dp(12),dp(12),dp(12),dp(12));
        v.setFilterTouchesWhenObscured(true);v.setOnClickListener(w->action.run());return v;}
    LinearLayout column(){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout card(LinearLayout parent,boolean first,boolean last){LinearLayout v=column();v.setPadding(dp(16),dp(17),dp(16),dp(17));
        GradientDrawable shape=shape(color(R.color.vault_card),0);float a=dp(first?18:3),b=dp(last?18:3);
        shape.setCornerRadii(new float[]{a,a,a,a,b,b,b,b});v.setBackground(new RippleDrawable(ColorStateList.valueOf(color(R.color.vault_ripple)),shape,null));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(last?0:3);parent.addView(v,p);return v;}
    void section(LinearLayout parent,int icon,String title){LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8),dp(28),dp(8),dp(12));
        if(icon!=0){ImageView image=new ImageView(c);image.setImageResource(icon);image.setImageTintList(ColorStateList.valueOf(color(R.color.vault_secondary)));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(17),dp(17));p.setMarginEnd(dp(8));row.addView(image,p);}
        TextView label=text(title,12,false);label.setLetterSpacing(.05f);row.addView(label);parent.addView(row);}
    void settingsSection(LinearLayout parent,String title){TextView v=text(title,15,true);v.setTextColor(color(R.color.vault_accent));v.setPadding(dp(12),dp(24),dp(12),dp(10));parent.addView(v);}
    void copy(LinearLayout parent,String title,String detail){TextView name=text(title,16,true);parent.addView(name);
        if(detail!=null&&!detail.isEmpty()){TextView secondary=text(detail,14,false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(4);parent.addView(secondary,p);}}
    void feature(LinearLayout parent,int icon,int tint,String title,String detail){
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView badge=new ImageView(c);badge.setImageResource(icon);badge.setImageTintList(ColorStateList.valueOf(tint));
        badge.setPadding(dp(11),dp(11),dp(11),dp(11));badge.setBackground(shape((tint&0x00ffffff)|0x24000000,16));
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(badge,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout copy=column();copy.setPadding(dp(14),0,0,0);copy(copy,title,detail);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));parent.addView(row);
    }
    void hero(LinearLayout parent){
        LinearLayout card=new LinearLayout(c);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(20),dp(20),dp(12),dp(20));
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff293956,0xff183237,0xff1d283e});
        background.setCornerRadius(dp(26));background.setStroke(dp(1),0xff3e5469);card.setBackground(background);
        LinearLayout copy=column();copy.addView(text(c.getString(R.string.vault_hero_title),23,true));
        TextView subtitle=text(c.getString(R.string.vault_hero_detail),13,false);subtitle.setTextColor(0xffbdcdd9);subtitle.setPadding(0,dp(8),0,0);copy.addView(subtitle);
        card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        com.airbnb.lottie.LottieAnimationView animation=new com.airbnb.lottie.LottieAnimationView(c);
        animation.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);animation.setAnimation(R.raw.vault_guardian);animation.setRepeatCount(-1);
        if(android.animation.ValueAnimator.areAnimatorsEnabled())animation.playAnimation();else animation.setProgress(.5f);
        card.addView(animation,new LinearLayout.LayoutParams(dp(112),dp(132)));parent.addView(card,new LinearLayout.LayoutParams(-1,-2));
    }
    void action(LinearLayout parent,String label,Runnable action){Button v=button(label,false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(14);parent.addView(v,p);v.setOnClickListener(w->action.run());}
    void navigation(LinearLayout parent,int selected,java.util.function.IntConsumer action){parent.removeAllViews();parent.setBackgroundColor(color(R.color.vault_navigation));parent.setPadding(dp(8),dp(8),dp(8),dp(10));
        int[] icons={R.drawable.v_home,R.drawable.v_apps,R.drawable.v_archive,R.drawable.v_settings};
        int[] labels={R.string.vault_home,R.string.vault_apps,R.string.vault_archives,R.string.vault_settings};
        for(int i=0;i<icons.length;i++){final int destination=i;boolean on=i==selected;LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setPadding(0,dp(2),0,0);
            ImageView image=new ImageView(c);image.setImageResource(icons[i]);image.setImageTintList(ColorStateList.valueOf(color(on?R.color.vault_accent:R.color.vault_secondary)));
            image.setPadding(dp(15),dp(4),dp(15),dp(4));image.setBackground(on?shape(color(R.color.vault_selected),22):null);
            item.addView(image,new LinearLayout.LayoutParams(dp(54),dp(30)));TextView label=text(c.getString(labels[i]),12,on);
            label.setTextColor(color(on?R.color.vault_accent:R.color.vault_secondary));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.topMargin=dp(5);item.addView(label,lp);
            item.setContentDescription(c.getString(labels[i]));item.setSelected(on);item.setFocusable(true);item.setBackground(ripple(android.graphics.Color.TRANSPARENT,20));item.setOnClickListener(v->action.accept(destination));
            parent.addView(item,new LinearLayout.LayoutParams(0,-2,1));}}
}
