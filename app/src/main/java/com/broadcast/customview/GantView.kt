package com.broadcast.customview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Parcel
import android.os.Parcelable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.math.abs

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

    // Обнаружение и расчет скейла
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())

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

    // region Сохранение состояния

    override fun onSaveInstanceState(): Parcelable {
        return SavedState(super.onSaveInstanceState()).also(transformations::onSaveInstanceState)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            transformations.onRestoreInstanceState(state)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    // endregion

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
            MeasureSpec.AT_MOST -> heightSpecSize.coerceAtMost(heightSpecSize)
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
    }

    private fun updateTasksRects() {
        uiTasks.forEachIndexed { index, uiTask -> uiTask.updateInitialRect(index) }
        // Пересчитываем что необходимо и применяем предыдущие трансформации
        transformations.recalculate()
    }

    // endregion

    // region Рисование

    override fun onDraw(canvas: Canvas) = with(canvas) {
        drawRows()
        drawPeriods()
        drawTasks()
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
            val textWidth = periodNamePaint.measureText(periodName)
            val periodCenter = periodWidth * transformations.scaleX * (index + 0.5f)
            val nameX = (periodCenter - textWidth / 2) + transformations.translationX
            drawText(periodName, nameX, nameY, periodNamePaint)
            // Разделитель
            val separatorX = periodWidth * (index + 1f) * transformations.scaleX + transformations.translationX
            drawLine(separatorX, 0f, separatorX, height.toFloat(), separatorsPaint)
        }
    }

    private fun Canvas.drawTasks() {
        val minTextLeft = taskTextHorizontalMargin
        uiTasks.forEach { uiTask ->
            if (uiTask.isRectOnScreen) {
                drawPath(uiTask.path, taskShapePaint)

                val taskRect = uiTask.rect
                val taskName = uiTask.task.name
                // Расположение названия
                val textStart = (taskRect.left + cutOutRadius + taskTextHorizontalMargin).coerceAtLeast(minTextLeft)
                val maxTextWidth = taskRect.right - taskTextHorizontalMargin - textStart
                if (maxTextWidth > 0) {
                    val textY = taskNamePaint.getTextBaselineByCenter(taskRect.centerY())
                    // Количество символов из названия, которые поместятся в фигуру
                    val charsCount = taskNamePaint.breakText(taskName, true, maxTextWidth, null)
                    drawText(taskName.substring(startIndex = 0, endIndex = charsCount), textStart, textY, taskNamePaint)
                }
            }
        }
    }

    private fun Paint.getTextBaselineByCenter(center: Float) = center - (descent() + ascent()) / 2

    // endregion

    // region Тачи
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return false

        // Пример - Запрещаем родителю перехватывать тачи в текущем взаимодействии, если движение пальца горизонтальное
//        if (abs(event.x - lastPoint.x) > abs(event.y - lastPoint.y)) {
//            parent?.requestDisallowInterceptTouchEvent(true)
//        }
        return if (event.pointerCount > 1) scaleGestureDetector.onTouchEvent(event) else processMove(event)
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
                if (width < contentWidth * transformations.scaleX) {
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
        // Rect с учетом всех преобразований
        val rect = RectF()

        // Path для фигуры таски
        val path = Path()

        // Path для вырезаемого круга
        val cutOutPath = Path()

        // Начальный Rect для текущих размеров View
        private val untransformedRect = RectF()

        // Если false, таск рисовать не нужно
        val isRectOnScreen: Boolean
            get() = rect.top < height && (rect.right > 0 || rect.left < width)

        fun updateInitialRect(index: Int) {
            fun getX(date: LocalDate): Float? {
                val periodIndex = periods.getValue(periodType).indexOf(periodType.getDateString(date))
                return if (periodIndex >= 0) {
                    periodWidth * (periodIndex + periodType.getPercentOfPeriod(date))
                } else {
                    null
                }
            }

            untransformedRect.set(
                getX(task.dateStart) ?: -taskCornerRadius,
                rowHeight * (index + 1f) + taskVerticalMargin,
                getX(task.dateEnd) ?: width + taskCornerRadius,
                rowHeight * (index + 2f) - taskVerticalMargin,
            )
            rect.set(untransformedRect)
        }

        fun transform(matrix: Matrix) {
            // Трансформируем untransformedRect и помещаем полученные значения в rect
            matrix.mapRect(rect, untransformedRect)
            updatePath()
        }

        private fun updatePath() {
            if (isRectOnScreen) {
                // Вырезаемый круг
                with(cutOutPath) {
                    reset()
                    addCircle(rect.left, rect.centerY(), cutOutRadius, Path.Direction.CW)
                }
                with(path) {
                    reset()
                    // Прямоугольник
                    addRoundRect(rect, taskCornerRadius, taskCornerRadius, Path.Direction.CW)
                    // Вырезаем из прямоугольника круг
                    op(cutOutPath, Path.Op.DIFFERENCE)
                }
            }
        }
    }

    private inner class Transformations {
        var translationX = 0f
            private set
        var scaleX = 1f
            private set

        // Матрица для преобразования фигур тасок
        private val matrix = Matrix()

        // На сколько максимально можно сдвинуть диаграмму
        private val minTranslation: Float
            get() = (width - contentWidth * transformations.scaleX).coerceAtMost(0f)

        // Относительный сдвиг на dx
        fun addTranslation(dx: Float) {
            translationX = (translationX + dx).coerceIn(minTranslation, 0f)
            updateTasks()
        }

        // Относительное увеличение на sx
        fun addScale(sx: Float) {
            scaleX = (scaleX * sx).coerceIn(1f, MAX_SCALE)
            recalculateTranslationX()
            updatePeriodTypeIfNeeded(scaleX)
            updateTasks()
        }

        fun onSaveInstanceState(state: SavedState) {
            state.translationX = translationX
            state.scaleX = scaleX
        }

        fun onRestoreInstanceState(state: SavedState) {
            translationX = state.translationX
            scaleX = state.scaleX
            recalculate()
        }

        // Пересчет необходимых значений и применение к таскам
        fun recalculate() {
            recalculateTranslationX()
            updatePeriodTypeIfNeeded(scaleX)
            updateTasks()
        }

        // Когда изменился scale или размер View надо пересчитать сдвиг
        private fun recalculateTranslationX() {
            translationX = translationX.coerceIn(minTranslation, 0f)
        }

        private fun updateTasks() {
            // Подготовка матрицы для трансформации фигур тасок
            with(matrix) {
                reset()
                // Порядок имеет значение
                postScale(scaleX, 1f)
                postTranslate(translationX, 0f)
            }
            uiTasks.forEach { it.transform(matrix) }
            invalidate()
        }

        // В зависимости от скейла PeriodType может измениться
        private fun updatePeriodTypeIfNeeded(scale: Float) {
            val periodTypes = PeriodType.values()
            // Шаг скейла, при котором меняется PeriodType
            val scaleStep = (MAX_SCALE - 1f) / periodTypes.size
            val periodTypeIndex = ((scale - 1f) / scaleStep).toInt().coerceAtMost(periodTypes.lastIndex)
            val periodType = periodTypes[periodTypeIndex]
            if (this@GantView.periodType != periodType) {
                this@GantView.periodType = periodType
                updateTasksRects()
            }
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector?): Boolean {
            return if (detector != null) {
                transformations.addScale(detector.scaleFactor)
                true
            } else {
                false
            }
        }
    }

    private class SavedState : BaseSavedState {
        var translationX: Float = 0f
        var scaleX: Float = 0f

        // Коснтруктор для сохранения стейта
        constructor(superState: Parcelable?) : super(superState)

        // Коснтруктор для восстановления стейта
        constructor(source: Parcel?) : super(source) {
            source?.apply {
                // Порядок имеет значение
                translationX = readFloat()
                scaleX = readFloat()
            }
        }

        override fun writeToParcel(out: Parcel?, flags: Int) {
            super.writeToParcel(out, flags)
            // Порядок имеет значение
            out?.writeFloat(translationX)
            out?.writeFloat(scaleX)
        }

        companion object {
            // Как у любого Parcelable
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
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