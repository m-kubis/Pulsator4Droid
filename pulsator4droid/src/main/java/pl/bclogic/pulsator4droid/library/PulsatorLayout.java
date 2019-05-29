package pl.bclogic.pulsator4droid.library;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.RectF;
import android.graphics.Region.Op;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.RelativeLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Created by booncol on 04.07.2016.
 */
public class PulsatorLayout extends RelativeLayout {

    public static final int INFINITE = 0;

    public static final int INTERP_LINEAR = 0;
    public static final int INTERP_ACCELERATE = 1;
    public static final int INTERP_DECELERATE = 2;
    public static final int INTERP_ACCELERATE_DECELERATE = 3;
    public static final float RADIUS_NONE = 0;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PULSE_TYPE_CIRCLE, PULSE_TYPE_ROUNDED_RECTANGLE})
    @interface ShapeType {

    }

    public static final int PULSE_TYPE_CIRCLE = 0;
    public static final int PULSE_TYPE_ROUNDED_RECTANGLE = 1;

    private static final int DEFAULT_COUNT = 4;
    private static final int DEFAULT_COLOR = Color.rgb(0, 116, 193);
    private static final int DEFAULT_DURATION = 7000;
    private static final int DEFAULT_REPEAT = INFINITE;
    private static final boolean DEFAULT_START_FROM_SCRATCH = true;
    private static final int DEFAULT_INTERPOLATOR = INTERP_LINEAR;

    private int mCount;
    private int mDuration;
    private int mRepeat;
    private boolean mStartFromScratch;
    private int mColor;
    private int mInterpolator;

    private PulseShape mPulseShape;

    private final List<View> mViews = new ArrayList<>();
    /**
     * {@link android.animation.AnimatorSet} seems to be having issues with
     * {@link android.animation.ValueAnimator#setCurrentPlayTime(long)}
     * being used for its encapsulated animations. We have to handle them (start them) manually one
     * by one to avoid that. More precisely Android versions O and P do not take current play time
     * setting into consideration and play all the animations at the same timing when started using
     * an {@link android.animation.AnimatorSet}.
     *
     * <p>(The play time can be fast-forwarded for the whole set, but not before API 26.)
     */
    private List<Animator> mAnimators;
    private Paint mPaint;
    private Path mMask;
    private float mCircularMaskRadius = RADIUS_NONE;
    private int mRoundedRectangleMaskWidth = 0;
    private int mRoundedRectangleMaskHeight = 0;
    private boolean mIsStarted;

    /**
     * Simple constructor to use when creating a view from code.
     *
     * @param context   The Context the view is running in, through which it can access the current
     *     theme, resources, etc.
     */
    public PulsatorLayout(Context context) {
        this(context, null, 0);
    }

    /**
     * Constructor that is called when inflating a view from XML.
     *
     * @param context   The Context the view is running in, through which it can access the current
     *     theme, resources, etc.
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public PulsatorLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Perform inflation from XML and apply a class-specific base style from a theme attribute.
     *
     * @param context   The Context the view is running in, through which it can access the current
     *     theme, resources, etc.
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @param defStyleAttr  An attribute in the current theme that contains a reference to a style
     *     resource that supplies default values for the view. Can be 0 to not look for defaults.
     */
    public PulsatorLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // get attributes
        TypedArray attr = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.Pulsator4Droid, 0, 0);
        int pulseShapeType;

        mCount = DEFAULT_COUNT;
        mDuration = DEFAULT_DURATION;
        mRepeat = DEFAULT_REPEAT;
        mStartFromScratch = DEFAULT_START_FROM_SCRATCH;
        mColor = DEFAULT_COLOR;
        mInterpolator = DEFAULT_INTERPOLATOR;
        mPulseShape = new PulseCircle(mPaint);

        try {
            mCount = attr.getInteger(R.styleable.Pulsator4Droid_pulse_count, DEFAULT_COUNT);
            mDuration = attr.getInteger(R.styleable.Pulsator4Droid_pulse_duration,
                    DEFAULT_DURATION);
            mRepeat = attr.getInteger(R.styleable.Pulsator4Droid_pulse_repeat, DEFAULT_REPEAT);
            mStartFromScratch = attr.getBoolean(R.styleable.Pulsator4Droid_pulse_startFromScratch,
                    DEFAULT_START_FROM_SCRATCH);
            mColor = attr.getColor(R.styleable.Pulsator4Droid_pulse_color, DEFAULT_COLOR);
            mInterpolator = attr.getInteger(R.styleable.Pulsator4Droid_pulse_interpolator,
                    DEFAULT_INTERPOLATOR);
            pulseShapeType = attr.getInt(R.styleable.Pulsator4Droid_pulse_shape, PULSE_TYPE_CIRCLE);
        } finally {
            attr.recycle();
        }

        // create paint
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mColor);

        setPulseShapeType(pulseShapeType);

        // create views
        build();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mMask == null && mCircularMaskRadius > 0) {
            mMask = new Path();
            PulseCircle circle = (PulseCircle) mPulseShape;
            mMask.addCircle(
                    circle.getCenterX(), circle.getCenterY(), mCircularMaskRadius, Direction.CW);
        } else if (mMask == null
                && mRoundedRectangleMaskWidth > 0
                && mRoundedRectangleMaskHeight > 0) {

            mMask = new Path();
            RectF rectangle = ((PulseRoundedRectangle) mPulseShape).getRect();
            float left = (rectangle.right - rectangle.left - mRoundedRectangleMaskWidth) * 0.5f;
            float right = left + mRoundedRectangleMaskWidth;
            float top = (rectangle.bottom - rectangle.top - mRoundedRectangleMaskHeight) * 0.5f;
            float bottom = top + mRoundedRectangleMaskHeight;
            mMask.addRoundRect(
                    left, top, right, bottom, Integer.MAX_VALUE, Integer.MAX_VALUE, Direction.CW);
        }
        if (mMask != null) {
            canvas.clipPath(mMask, Op.DIFFERENCE);
        }
    }

    /**
     * @return Shape as defined in 'pulse_shape' custom attribute.
     */
    private PulseShape createPulseShapeFromAttr(int attribute, Paint paint) {
        switch (attribute) {
            case PULSE_TYPE_ROUNDED_RECTANGLE:
                return new PulseRoundedRectangle(paint);
            case PULSE_TYPE_CIRCLE:
                return new PulseCircle(paint);
            default:
                Timber.e("Unknown shape type value %d, using circle", attribute);
                return new PulseCircle(paint);
        }
    }

    /**
     * The pulse animation will be clipped in a concentric circle with a given radius. Useful when
     * we need transparency in the middle. Requires the shape to be {@link PulseCircle}, does
     * nothing otherwise.
     *
     * @param radius    Radius in pixels for the circle masking out the animation in the center.
     *     Providing a value &lt;=0 disables the masking.
     */
    public void setCenterCircularMaskRadius(float radius) {
        if (mPulseShape instanceof PulseCircle) {
            if (radius <= 0) {
                // disable the onDraw method (masking)
                setWillNotDraw(true);
                mCircularMaskRadius = RADIUS_NONE;
                mMask = null;
            } else {
                // enable the onDraw method (masking)
                setWillNotDraw(false);
                mCircularMaskRadius = radius;
                // mask is being initialized on demand during #onDraw(Canvas) method (now enabled)
                // to make sure there has already been #onMeasure(int, int) pass
            }
        }
    }

    /**
     * The pulse animation will be clipped in a rounded rectangle of the given size with a given
     * radius. Useful when we need transparency in the middle. Requires the shape to be
     * {@link PulseRoundedRectangle}, does nothing otherwise.
     *
     * @param width    Width in pixels for the rounded rectangle masking out the animation in
     *      the center. Providing a value &lt;=0 disables the masking.
     * @param height   Height in pixels for the rounded rectangle masking out the animation in
     *      the center. Providing a value &lt;=0 disables the masking.
     */
    public void setCenterRoundedRectangleMask(int width, int height) {
        if (mPulseShape instanceof PulseRoundedRectangle) {
            if (width < 1 || height < 1) {
                // disable the onDraw method (masking)
                setWillNotDraw(true);
                mRoundedRectangleMaskWidth = 0;
                mRoundedRectangleMaskHeight = 0;
                mMask = null;
            } else {
                // enable the onDraw method (masking)
                setWillNotDraw(false);
                mRoundedRectangleMaskWidth = width;
                mRoundedRectangleMaskHeight = height;
                // mask is being initialized on demand during #onDraw(Canvas) method (now enabled)
                // to make sure there has already been #onMeasure(int, int) pass
            }
        }
    }

    /**
     * Start pulse animation. If the start request comes and there are no animations to run
     * (e.g. the view has been re-attached to a view before (and animations cleared), it is
     * reinitialized.
     */
    public synchronized void start() {

        if (mAnimators == null) {
            reset();
            if (mAnimators == null) {
                return;
            }
        }

        if (mIsStarted) {
            return;
        }

        for (int x = 0; x < mAnimators.size(); x++) {
            ObjectAnimator objectAnimator = (ObjectAnimator) mAnimators.get(x);

            if (!mStartFromScratch) {
                // instead of delaying the animation, fast-forward it
                long delay = objectAnimator.getStartDelay();
                objectAnimator.setStartDelay(0);

                // This is where it starts to get tricky. The documentation of
                // ValueAnimator#setCurrentPlayTime(long) is a bit confusing about whether it
                // should be called before or after starting the animation itself. The truth is,
                // it seems the behavior differs between Android versions. If it gets called at
                // a wrong time, only some of the animations will start while others will not or
                // the animated object won't be visible at all.
                boolean shouldStartBeforeSettingCurrentTime =
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1;

                if (shouldStartBeforeSettingCurrentTime) {
                    objectAnimator.start();
                }
                objectAnimator.setCurrentPlayTime(mDuration - delay);
                if (!shouldStartBeforeSettingCurrentTime) {
                    objectAnimator.start();
                }
            } else {
                objectAnimator.start();
            }
        }
    }

    /**
     * Stop pulse animation.
     */
    public synchronized void stop() {
        if (mAnimators == null || !mIsStarted) {
            return;
        }
        for (Animator animator : mAnimators) {
            animator.end();
        }
    }

    public synchronized boolean isStarted() {
        return (mAnimators != null && mIsStarted);
    }

    /**
     * Get number of pulses.
     *
     * @return Number of pulses
     */
    public int getCount() {
        return mCount;
    }

    /**
     * Set number of pulses.
     *
     * @param count Number of pulses
     */
    public void setCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count cannot be negative");
        }

        if (count != mCount) {
            mCount = count;
            reset();
            invalidate();
        }
    }

    /**
     * Get pulse duration.
     *
     * @return Duration of single pulse in milliseconds
     */
    public int getDuration() {
        return mDuration;
    }

    /**
     * Set single pulse duration.
     *
     * @param millis Pulse duration in milliseconds
     */
    public void setDuration(int millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("Duration cannot be negative");
        }

        if (millis != mDuration) {
            mDuration = millis;
            reset();
            invalidate();
        }
    }

    /**
     * Gets the current color of the pulse effect in integer Defaults to Color.rgb(0, 116, 193);
     *
     * @return an integer representation of color
     */
    @ColorInt
    public int getColor() {
        return mColor;
    }

    /**
     * Sets the current color of the pulse effect in integer Takes effect immediately Usage:
     * Color.parseColor("hex-value") or getResources().getColor(R.color.colorAccent)
     *
     * @param color : an integer representation of color
     */
    public void setColor(@ColorInt int color) {
        if (color != mColor) {
            this.mColor = color;

            if (mPaint != null) {
                mPaint.setColor(color);
            }
        }
    }

    /**
     * Get current interpolator type used for animating.
     *
     * @return Interpolator type as int
     */
    public int getInterpolator() {
        return mInterpolator;
    }

    /**
     * Set current interpolator used for animating.
     *
     * @param type Interpolator type as int
     */
    public void setInterpolator(int type) {
        if (type != mInterpolator) {
            mInterpolator = type;
            reset();
            invalidate();
        }
    }

    /**
     * Set how many times the pulse should repeat.
     *
     * @param repeat {@link #INFINITE} for infinite repeat. Also the default value.
     */
    public void setRepeat(int repeat) {
        mRepeat = repeat;
    }

    /**
     * Determines whether the animation starts empty and pulses are added gradually making
     * the animation come from the center or have all the rings present at time of starting.
     * @param startFromScratch <code>true</code> to add pulses one by one from the center.
     * <code>true</code> by default.
     */
    public void setStartFromScratch(boolean startFromScratch) {
        mStartFromScratch = startFromScratch;
    }

    /**
     * Type of shape to be used.
     *
     * @param type {@link ShapeType#PULSE_TYPE_CIRCLE} by default.
     */
    public void setPulseShapeType(@ShapeType int type) {
        mPulseShape = createPulseShapeFromAttr(type, mPaint);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int height = MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom();

        mPulseShape.setSize(width, height);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * vv Remove all views and animators.
     */
    private void clear() {
        // remove animators
        stop();

        // remove old views
        for (View view : mViews) {
            removeView(view);
        }
        mViews.clear();
        mCircularMaskRadius = RADIUS_NONE;
    }

    /**
     * Build pulse views and animators.
     */
    private void build() {
        // create views and animators
        LayoutParams layoutParams = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);

        int repeatCount = (mRepeat == INFINITE) ? ObjectAnimator.INFINITE : mRepeat;

        mAnimators = new ArrayList<>(3 * mCount);
        for (int index = 0; index < mCount; index++) {
            // setup view
            PulseView pulseView = new PulseView(getContext());
            pulseView.setScaleX(0);
            pulseView.setScaleY(0);
            pulseView.setAlpha(1);

            addView(pulseView, index, layoutParams);
            mViews.add(pulseView);

            long delay = index * mDuration / mCount;

            // setup animators
            ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(pulseView, "ScaleX", 0f, 1f);
            scaleXAnimator.setStartDelay(delay);
            mAnimators.add(scaleXAnimator);

            ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(pulseView, "ScaleY", 0f, 1f);
            scaleYAnimator.setStartDelay(delay);
            mAnimators.add(scaleYAnimator);

            ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(pulseView, "Alpha", 1f, 0f);
            alphaAnimator.setStartDelay(delay);
            mAnimators.add(alphaAnimator);
        }

        for (Animator animator : mAnimators) {
            ObjectAnimator objectAnimator = (ObjectAnimator) animator;
            objectAnimator.setRepeatCount(repeatCount);
            objectAnimator.setRepeatMode(ObjectAnimator.RESTART);
            objectAnimator.setInterpolator(createInterpolator(mInterpolator));
            objectAnimator.setDuration(mDuration);
        }

        if (mAnimators.isEmpty()) {
            mAnimators = null;
        } else {
            mAnimators.get(0).addListener(mAnimatorStartListener);
            mAnimators.get(mAnimators.size() - 1).addListener(mAnimatorEndListener);
        }
    }

    /**
     * Reset views and animations.
     */
    private void reset() {
        boolean isStarted = isStarted();

        clear();
        build();

        if (isStarted) {
            start();
        }
    }

    /**
     * Create interpolator from type.
     *
     * @param type Interpolator type as int
     * @return Interpolator object of type
     */
    private static Interpolator createInterpolator(int type) {
        switch (type) {
            case INTERP_ACCELERATE:
                return new AccelerateInterpolator();
            case INTERP_DECELERATE:
                return new DecelerateInterpolator();
            case INTERP_ACCELERATE_DECELERATE:
                return new AccelerateDecelerateInterpolator();
            default:
                return new LinearInterpolator();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mAnimators != null) {
            for (Animator animator : mAnimators) {
                animator.cancel();
            }
            mAnimators = null;
        }
    }

    private class PulseView extends View {

        public PulseView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            mPulseShape.draw(canvas);
        }

    }

    private class AnimatorSimpleListener implements Animator.AnimatorListener {

        @Override
        public void onAnimationStart(Animator animation) {

        }

        @Override
        public void onAnimationEnd(Animator animation) {

        }

        @Override
        public void onAnimationCancel(Animator animation) {

        }

        @Override
        public void onAnimationRepeat(Animator animation) {

        }
    }

    private final AnimatorSimpleListener mAnimatorStartListener = new AnimatorSimpleListener() {

        @Override
        public void onAnimationStart(Animator animator) {
            mIsStarted = true;
        }

    };

    private final AnimatorSimpleListener mAnimatorEndListener = new AnimatorSimpleListener() {

        @Override
        public void onAnimationEnd(Animator animator) {
            mIsStarted = false;
        }

        @Override
        public void onAnimationCancel(Animator animator) {
            mIsStarted = false;
        }

    };

}
