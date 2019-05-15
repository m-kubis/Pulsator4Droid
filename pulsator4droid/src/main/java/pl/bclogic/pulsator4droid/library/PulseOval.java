package pl.bclogic.pulsator4droid.library;

import android.graphics.Canvas;
import android.graphics.Paint;

public class PulseOval implements PulseShape {

    private float mLeft;
    private float mTop;
    private float mRight;
    private float mBottom;
    private Paint mPaint;

    PulseOval(Paint paint) {
        this.mPaint = paint;
    }

    @Override
    public void setSize(float width, float height) {
        mLeft = 0;
        mTop = 0;
        mRight = width;
        mBottom = height;
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawOval(mLeft, mTop, mRight, mBottom, mPaint);
    }
}
