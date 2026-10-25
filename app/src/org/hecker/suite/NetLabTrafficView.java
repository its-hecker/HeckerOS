// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** On-screen local interface samples, with no fabricated per-peer accounting. */
final class NetLabTrafficView extends View {
    private final ArrayDeque<long[]> samples=new ArrayDeque<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    NetLabTrafficView(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void sample(long rx,long tx){if(samples.size()==40)samples.removeFirst();samples.addLast(new long[]{Math.max(0,rx),Math.max(0,tx)});invalidate();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;
        float left=8*density,top=12*density,width=getWidth()-16*density,height=getHeight()-24*density;
        paint.setStrokeWidth(density);paint.setColor(getContext().getColor(R.color.hecker_border));
        for(int i=0;i<4;i++)canvas.drawLine(left,top+height*i/3,left+width,top+height*i/3,paint);
        if(samples.size()<2)return;long max=1024;for(long[] s:samples)max=Math.max(max,Math.max(s[0],s[1]));
        for(int stream=0;stream<2;stream++){Path path=new Path();int i=0;
            for(long[] s:samples){float x=left+width*i/(samples.size()-1),y=top+height-height*s[stream]/max;if(i++==0)path.moveTo(x,y);else path.lineTo(x,y);}
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.5f*density);paint.setColor(stream==0?getContext().getColor(R.color.hecker_accent):0xffba9fff);canvas.drawPath(path,paint);}
        paint.setStyle(Paint.Style.FILL);
    }
}
