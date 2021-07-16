package com.broadcast.customview

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
    private val periodNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
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

    // Для откусывания поолукруга от таски
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

    // Чередующиеся цвета строк
    private val rowColors = listOf(
        ContextCompat.getColor(context, R.color.grey_100),
        Color.WHITE
    )

    // endregion

    // region Вспомогательные сущности

    private val contentWidth: Int
        get() = periodWidth * periods.getValue(periodType).size

    // Rect для рисования строк
    private val rowRect = Rect()

    // endregion

    // region Время
    private val today = LocalDate.now()
    private val startDate = today.minusMonths(MONTH_COUNT)
    private val endDate = today.plusMonths(MONTH_COUNT)
    private val allDaysCount = ChronoUnit.DAYS.between(startDate, endDate).toFloat()

    private var periodType = PeriodType.MONTH
    private val periods = initPeriods()
    // endregion

    private var tasks: List<Task> = emptyList()
    private var uiTasks: List<UiTask> = emptyList()

    fun setTasks(tasks: List<Task>) {
        if (tasks != this.tasks) {
            this.tasks = tasks
            uiTasks = tasks.mapIndexed { index, task ->
                UiTask(task).apply { updateRect(index) }
            }

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

        val width = calculateSize(measureSpec = widthMeasureSpec, contentSize = contentWidth)

        // Высота всех строк с тасками + строки с периодами
        val contentHeight = rowHeight * (tasks.size + 1)
        val height = calculateSize(measureSpec = heightMeasureSpec, contentSize = contentHeight)

        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Размер изменился, надо пересчитать ширину строки
        rowRect.set(0, 0, w, rowHeight)
        // И расположение тасок
        uiTasks.forEachIndexed { index, uiTask -> uiTask.updateRect(index) }
        // И размер градиента
        taskShapePaint.shader = LinearGradient(
            0f,
            0f,
            w.toFloat(),
            0f,
            ContextCompat.getColor(context, R.color.blue_600),
            Color.WHITE,
            Shader.TileMode.CLAMP
        )
    }

    // endregion

    // region Рисование

    override fun onDraw(canvas: Canvas) = with(canvas) {
        drawRows()
        drawSeparators()
        drawPeriodNames()
        drawTasks()
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
        val nameY = periodNamePaint.getTextBaselineByCenter(rowHeight / 2f)
        currentPeriods.forEachIndexed { index, periodName ->
            // По X текст рисуется относительно его начала
            val nameX = periodWidth * (index + 0.5f) - periodNamePaint.measureText(periodName) / 2
            drawText(periodName, nameX, nameY, periodNamePaint)
        }
    }

    private fun Canvas.drawTasks() {
        uiTasks.forEach { uiTask ->
            val taskRect = uiTask.rect
            val taskName = uiTask.task.name
            // Фигура таски
            drawRoundRect(taskRect, taskCornerRadius, taskCornerRadius, taskShapePaint)
            // Откусываем кусок от фигуры
            drawCircle(taskRect.left, taskRect.centerY(), taskRect.height() / 4f, cutOutPaint)
            // Расположение названия
            val textX = taskRect.left + taskTextHorizontalMargin
            val textY = taskNamePaint.getTextBaselineByCenter(taskRect.centerY())
            // Количество символов из названия, которые поместятся в фигуру
            val charsCount = taskNamePaint.breakText(
                taskName,
                true,
                taskRect.width() - taskTextHorizontalMargin * 2,
                null
            )
            drawText(taskName.substring(startIndex = 0, endIndex = charsCount), textX, textY, taskNamePaint)
        }
    }

    private fun Paint.getTextBaselineByCenter(center: Float) = center - (descent() + ascent()) / 2

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

    private inner class UiTask(val task: Task) {
        val rect = RectF()

        fun updateRect(index: Int) {
            val startPercent = ChronoUnit.DAYS.between(startDate, task.dateStart) / allDaysCount
            val endPercent = ChronoUnit.DAYS.between(startDate, task.dateEnd) / allDaysCount
            rect.set(
                contentWidth * startPercent,
                rowHeight * (index + 1f) + taskVerticalMargin,
                contentWidth * endPercent,
                rowHeight * (index + 2f) - taskVerticalMargin,
            )
        }
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

    companion object {
        // Количество месяцев до и после текущей даты
        private const val MONTH_COUNT = 2L
    }
}

data class Task(
    val name: String,
    val dateStart: LocalDate,
    val dateEnd: LocalDate,
)