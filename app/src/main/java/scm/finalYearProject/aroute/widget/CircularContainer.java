package scm.finalYearProject.aroute.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import scm.finalYearProject.aroute.R;

public class CircularContainer extends CardView {

    private float tlRadiu;
    private float trRadiu;
    private float brRadiu;
    private float blRadiu;


    public CircularContainer(Context context)   {
        this(context, null);
    }

    public CircularContainer(Context context, AttributeSet attrs)   {
        this(context, attrs, com.google.android.material.R.attr.materialCardViewStyle);
    }

    public CircularContainer(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setRadius(0);
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.CircularContainer);
        tlRadiu = array.getDimension(R.styleable.CircularContainer_rcv_topLeftRadiu, 0);
        trRadiu = array.getDimension(R.styleable.CircularContainer_rcv_topRightRadiu, 0);
        brRadiu = array.getDimension(R.styleable.CircularContainer_rcv_bottomRightRadiu, 0);
        blRadiu = array.getDimension(R.styleable.CircularContainer_rcv_bottomLeftRadiu, 0);
        setBackground(new ColorDrawable());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Path path = new Path();
        RectF rectF = getRectF();
        float[] radius = {tlRadiu,tlRadiu,trRadiu,trRadiu,brRadiu,brRadiu,blRadiu,blRadiu};
        path.addRoundRect(rectF,radius,Path.Direction.CW);
        canvas.clipPath(path, Region.Op.INTERSECT);
        super.onDraw(canvas);
    }

    private RectF getRectF()    {
        Rect rect = new Rect();
        getDrawingRect(rect);
        RectF rectF = new RectF(rect);
        return rectF;
    }
}
