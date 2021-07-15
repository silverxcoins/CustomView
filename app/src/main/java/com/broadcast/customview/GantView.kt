package com.broadcast.customview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import java.time.LocalDate
import java.time.temporal.IsoFields

class GantView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // region Paint

    // Для строк
    private val rowPaint = Paint().apply { style = Paint.Style.FILL }

    // Для разделителей
    private val separatorsPaint = Paint().apply {
        strokeWidth = resources.getDimension(R.dimen.gant_separator_width)
        color = ContextCompat.getColor(context, R.color.grey_300)
    }

    // Для названий периодов
    private val periodNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.getDimension(R.dimen.gant_period_name_text_size)
        color = ContextCompat.getColor(context, R.color.grey_500)
    }

    // Для фигур тасок
    private val taskShapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Для названий тасок
    private val taskNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.getDimension(R.dimen.gant_task_name_text_size)
        color = Color.WHITE
    }

    // Для вырезания
    private val cutOutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    // Paint для

    // endregion

    // region Цвета и размеры

    // Ширина столбца с периодом
    private val periodWidth = resources.getDimensionPixelSize(R.dimen.gant_period_width)

    // Высота строки
    private val rowHeight = resources.getDimensionPixelSize(R.dimen.gant_row_height)

    // Радиус скругления углов таски
    private val taskCornerRadius = resources.getDimension(R.dimen.gant_task_corner_radius)

    // Вертикальный отступ таски внутри строки
    private val taskVerticalMargin = resources.getDimension(R.dimen.gant_task_vertical_margin)

    // Горизонтальный отступ текста таски внутри ее фигуры
    private val taskTextHorizontalMargin = resources.getDimension(R.dimen.gant_task_text_horizontal_margin)

    // Радиус круга, вырезаемого из фигуры таски
    private val cutOutRadius = (rowHeight - taskVerticalMargin * 2) / 4

    // Чередующиеся цвета строк
    private val rowColors = listOf(
        ContextCompat.getColor(context, R.color.grey_100),
        Color.WHITE
    )

    // Цвета градиента
    private val gradientStartColor = ContextCompat.getColor(context, R.color.blue_700)
    private val gradientEndColor = ContextCompat.getColor(context, R.color.blue_200)

    private val contentWidth: Int
        get() = periodWidth * periods.getValue(periodType).size

    // endregion

    // region Вспомогательные сущности для рисования

    // Rect для рисования строк
    private val rowRect = Rect()

    private lateinit var bitmap: Bitmap

    // endregion

    // region Время
    private val today = LocalDate.now()

    private var periodType = PeriodType.MONTH
    private val periods = initPeriods()
    // endregion

    // region Вспомогательные сущности для обработки Touch эвентов

    // Значения последнего эвента
    private val lastPoint = PointF()
    private var lastPointerId = 0

    // Отвечает за зум и сдвиги
    private val transformations = Transformations()

    // endregion

    private var tasks: List<Task> = emptyList()
    private var uiTasks: List<UiTask> = emptyList()

    fun setTasks(tasks: List<Task>) {
        if (tasks != this.tasks) {
            this.tasks = tasks
            uiTasks = tasks.map(::UiTask)
            updateTasksRects()

            // Сообщаем, что нужно пересчитать размеры
            requestLayout()
            // Сообщаем, что нужно перерисоваться
            invalidate()
        }
    }

    // region Измерения размеров

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            contentWidth
        } else {
            // Даже если AT_MOST занимаем все доступное место, т.к. может быть зум
            MeasureSpec.getSize(widthMeasureSpec)
        }

        // Высота всех строк с тасками + строки с периодами
        val contentHeight = rowHeight * (tasks.size + 1)
        val heightSpecSize = MeasureSpec.getSize(heightMeasureSpec)
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            // Нас никто не ограничивает - занимаем размер контента
            MeasureSpec.UNSPECIFIED -> contentHeight
            // Ограничение "не больше, не меньше" - занимаем столько, сколько пришло в спеке
            MeasureSpec.EXACTLY -> heightSpecSize
            // Можно занять меньше места, чем пришло в спеке, но не больше
            MeasureSpec.AT_MOST -> contentHeight.coerceAtMost(heightSpecSize)
            // Успокаиваем компилятор, сюда не попадем
            else -> error("Unreachable")
        }

        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Размер изменился, надо пересчитать ширину строки
        rowRect.set(0, 0, w, rowHeight)
        // И размер градиента
        taskShapePaint.shader = LinearGradient(
            0f,
            0f,
            w.toFloat(),
            0f,
            gradientStartColor,
            gradientEndColor,
            Shader.TileMode.CLAMP
        )
        // И прямоугольники тасок
        updateTasksRects()

        // Перерисовываем bitmap
        bitmap = createBitmap(contentWidth, h).applyCanvas {
            drawPeriods()
            drawTasks()
        }
    }

    private fun updateTasksRects() {
        uiTasks.forEachIndexed { index, uiTask -> uiTask.updateRect(index) }
        // Пересчитываем что необходимо и применяем предыдущие трансформации
        transformations.recalculate()
    }

    // endregion

    // region Рисование

    override fun onDraw(canvas: Canvas) = with(canvas) {
        drawRows()
        withTranslation(x = transformations.translationX) {
            drawBitmap(bitmap, 0f, 0f, rowPaint)
        }
    }

    private fun Canvas.drawRows() {
        repeat(tasks.size + 1) { index ->
            // Rect для строки создан заранее, чтобы не создавать объекты во время отрисовки, но мы можем его подвигать
            rowRect.offsetTo(0, rowHeight * index)
            if (rowRect.top < height) {
                // Чередуем цвета строк
                rowPaint.color = rowColors[index % rowColors.size]
                drawRect(rowRect, rowPaint)
            }
        }
        // Разделитель между периодами и задачами
        val horizontalSeparatorY = rowHeight.toFloat()
        drawLine(0f, horizontalSeparatorY, width.toFloat(), horizontalSeparatorY, separatorsPaint)
    }

    private fun Canvas.drawPeriods() {
        val currentPeriods = periods.getValue(periodType)
        val nameY = periodNamePaint.getTextBaselineByCenter(rowHeight / 2f)
        currentPeriods.forEachIndexed { index, periodName ->
            // По X текст рисуется относительно его начала
            val nameX = periodWidth * (index + 0.5f) - periodNamePaint.measureText(periodName) / 2
            drawText(periodName, nameX, nameY, periodNamePaint)
            // Разделитель
            val separatorX = periodWidth * (index + 1f)
            drawLine(separatorX, 0f, separatorX, height.toFloat(), separatorsPaint)
        }
    }

    private fun Canvas.drawTasks() {
        uiTasks.forEach { uiTask ->
            val taskRect = uiTask.rect
            val taskName = uiTask.task.name

            drawRoundRect(taskRect, taskCornerRadius, taskCornerRadius, taskShapePaint)
            drawCircle(taskRect.left, taskRect.centerY(), cutOutRadius, cutOutPaint)

            // Расположение названия
            val textX = taskRect.left + taskTextHorizontalMargin + cutOutRadius
            val textY = taskNamePaint.getTextBaselineByCenter(taskRect.centerY())
            // Количество символов из названия, которые поместятся в фигуру
            val charsCount = taskNamePaint.breakText(
                taskName,
                true,
                taskRect.width() - taskTextHorizontalMargin * 2 - cutOutRadius,
                null
            )
            drawText(taskName.substring(startIndex = 0, endIndex = charsCount), textX, textY, taskNamePaint)
        }
    }

    private fun Paint.getTextBaselineByCenter(center: Float) = center - (descent() + ascent()) / 2

    // endregion

    // region Тачи
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return false

        return if (event.pointerCount == 1) processMove(event) else false
    }

    private fun processMove(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastPoint.set(event.x, event.y)
                lastPointerId = event.getPointerId(0)
                true
            }

            MotionEvent.ACTION_MOVE -> {
                // Если размер контента меньше размера View - сдвиг недоступен
                if (width < contentWidth) {
                    val pointerId = event.getPointerId(0)
                    // Чтобы избежать скачков - сдвигаем, только если поинтер(палец) тот же, что и раньше
                    if (lastPointerId == pointerId) {
                        transformations.addTranslation(event.x - lastPoint.x)
                    }

                    // Запоминаем поинтер и последнюю точку в любом случае
                    lastPoint.set(event.x, event.y)
                    lastPointerId = event.getPointerId(0)

                    true
                } else {
                    false
                }
            }

            else -> false
        }
    }

    // endregion

    private fun initPeriods(): Map<PeriodType, List<String>> {
        // Один раз получаем все названия периодов для каждого из PeriodType
        return PeriodType.values().associateWith { periodType ->
            val startDate = today.minusMonths(MONTH_COUNT)
            val endDate = today.plusMonths(MONTH_COUNT)
            var lastDate = startDate
            mutableListOf<String>().apply {
                while (lastDate <= endDate) {
                    add(periodType.getDateString(lastDate))
                    lastDate = periodType.increment(lastDate)
                }
            }
        }
    }

    private inner class UiTask(val task: Task) {
        val rect = RectF()

        fun updateRect(index: Int) {
            fun getX(date: LocalDate): Float? {
                val periodIndex = periods.getValue(periodType).indexOf(periodType.getDateString(date))
                return if (periodIndex >= 0) {
                    periodWidth * (periodIndex + periodType.getPercentOfPeriod(date))
                } else {
                    null
                }
            }

            rect.set(
                getX(task.dateStart) ?: -taskCornerRadius,
                rowHeight * (index + 1f) + taskVerticalMargin,
                getX(task.dateEnd) ?: width + taskCornerRadius,
                rowHeight * (index + 2f) - taskVerticalMargin,
            )
        }
    }

    private inner class Transformations {
        var translationX = 0f
            private set

        // На сколько максимально можно сдвинуть диаграмму
        private val minTranslation: Float
            get() = (width - contentWidth).toFloat().coerceAtMost(0f)

        // Относительный сдвиг на dx
        fun addTranslation(dx: Float) {
            translationX = (translationX + dx).coerceIn(minTranslation, 0f)
            invalidate()
        }

        // Пересчет текущих значений
        fun recalculate() {
            recalculateTranslationX()
        }

        // Когда изменился размер View надо пересчитать сдвиг
        private fun recalculateTranslationX() {
            translationX = translationX.coerceIn(minTranslation, 0f)
        }
    }

    private enum class PeriodType {
        MONTH {
            override fun increment(date: LocalDate): LocalDate = date.plusMonths(1)

            override fun getDateString(date: LocalDate): String = date.month.name

            override fun getPercentOfPeriod(date: LocalDate): Float = (date.dayOfMonth - 1f) / date.lengthOfMonth()
        },
        WEEK {
            override fun increment(date: LocalDate): LocalDate = date.plusWeeks(1)

            override fun getDateString(date: LocalDate): String = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString()

            override fun getPercentOfPeriod(date: LocalDate): Float = (date.dayOfWeek.value - 1f) / 7
        };

        abstract fun increment(date: LocalDate): LocalDate

        abstract fun getDateString(date: LocalDate): String

        abstract fun getPercentOfPeriod(date: LocalDate): Float
    }

    companion object {
        // Количество месяцев до и после текущей даты
        private const val MONTH_COUNT = 2L
        private const val MAX_SCALE = 2f
    }
}

data class Task(
    val name: String,
    val dateStart: LocalDate,
    val dateEnd: LocalDate,
)