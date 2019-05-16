package pl.bclogic.pulsator4droid.library;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

public class PulseRoundedRectangle implements PulseShape {

    private RectF mRect;
    private Paint mPaint;

    PulseRoundedRectangle(Paint paint) {
        this.mPaint = paint;
    }

    @Override
    public void setSize(float width, float height) {
        mRect = new RectF(0, 0, width, height);
    }

    @Override
    public void draw(Canvas canvas) {
        float radius = mRect.height() * 0.5f;
        canvas.drawRoundRect(mRect, radius, radius, mPaint);
    }

    RectF getRect() {
        return mRect;
    }
}
