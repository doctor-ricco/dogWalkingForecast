package garagem.ideias.dogwalkingforecast.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
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
        backgroundPaint.setStrokeWidth(24f);
        backgroundPaint.setAntiAlias(true);
        backgroundPaint.setStrokeCap(Paint.Cap.ROUND);

        scorePaint = new Paint();
        scorePaint.setStyle(Paint.Style.STROKE);
        scorePaint.setStrokeWidth(24f);
        scorePaint.setAntiAlias(true);
        scorePaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint();
        textPaint.setColor(ContextCompat.getColor(getContext(), R.color.score_text));
        textPaint.setTextSize(48f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        textPaint.setFakeBoldText(true);
        
        // Stronger shadow for better depth
        scorePaint.setShadowLayer(6, 0, 3, Color.parseColor("#40000000"));
        
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

        int width = getWidth();
        int height = getHeight();
        int size = Math.min(width, height);
        float strokeWidth = scorePaint.getStrokeWidth();
        float padding = strokeWidth / 2 + 8;

        circleRect.set(padding, padding, size - padding, size - padding);

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
        
        // Draw percentage symbol smaller
        String percentSymbol = "%";
        Paint percentPaint = new Paint(textPaint);
        percentPaint.setTextSize(textPaint.getTextSize() * 0.5f);
        
        canvas.drawText(scoreText, textX, textY, textPaint);
        canvas.drawText(percentSymbol, 
            textX + textPaint.measureText(scoreText) * 0.7f,
            textY - textPaint.getTextSize() * 0.2f, 
            percentPaint);
    }

    private int getScoreColor(int score) {
        if (score >= 90) return ContextCompat.getColor(getContext(), R.color.score_excellent);
        if (score >= 70) return ContextCompat.getColor(getContext(), R.color.score_good);
        if (score >= 50) return ContextCompat.getColor(getContext(), R.color.score_moderate);
        if (score >= 30) return ContextCompat.getColor(getContext(), R.color.score_poor);
        return ContextCompat.getColor(getContext(), R.color.score_bad);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = 120;
        int finalSize = resolveSize(size, widthMeasureSpec);
        setMeasuredDimension(finalSize, finalSize);
    }
} 