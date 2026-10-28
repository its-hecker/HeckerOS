// SPDX-License-Identifier: Apache-2.0
package com.android.settings.hecker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.android.settings.R;

/** Draw connected card sections behind the unchanged native preference rows. */
public final class HeckerHomeDecoration extends RecyclerView.ItemDecoration {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int card, border;
    private final float radius, stroke;
    public HeckerHomeDecoration(Context context) {
        card=context.getColor(R.color.hecker_home_card); border=context.getColor(R.color.hecker_home_separator);
        radius=context.getResources().getDisplayMetrics().density*26;
        stroke=context.getResources().getDisplayMetrics().density;
    }
    @Override public void onDraw(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
        if (!(parent.getAdapter() instanceof PreferenceGroupAdapter)) return;
        PreferenceGroupAdapter adapter=(PreferenceGroupAdapter)parent.getAdapter();
        for (int n=0; n<parent.getChildCount(); n++) {
            View view=parent.getChildAt(n);int position=parent.getChildAdapterPosition(view);
            if(position<0)continue;
            Preference item=adapter.getItem(position);
            if(item==null || item instanceof PreferenceCategory || item.getParent()==null || item instanceof HeckerBrandPreference)continue;
            Preference before=position>0?adapter.getItem(position-1):null;
            Preference after=position+1<adapter.getItemCount()?adapter.getItem(position+1):null;
            boolean first=before==null || before instanceof PreferenceCategory || before.getParent()!=item.getParent();
            boolean last=after==null || after instanceof PreferenceCategory || after.getParent()!=item.getParent();
            float top=first?radius:0,bottom=last?radius:0;
            RectF bounds=new RectF(view.getLeft(),view.getTop()+view.getTranslationY(),view.getRight(),view.getBottom()+view.getTranslationY());
            Path path=new Path();path.addRoundRect(bounds,new float[]{top,top,top,top,bottom,bottom,bottom,bottom},Path.Direction.CW);
            paint.setStyle(Paint.Style.FILL);paint.setColor(card);canvas.drawPath(path,paint);
            // The reference has inset separators and a clean silhouette, without a card outline.
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke*.6f);paint.setColor(border);
            if(!last)canvas.drawLine(bounds.left+64*stroke,bounds.bottom,bounds.right-18*stroke,bounds.bottom,paint);
        }
    }
}
