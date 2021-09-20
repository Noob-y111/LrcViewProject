package com.example.lrcviewproject

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.Scroller
import androidx.annotation.ColorInt
import java.lang.Exception
import kotlin.math.abs
import kotlin.math.roundToInt

class LrcView : View {

    companion object {
        const val TAG = "LrcView"
    }

    data class LyricLine(var text: String, var startTime: Int, var endTime: Int)
    enum class Direction { UP, DOWN, STOP }

    //lyrics
    private var lyrics: ArrayList<LyricLine>? = null

    //attrs
    private var normalSize = spToPx(13f)
    private var highlightSize = spToPx(15f)
    private var normalHeight = 0f
    private var highlightHeight = 0f
    private var currentTime = 0
    private var currentIndex = 0
    private var minWidth = dpToPx(100f)
    private var minHeight = dpToPx(100f)
    private var lineHeight = dpToPx(20f)
    private var centerLineWidth = dpToPx(1f)

    @ColorInt
    private var centerLineColor = Color.LTGRAY

    @ColorInt
    private var normalColor = Color.GRAY

    @ColorInt
    private var highlightColor = Color.BLACK

    //baseline
    private var highlightDy = 0f
    private var normalDy = 0f

    //run time
    private var startY = 0f
    private var downY = 0f
    private var direction = Direction.STOP
    private var maxDownOffset = 0
    private var velocityTracker: VelocityTracker? = null
    private val fingerScroller = Scroller(context)
    private val autoScroller = Scroller(context)
    private var isTouched = false
    private var isDragged = false
    private var clickListener: ((time: Int) -> Unit)? = null

    private val highlightLinePaint = TextPaint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }
    private val normalLinePaint = TextPaint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }
    private val centerLinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(3f, 2f), 0f)
    }

    private fun dpToPx(float: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            float,
            resources.displayMetrics
        )
    }

    private fun spToPx(float: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            float,
            resources.displayMetrics
        )
    }

    override fun performClick() = super.performClick()

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        getAttrs(context, attrs)
    }

    private fun getAttrs(context: Context, attrs: AttributeSet?) {
        context.obtainStyledAttributes(attrs, R.styleable.LrcView).apply {
            normalSize = getDimension(R.styleable.LrcView_normalLineSize, normalSize)
            highlightSize = getDimension(R.styleable.LrcView_highlightLineSize, highlightSize)
            normalColor = getColor(R.styleable.LrcView_normalLineColor, normalColor)
            highlightColor = getColor(R.styleable.LrcView_highlightLineColor, highlightColor)
            lineHeight = getDimension(R.styleable.LrcView_lineHeight, lineHeight)
            centerLineWidth = getDimension(R.styleable.LrcView_centerLineWidth, centerLineWidth)
            centerLineColor = getColor(R.styleable.LrcView_centerLineWidth, centerLineColor)

            Log.d(TAG, "getAttrs: highlightSize ==> $highlightSize")
            Log.d(TAG, "getAttrs: normalSize ==> $normalSize")

            recycle()
        }
        initPaint()
        baseLine()
    }

    private fun initPaint() {
        highlightLinePaint.apply {
            textSize = highlightSize
            color = highlightColor
        }
        highlightHeight =
            (highlightLinePaint.fontMetrics.bottom - highlightLinePaint.fontMetrics.top)
        Log.d(TAG, "initPaint: highlightHeight ==> $highlightHeight")

        normalLinePaint.apply {
            textSize = normalSize
            color = normalColor
        }
        normalHeight =
            (normalLinePaint.fontMetrics.bottom - normalLinePaint.fontMetrics.top)
        Log.d(TAG, "initPaint: normalHeight ==> $normalHeight")

        centerLinePaint.apply {
            strokeWidth = centerLineWidth
            color = centerLineColor
        }
    }

    private fun baseLine() {
        val highlightFontMetrics = highlightLinePaint.fontMetrics
        val normalFontMetrics = normalLinePaint.fontMetrics
        highlightDy =
            (highlightFontMetrics.bottom - highlightFontMetrics.top) / 2 - highlightFontMetrics.bottom
        normalDy =
            (normalFontMetrics.bottom - normalFontMetrics.top) / 2 - normalFontMetrics.bottom

        Log.d(TAG, "baseLine: highlightDy ==> $highlightDy")
        Log.d(TAG, "baseLine: normalDy ==> $normalDy")
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        Log.d(TAG, "onMeasure: width ==> $width")
        Log.d(TAG, "onMeasure: height ==> $height")

        val targetWidth: Int = if (width < minWidth) minWidth.toInt() else {
            minWidth = width.toFloat()
            width
        }

        val targetHeight: Int = if (height < minHeight)
            minHeight.toInt()
        else {
            minHeight = height.toFloat()
            height
        }

        setMeasuredDimension(
            targetWidth - (paddingStart + paddingEnd),
            targetHeight - (paddingTop + paddingBottom)
        )
    }

    override fun onDraw(canvas: Canvas?) {
        lyrics?.let {
            if (it.size <= 0) return
            canvas?.let { mCanvas ->
                drawLyrics(mCanvas)
                drawLineInCenter(mCanvas)
            }
        }
    }

    private fun drawLyrics(canvas: Canvas) {
        canvas.save()
        lyrics!!.forEachIndexed { index, it ->
            if (currentTime >= it.startTime && currentTime < it.endTime) {
                drawHighlightLine(canvas, it.text)
                currentIndex = index
            } else {
                drawNormalLine(canvas, it.text)
            }
            canvas.translate(0f, normalSize + lineHeight)
        }
        canvas.restore()
    }

    private fun drawLineInCenter(canvas: Canvas) {
        if (isDragged) {
            canvas.drawLine(
                0f,
                (measuredHeight / 2).toFloat() + scrollY,
                measuredWidth.toFloat(),
                (measuredHeight / 2).toFloat() + scrollY,
                centerLinePaint
            )
        }
    }

    private fun drawTextLine(canvas: Canvas, lyric: String, paint: TextPaint, dy: Float) {
        canvas.drawText(lyric, (width / 2).toFloat(), height / 2 + dy, paint)
    }

    private fun drawNormalLine(canvas: Canvas, lyric: String) {
        drawTextLine(canvas, lyric, normalLinePaint, normalDy)
    }

    private fun drawHighlightLine(canvas: Canvas, lyric: String) {
        drawTextLine(canvas, lyric, highlightLinePaint, highlightDy)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        performClick()
        velocityTracker ?: kotlin.run {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)
        event?.let { motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    isTouched = true
                    startY = motionEvent.y
                    downY = motionEvent.y
                }
                MotionEvent.ACTION_MOVE -> {
                    isDragged = true
                    moveView(motionEvent.y)
                }
                MotionEvent.ACTION_UP -> {
                    click(motionEvent.y)
                    isTouched = false
                    isDragged = false
                    velocityTracker?.computeCurrentVelocity(1000)
                    inertialMove(velocityTracker?.yVelocity, motionEvent.y)
                }
                else -> {

                }
            }
        }
        invalidate()
        return true
    }

    private fun click(y: Float) {
        clickListener?.let {
            if (isDragged) return
            val touchLineTime = touchLineTime(y)
            it(touchLineTime)
        }
    }

    private fun touchLineTime(y: Float): Int {
        val b = lineHeight + highlightHeight
        val a = lineHeight + normalHeight
        val dy = scrollY + y
        val touchIndex = ((dy - b) / a + 1).roundToInt()
        Log.d(TAG, "touchLineTime: touchIndex ==> $touchIndex")
        return lyrics!![touchIndex].startTime
    }

    override fun computeScroll() {
        super.computeScroll()
        if (isTouched) return
        if (autoScroller.computeScrollOffset()) {
            scrollTo(0, autoScroller.currY)
        }
        if (fingerScroller.computeScrollOffset()) {
            if (direction == Direction.UP) {
                if (scrollY <= 0) return
                scrollTo(0, fingerScroller.currY)
            } else if (direction == Direction.DOWN) {
                if (scrollY >= maxDownOffset) return
                scrollTo(0, fingerScroller.currY)
            }
        }
        invalidate()
    }

    private fun inertialMove(yVelocity: Float?, y: Float) {
        yVelocity?.let {
            Log.d(TAG, "onTouchEvent: velocityTracker?.yVelocity ==> $it")
            if (abs(it) > 7000) {
                if (it < 0)
                    fingerScroller.startScroll(0, scrollY, 0, 1000, 600)
                else
                    fingerScroller.startScroll(0, scrollY, 0, -1000, 600)
                return
            }

            if (abs(y - downY) <= 100) return
            if (abs(it) > 2000) { //速度超过3000
                if (it < 0)
                    fingerScroller.startScroll(0, scrollY, 0, 1000, 600)
                else
                    fingerScroller.startScroll(0, scrollY, 0, -1000, 600)
            }
        }
    }

    private fun moveView(fingerY: Float) {
        val distance = (fingerY - startY)
        updateDirection(distance)
        crossingEdge(distance)
        startY = fingerY
    }

    private fun updateDirection(distanceY: Float) {
        if (distanceY > 0) direction = Direction.UP
        else if (distanceY < 0) direction = Direction.DOWN
    }

    private fun crossingEdge(distanceY: Float) {
        when (direction) {
            Direction.UP -> {
                if (scrollY <= 0) {
                    scrollTo(0, 0)
                } else {
                    scrollBy(0, distanceY.toInt() * -1)
                }
            }
            Direction.DOWN -> {
                if (scrollY >= maxDownOffset) {
                    scrollTo(0, maxDownOffset)
                } else {
                    scrollBy(0, distanceY.toInt() * -1)
                }
            }
            else -> {
            }
        }
    }

    private fun formatLine(strLine: ArrayList<String>): ArrayList<LyricLine> {
        val list = ArrayList<LyricLine>()
        try {
            for (index in 5 until strLine.size) {
                strLine[index].apply {
                    val text = substring(10)
                    if (text.isNotEmpty()) {
                        val minute = substring(1, 3)
                        val second = substring(4, 6)
                        val millisecond = substring(7, 9)
                        val startTime =
                            ((minute.toInt() * 60 * 1000) + second.toInt() * 1000 + millisecond.toInt())
                        list.add(LyricLine(text, startTime, 0))
                    }
                }
            }
            for (index in 1 until list.size) {
                list[index - 1].endTime = list[index].startTime
            }
            list[list.size - 1].endTime = list[list.size - 1].startTime + 100
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("歌词文件不是格式化文件")
        }
        return list
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        velocityTracker?.recycle()
    }

    //public method
    fun toDealWithTheLyrics(behavior: () -> ArrayList<String>?) {
        behavior()?.let {
            formatLine(it).also { list ->
                lyrics = list
                maxDownOffset = ((list.size * (normalSize + lineHeight)).toInt())
                Log.d(TAG, "toDealWithTheLyrics: maxDownOffset ==> $maxDownOffset")
            }
            invalidate()
        }
    }

    fun onClickLine(listener: (time: Int) -> Unit) {
        this.clickListener = listener
    }

    fun seekTo(time: Int) {
        currentTime = time
        lyrics?.let {
            val lyric = it[currentIndex]
            if (!(currentTime > lyric.startTime && currentTime <= lyric.endTime)) {
                autoScroller.startScroll(
                    0,
                    scrollY,
                    0,
                    (((highlightHeight + lineHeight).roundToInt())),
                    500
                )
            }
        }
    }
}