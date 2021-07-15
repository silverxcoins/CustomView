package com.broadcast.customview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.temporal.IsoFields

class GantView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // region Paint

    // Paint для строк
    private val rowPaint = Paint().apply { style = Paint.Style.FILL }
    // Paint для разделителей
    private val separatorsPaint = Paint().apply {
        strokeWidth = resources.getDimension(R.dimen.gant_separator_width)
        color = ContextCompat.getColor(context, R.color.grey_300)
    }
    // Paint для названий периодов
    private val periodNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.getDimension(R.dimen.gant_period_name_text_size)
        color = ContextCompat.getColor(context, R.color.grey_500)
    }

    // endregion

    // region Цвета и размеры

    // Ширина столбца с периодом
    private val periodWidth = resources.getDimensionPixelSize(R.dimen.gant_period_width)
    // Высота строки
    private val rowHeight = resources.getDimensionPixelSize(R.dimen.gant_row_height)

    // Чередующиеся цвета строк
    private val rowColors = listOf(
        ContextCompat.getColor(context, R.color.grey_100),
        Color.WHITE
    )

    // endregion

    // region Вспомогательные сущности

    // Rect для рисования строк
    private val rowRect = Rect()

    // endregion

    // region Время
    private val today = LocalDate.now()
    private val startDate = today.minusMonths(MONTH_COUNT)
    private val endDate = today.plusMonths(MONTH_COUNT)

    private var periodType = PeriodType.MONTH
    private val periods = initPeriods()
    // endregion

    private var tasks: List<Task> = emptyList()

    fun setTasks(tasks: List<Task>) {
        if (tasks != this.tasks) {
            this.tasks = tasks

            // Сообщаем, что нужно пересчитать размеры
            requestLayout()
            // Сообщаем, что нужно перерисоваться
            invalidate()
        }
    }

    // region Измерения размеров

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        fun calculateSize(measureSpec: Int, contentSize: Int): Int {
            // Размер из спеки(ограничений)
            val specSize = MeasureSpec.getSize(measureSpec)
            // Основываясь на mode из спеки рассчитываем итоговый размер
            return when (MeasureSpec.getMode(measureSpec)) {
                // Нас никто не ограничивает - занимаем размер контента
                MeasureSpec.UNSPECIFIED -> contentSize
                // Ограничение "не больше, не меньше" - занимаем столько, сколько пришло в спеке
                MeasureSpec.EXACTLY -> specSize
                // Можно занять меньше места, чем пришло в спеке, но не больше
                MeasureSpec.AT_MOST -> contentSize.coerceAtMost(specSize)
                // Успокаиваем компилятор, сюда не попадем
                else -> error("Unreachable")
            }
        }

        // Ширина всех периодов
        val contentWidth = periodWidth * periods.size
        val width = calculateSize(measureSpec = widthMeasureSpec, contentSize = contentWidth)

        // Высота всех строк с тасками + строки с периодами
        val contentHeight = rowHeight * (tasks.size + 1)
        val height = calculateSize(measureSpec = heightMeasureSpec, contentSize = contentHeight)

        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Размер изменился, надо пересчитать ширину строки
        rowRect.set(0, 0, w, rowHeight)
    }

    // endregion

    // region Рисование

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        with(canvas) {
            drawRows()
            drawSeparators()
            drawPeriodNames()
        }
    }

    private fun Canvas.drawRows() {
        repeat(tasks.size + 1) { index ->
            // Rect для строки создан заранее, чтобы не создавать объекты во время отрисовки, но мы можем его подвигать
            rowRect.offsetTo(0, rowHeight * index)
            // Чередуем цвета строк
            rowPaint.color = rowColors[index % rowColors.size]

            drawRect(rowRect, rowPaint)
        }
    }

    private fun Canvas.drawSeparators() {
        // Разделитель между периодами и задачами
        val horizontalSeparatorY = rowHeight.toFloat()
        drawLine(0f, horizontalSeparatorY, width.toFloat(), horizontalSeparatorY, separatorsPaint)

        // Разделители между периодами
        repeat(periods.getValue(periodType).size) { index ->
            val separatorX = periodWidth * (index + 1f)
            drawLine(separatorX, 0f, separatorX, height.toFloat(), separatorsPaint)
        }
    }

    private fun Canvas.drawPeriodNames() {
        val currentPeriods = periods.getValue(periodType)
        // По Y текст рисуется относительно baseline, нужно немного магии, чтобы расположить его в центре ячейки
        val nameY = rowHeight / 2f - (periodNamePaint.descent() + periodNamePaint.baselineShift) / 2
        currentPeriods.forEachIndexed { index, periodName ->
            // По X текст рисуется относительно его начала
            val nameX = periodWidth * (index + 0.5f) - periodNamePaint.measureText(periodName) / 2
            drawText(periodName, nameX, nameY, periodNamePaint)
        }
    }

    // endregion

    private fun initPeriods(): Map<PeriodType, List<String>> {
        // Один раз получаем все названия периодов для каждого из PeriodType
        return PeriodType.values().associateWith { periodType ->
            mutableListOf<String>().apply {
                var lastDate = startDate
                while (lastDate <= endDate) {
                    add(periodType.getDateString(lastDate))
                    lastDate = periodType.increment(lastDate)
                }
            }
        }
    }

    companion object {
        // Количество месяцев до и после текущей даты
        private const val MONTH_COUNT = 2L
    }

    private enum class PeriodType {
        MONTH {
            override fun increment(date: LocalDate): LocalDate = date.plusMonths(1)

            override fun getDateString(date: LocalDate): String = date.month.name
        },
        WEEK {
            override fun increment(date: LocalDate): LocalDate = date.plusWeeks(1)

            override fun getDateString(date: LocalDate): String = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString()
        };

        abstract fun increment(date: LocalDate): LocalDate

        abstract fun getDateString(date: LocalDate): String
    }
}

data class Task(
    val name: String,
    val dateStart: LocalDate,
    val dateEnd: LocalDate,
)