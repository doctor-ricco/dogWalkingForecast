package garagem.ideias.dogwalkingforecast.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
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
    private float textSize = 48f;

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
        backgroundPaint.setStrokeWidth(8f);
        backgroundPaint.setAntiAlias(true);
        backgroundPaint.setStrokeCap(Paint.Cap.ROUND);

        scorePaint = new Paint();
        scorePaint.setStyle(Paint.Style.STROKE);
        scorePaint.setStrokeWidth(12f);  // Reduced from 16f to 12f for a cleaner look
        scorePaint.setAntiAlias(true);
        scorePaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint();
        textPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_date));
        textPaint.setTextSize(textSize);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));  // Just using one method for bold

        circleRect = new RectF();
    }

    public void setScore(int score) {
        this.score = score;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        // Calculate the dimensions of the circle
        float padding = backgroundPaint.getStrokeWidth();
        circleRect.set(padding, padding, w - padding, h - padding);
        
        // Adjust text size based on view size
        textSize = w / 4f;
        textPaint.setTextSize(textSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Draw background circle
        canvas.drawArc(circleRect, 0, 360, false, backgroundPaint);

        // Draw score arc with rounded ends
        float sweepAngle = (score * 360) / 100f;
        scorePaint.setColor(getScoreColor(score));
        canvas.drawArc(circleRect, -90, sweepAngle, false, scorePaint);

        // Draw score text
        String scoreText = String.valueOf(score);
        float textX = width / 2f;
        float textY = height / 2f - ((textPaint.descent() + textPaint.ascent()) / 2);
        
        // Draw percentage symbol smaller with same color as main text
        String percentSymbol = "%";
        Paint percentPaint = new Paint(textPaint);
        percentPaint.setTextSize(textPaint.getTextSize() * 0.5f);
        percentPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_date));
        percentPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));  // Same bold style as number
        
        canvas.drawText(scoreText, textX, textY, textPaint);
        canvas.drawText(percentSymbol, 
            textX + textPaint.measureText(scoreText) * 0.7f,
            textY - textPaint.getTextSize() * 0.2f, 
            percentPaint);
    }

    private int getScoreColor(int score) {
        if (score >= 90) {
            return ContextCompat.getColor(getContext(), R.color.score_excellent);  // Green
        } else if (score >= 70) {
            return ContextCompat.getColor(getContext(), R.color.score_good);       // Light Green
        } else if (score >= 50) {
            return ContextCompat.getColor(getContext(), R.color.score_moderate);   // Yellow
        } else if (score >= 30) {
            return ContextCompat.getColor(getContext(), R.color.score_poor);       // Orange
        } else {
            return ContextCompat.getColor(getContext(), R.color.score_bad);        // Red
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = 120;
        int finalSize = resolveSize(size, widthMeasureSpec);
        setMeasuredDimension(finalSize, finalSize);
    }
} 