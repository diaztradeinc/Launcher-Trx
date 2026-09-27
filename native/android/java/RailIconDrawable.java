package com.diaztradeinc.trxlauncher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Density independent rail symbols, without font-dependent Unicode glyphs. */
public final class RailIconDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String symbol;
    RailIconDrawable(String symbol,int color){this.symbol=symbol;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
    @Override public void draw(Canvas canvas){
        canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);
        Path p=new Path();
        if("⌂".equals(symbol)){p.moveTo(3,10);p.lineTo(12,3);p.lineTo(21,10);p.lineTo(21,21);p.lineTo(15,21);p.lineTo(15,14);p.lineTo(9,14);p.lineTo(9,21);p.lineTo(3,21);p.close();}
        else if("◫".equals(symbol)){canvas.drawRoundRect(3,4,21,20,3,3,paint);p.moveTo(12,5);p.lineTo(12,19);}
        else if("←".equals(symbol)){p.moveTo(12,5);p.lineTo(5,12);p.lineTo(12,19);p.moveTo(5,12);p.lineTo(21,12);}
        else{p.moveTo(6,14);p.lineTo(12,8);p.lineTo(18,14);canvas.drawLine(8,19,16,19,paint);}
        canvas.drawPath(p,paint);canvas.restore();
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
