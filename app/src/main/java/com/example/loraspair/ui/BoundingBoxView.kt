package com.example.loraspair.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toPoint

// Интерфейс выбора зоны карты для сохранения
class BoundingBoxView : View {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attributeSet: AttributeSet?) : super(context, attributeSet, 0)
    constructor(context: Context?, attributeSet: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attributeSet,
        defStyleAttr
    )

    object Constants { // Статические поля в пространстве имён Constants
        const val CAPTURE_ZONE_RADIUS = 48.0f
    }

    var listener: BoundingBoxListener? = null // Инициализация слушателя изменения зоны для сохранения карты

    private var topLeftPoint: PointF // Левая верхняя точка зоны
    private var bottomRightPoint: PointF // Правая нижняя точка зоны
    private var topLeftPointIsCaptured: Boolean = false // Захвачена ли в текущий момент времени левая верхняя точка зоны
    private var bottomRightPointIsCaptured: Boolean = false // Захвачена ли в текущий момент времени правая нижняя точка зоны
    private lateinit var viewTopLeftPoint: PointF // Левая верхняя точка интерфейса выбора зоны карты для сохранения
    private lateinit var viewBottomRightPoint: PointF // Правая нижняя точка интерфейса выбора зоны карты для сохранения

    private var rectangle = RectF() // Прямоугольник зоны
    private val paint = Paint() // Инструмент рисования по интерфейсу

    init { // Конструктор класса
        val displayMetrics = Resources.getSystem().displayMetrics
        val width = displayMetrics.widthPixels.toFloat() // Ширина экрана
        val height = displayMetrics.heightPixels.toFloat() // Высота экрана

        topLeftPoint = PointF(width / 4.0f, height / 4.0f) // Установка левой верхней точки зоны
        bottomRightPoint = PointF(3.0f * width / 4.0f, height / 2.0f) // Установка правой нижней точки зоны

        rectangle = RectF(topLeftPoint.x, topLeftPoint.y, bottomRightPoint.x, bottomRightPoint.y) // Установка прямоугольника зоны

        paint.color = Color.BLACK // Установка черно цвета инструмента рисования
        paint.strokeWidth = 16.0f // Установка ширины линии инструмента рисования
    }

    override fun onDraw(canvas: Canvas) { // Вызывается при отрисовке интерфейса
        paint.style = Paint.Style.STROKE // Установка стиля инструмента рисования на отрисовку граней примитивов
        canvas.drawRect(rectangle, paint) // Отрисовка прямоугольника зоны
        paint.style = Paint.Style.FILL // Установка стиля инструмента рисования на заполнение площади примитивов
        canvas.drawCircle(
            topLeftPoint.x,
            topLeftPoint.y,
            24.0f,
            paint
        ) // Отрисовка левой верхней точки зоны
        canvas.drawCircle(
            bottomRightPoint.x,
            bottomRightPoint.y,
            24.0f,
            paint
        ) // Отрисовка правой нижней точки зоны
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) { // Вызывается при расположении интерфейса
        super.onLayout(changed, left, top, right, bottom)
        viewTopLeftPoint = PointF(left.toFloat(), top.toFloat()) // Установка левой верхней точки интерфейса выбора зоны карты для сохранения
        viewBottomRightPoint = PointF(right.toFloat(), bottom.toFloat()) // Установка правой нижней точки интерфейса выбора зоны карты для сохранения
    }

    override fun onTouchEvent(event: MotionEvent): Boolean { // Вызывается при прикосновении к интерфейсу выбора зоны карты для сохранения
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { // Если зафиксировано нажатие
                performClick()
                if (pointIsInTopLeft(event.x, event.y)) { // Если произошло нажатие на левую верхнюю точку зоны
                    topLeftPointIsCaptured = true // Левая верхняя точка зоны захвачена
                    return true // Возврат статуса того, что касание в полной мере было обработано в этой функции
                } else if (pointIsInBottomRight(event.x, event.y)) { // Если произошло нажатие на правую нижнюю точку зоны
                    bottomRightPointIsCaptured = true // Правая нижняя точка зоны захвачена
                    return true // Возврат статуса того, что касание в полной мере было обработано в этой функции
                }
            }

            MotionEvent.ACTION_MOVE -> { // Если зафиксировано передвижение
                if ((event.x in (viewTopLeftPoint.x + Constants.CAPTURE_ZONE_RADIUS)..(viewBottomRightPoint.x - Constants.CAPTURE_ZONE_RADIUS))
                    &&
                    (event.y in (viewTopLeftPoint.y + Constants.CAPTURE_ZONE_RADIUS)..(viewBottomRightPoint.y - Constants.CAPTURE_ZONE_RADIUS))
                ) { // Если точка ввода не выходит за пределы интерфейса выбора зоны карты для сохранения
                    if (topLeftPointIsCaptured) { // Если левая верхняя точка зоны захвачена
                        if (event.x < bottomRightPoint.x && event.y < bottomRightPoint.y) { // Если точка ввода левее и выше правой нижней точки зоны
                            topLeftPoint = PointF(event.x, event.y) // Установка левой верхней точки зоны на точку ввода
                            updateBoundingBox() // Обновление прямоугольника зоны
                        }
                        return true // Возврат статуса того, что касание в полной мере было обработано в этой функции
                    } else if (bottomRightPointIsCaptured) {
                        if (event.x > topLeftPoint.x && event.y > topLeftPoint.y) {
                            bottomRightPoint = PointF(event.x, event.y)
                            updateBoundingBox() // Обновление прямоугольника зоны
                        }
                        return true // Возврат статуса того, что касание в полной мере было обработано в этой функции
                    }
                }
            }

            MotionEvent.ACTION_UP -> { // Если зафиксировано отпускание
                topLeftPointIsCaptured = false // Левая верхняя точка зоны не захвачена
                bottomRightPointIsCaptured = false // Правая нижняя точка зоны не захвачена
                listener?.onBoundingBoxResized() // Изменение размеров зоны карты для сохранения завершено
            }

            MotionEvent.ACTION_CANCEL -> { // Если зафиксирована ошибка
                topLeftPointIsCaptured = false // Левая верхняя точка зоны не захвачена
                bottomRightPointIsCaptured = false // Правая нижняя точка зоны не захвачена
                listener?.onBoundingBoxResized() // Изменение размеров зоны карты для сохранения завершено
            }
        }

        return false // Возврат статуса того, что касание не было обработано в этой функции в полной мере
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun updateBoundingBox() { // Обновление прямоугольника зоны
        rectangle = RectF(topLeftPoint.x, topLeftPoint.y, bottomRightPoint.x, bottomRightPoint.y) // Установка прямоугольнка зоны
        invalidate() // Переотрисовка интерфейса выбора зоны карты для сохранения
    }

    private fun pointIsInTopLeft(x: Float, y: Float): Boolean { // Проверка того, находится ли принятая точка в зоне захвата левой верхней точки зоны
        return ((x in (topLeftPoint.x - Constants.CAPTURE_ZONE_RADIUS)..(topLeftPoint.x + Constants.CAPTURE_ZONE_RADIUS))
                &&
                (y in (topLeftPoint.y - Constants.CAPTURE_ZONE_RADIUS)..(topLeftPoint.y + Constants.CAPTURE_ZONE_RADIUS)))
    }

    private fun pointIsInBottomRight(x: Float, y: Float): Boolean { // Проверка того, находится ли принятая точка в зоне захвата правой нижней точки зоны
        return ((x in (bottomRightPoint.x - Constants.CAPTURE_ZONE_RADIUS)..(bottomRightPoint.x + Constants.CAPTURE_ZONE_RADIUS))
                &&
                (y in (bottomRightPoint.y - Constants.CAPTURE_ZONE_RADIUS)..(bottomRightPoint.y + Constants.CAPTURE_ZONE_RADIUS)))
    }

    val boundingBoxPoints: Pair<Point, Point>
        get() = Pair(topLeftPoint.toPoint(), bottomRightPoint.toPoint())

    interface BoundingBoxListener { // Интерфейс слушателя изменения размеров зоны карты для сохранения

        fun onBoundingBoxResized() // Вызывается для оповещении об завершении изменения размеров зоны карты для сохранения
    }
}