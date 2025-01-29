package garagem.ideias.dogwalkingforecast.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;
import garagem.ideias.dogwalkingforecast.R;

public class CircularScoreView extends View {
    private Paint backgroundPaint;
    private Paint scorePaint;
    private Paint textPaint;
    private RectF circleRect;
    private int score = 0;

    public CircularScoreView(Context context) {
        super(context);
        init();
    }

    public CircularScoreView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        backgroundPaint = new Paint();
        backgroundPaint.setColor(ContextCompat.getColor(getContext(), R.color.score_background));
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(20f);
        backgroundPaint.setAntiAlias(true);

        scorePaint = new Paint();
        scorePaint.setStyle(Paint.Style.STROKE);
        scorePaint.setStrokeWidth(10f);
        scorePaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(ContextCompat.getColor(getContext(), R.color.score_text));
        textPaint.setTextSize(50f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        textPaint.setFakeBoldText(true);

        circleRect = new RectF();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int padding = 10;
        circleRect.set(padding, padding, w - padding, h - padding);
    }

    public void setScore(int score) {
        this.score = score;
        updateScoreColor();
        invalidate();
    }

    private void updateScoreColor() {
        int colorResId;
        if (score >= 90) colorResId = R.color.score_excellent;
        else if (score >= 70) colorResId = R.color.score_good;
        else if (score >= 50) colorResId = R.color.score_moderate;
        else if (score >= 30) colorResId = R.color.score_poor;
        else colorResId = R.color.score_bad;
        
        scorePaint.setColor(ContextCompat.getColor(getContext(), colorResId));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Draw background circle
        canvas.drawArc(circleRect, 0, 360, false, backgroundPaint);
        
        // Draw score arc
        float sweepAngle = (score * 360) / 100f;
        canvas.drawArc(circleRect, -90, sweepAngle, false, scorePaint);
        
        // Draw score text
        float centerX = circleRect.centerX();
        float centerY = circleRect.centerY();
        canvas.drawText(score + "%", centerX, centerY + 15, textPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = 120;
        int finalSize = resolveSize(size, widthMeasureSpec);
        setMeasuredDimension(finalSize, finalSize);
    }
} 