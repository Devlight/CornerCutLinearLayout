package io.devlight.xtreeivi.sample

import android.annotation.SuppressLint
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.view.animation.CycleInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import com.google.android.material.math.MathUtils
import io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
import io.devlight.xtreeivi.sample.extensions.*
import kotlinx.android.synthetic.main.activity_showcase.*
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private val handler = Handler()
    private lateinit var runnable: Runnable
    private val lineCountRunnable = object : Runnable {
        override fun run() {
            with(txt_showcase_custom_view_area_provider ?: return) {
                maxLines = maxLines.let {
                    if ((it + 1) <= 5) it + 1
                    else 1
                }
            }
            handler.postDelayed(this, 2000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_showcase)

        //region Custom Corner Cut
        val inset = resources.displayMetrics.density * 8
        val eyeRadius = resources.displayMetrics.density * 3
        val halfOpenMouthAngle = 35.0F
        val pacmanMouthPath = Path()
        ccll_showcase_custom_lt_rb.setCornerCutProvider { _, cutout, cutEdge, rectF ->
            when (cutEdge) {
                CornerCutLinearLayout.CornerCutFlag.START_TOP -> {
                    rectF.inset(inset, inset)
                    cutout.moveTo(rectF.left, rectF.top)
                    cutout.lineTo(rectF.right, rectF.top)
                    cutout.lineTo(rectF.left, rectF.bottom)
                    true
                }
                CornerCutLinearLayout.CornerCutFlag.END_BOTTOM -> {
                    rectF.inset(inset / 2.0F, inset / 2.0F)
                    rectF.set(
                        rectF.right - rectF.height(),
                        rectF.top,
                        rectF.right,
                        rectF.bottom,
                    )
                    val circleRadius = rectF.width() / 2.0F
                    cutout.addCircle(
                        rectF.centerX(),
                        rectF.centerY(),
                        circleRadius,
                        Path.Direction.CW
                    )
                    cutout.addCircle(
                        rectF.centerX(),
                        rectF.centerY() - circleRadius / 2.0F,
                        eyeRadius,
                        Path.Direction.CCW
                    )
                    pacmanMouthPath.rewind()
                    pacmanMouthPath.arcTo(
                        rectF.left,
                        rectF.top,
                        rectF.right,
                        rectF.bottom,
                        0.0F - halfOpenMouthAngle,
                        halfOpenMouthAngle * 2.0F,
                        true
                    )
                    pacmanMouthPath.lineTo(rectF.centerX(), rectF.centerY())
                    pacmanMouthPath.close()
                    cutout.op(pacmanMouthPath, Path.Op.DIFFERENCE)
                    true
                }
                else -> false
            }
        }

        val maxMouthOpenHalfAngle = 360.0F
        var currentRotationAngle = 0.0F
        var lastMillis = -1L
        var isClockwise = true
        val rotationDuration = 2000L
        ccll_showcase_custom_lt_exceed_bounds.setCornerCutProvider(
            { _: CornerCutLinearLayout, _: Path, _: Int, _: RectF ->
                val matrix = Matrix()
                val pb = ccll_showcase_custom_lt_exceed_bounds.paddedBounds
                matrix.postRotate(currentRotationAngle, pb.centerX(), pb.centerY())
                matrix
            },
            { _, cutout, cutEdge, rectF ->
                when (cutEdge) {
                    CornerCutLinearLayout.CornerCutFlag.START_TOP -> {
                        val pb = ccll_showcase_custom_lt_exceed_bounds.paddedBounds
                        cutout.addRect(
                            pb.centerX() - rectF.width() / 2.0F,
                            pb.centerY() - rectF.height() / 2.0F,
                            pb.centerX() + rectF.width() / 2.0F,
                            pb.centerY() + rectF.height() / 2.0F,
                            Path.Direction.CW
                        )
                        true
                    }

                    else -> false
                }
            }
        )

        runnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                if (lastMillis == -1L) lastMillis = now - 1L
                val dt = now - lastMillis
                val dAngle =
                    (maxMouthOpenHalfAngle * dt / rotationDuration).let { if (isClockwise) it else -it }
                lastMillis = now
                currentRotationAngle += dAngle
                if (currentRotationAngle <= 0.0F) {
                    currentRotationAngle = 0.0F
                    isClockwise = true
                } else if (currentRotationAngle >= maxMouthOpenHalfAngle) {
                    currentRotationAngle = maxMouthOpenHalfAngle
                    isClockwise = false
                }
                ccll_showcase_custom_lt_exceed_bounds?.invalidateCornerCutPath()
                handler.removeCallbacks(this)
                handler.postDelayed(this, 32L)
            }
        }

        ccll_showcase_custom_divider_provider.doOnNonNullSizeLayout {
            val pb = ccll_showcase_custom_divider_provider.paddedBounds
            ccll_showcase_custom_divider_provider.customDividerProviderPaint.shader =
                RadialGradient(
                    pb.centerX(),
                    pb.centerY(),
                    hypot(pb.width() / 2.0F, pb.height() / 2.0F) * 0.8F,
                    Color.BLACK,
                    Color.WHITE,
                    Shader.TileMode.CLAMP
                )
        }
        //endregion

        //region Custom Child Corner Cut
        ccll_showcase_custom_child_cut_provider.setChildCornerCutProvider { view, cutout, _, rectF, _, _ ->
            //rectF.inset(-40.0F, 0.0F)
            cutout.moveTo(rectF.centerX(), rectF.top)
            cutout.arcTo(
                rectF.centerX(),
                rectF.top - rectF.height() / 3.0F,
                rectF.centerX() + rectF.width(),
                rectF.top + rectF.height() / 3.0F,
                180.0F,
                -180.0F,
                false
            )
            cutout.lineTo(rectF.centerX() + rectF.width(), rectF.bottom)
            cutout.arcTo(
                rectF.centerX(),
                rectF.bottom - rectF.height() / 3.0F,
                rectF.centerX() + rectF.width(),
                rectF.bottom + rectF.height() / 3.0F,
                0.0F,
                -180.0F,
                false
            )
            cutout.lineTo(rectF.centerX(), rectF.top)
            val halfChordWidth = rectF.height() / 2.0F
            cutout.addCircle(
                rectF.centerX() + rectF.width(),
                rectF.centerY(),
                halfChordWidth,
                Path.Direction.CW
            )
            cutout.moveTo(rectF.centerX() + rectF.width(), rectF.top)
            cutout.lineTo(view.paddedBounds.right - rectF.width() / 2.0F, rectF.centerY())
            cutout.lineTo(rectF.centerX() + rectF.width(), rectF.bottom)
            cutout.lineTo(rectF.centerX() + rectF.width(), rectF.top)
            true
        }

        ccll_showcase_custom_child_cut_provider_mixed.setChildCornerCutProvider(
            { _: CornerCutLinearLayout, _: Path, cutSide: Int, rectF: RectF, _: View?, _: View? ->
                val matrix = Matrix()
                when (cutSide) {
                    CornerCutLinearLayout.ChildSideCutFlag.START -> {
                        matrix.postSkew(-0.25F, 0.0F, rectF.centerX(), rectF.centerY())

                    }

                    CornerCutLinearLayout.ChildSideCutFlag.END -> {
                        matrix.postRotate(-10.0F, rectF.centerX(), rectF.centerY())
                        matrix.postTranslate(-rectF.width() / 2.0F, 0.0F)
                    }
                }
                matrix
            },
            { view, cutout, cutSide, rectF, relativeCutTopChild, _ ->
                when (cutSide) {
                    CornerCutLinearLayout.ChildSideCutFlag.START -> {
                        if (view.indexOfChild(
                                relativeCutTopChild ?: return@setChildCornerCutProvider false
                            ) != 1
                        ) return@setChildCornerCutProvider false
                        val width = rectF.width()
                        val height = rectF.height()
                        rectF.offset(rectF.width() / 2.0F, 0.0F)
                        cutout.moveTo(rectF.centerX(), rectF.top)
                        cutout.lineTo(rectF.centerX() + 0.125F * width, rectF.top + 0.38F * height)
                        cutout.lineTo(rectF.centerX() + 0.522F * width, rectF.top + 0.38F * height)
                        cutout.lineTo(rectF.centerX() + 0.2F * width, rectF.top + 0.612F * height)
                        cutout.lineTo(rectF.centerX() + 0.325F * width, rectF.top + 1.0F * height)
                        cutout.lineTo(rectF.centerX(), rectF.top + 0.76F * height)
                        cutout.lineTo(rectF.centerX() - 0.325F * width, rectF.top + 1.0F * height)
                        cutout.lineTo(rectF.centerX() - 0.2F * width, rectF.top + 0.612F * height)
                        cutout.lineTo(rectF.centerX() - 0.522F * width, rectF.top + 0.38F * height)
                        cutout.lineTo(rectF.centerX() - 0.125F * width, rectF.top + 0.38F * height)
                        true
                    }

                    CornerCutLinearLayout.ChildSideCutFlag.END -> {
                        if (view.indexOfChild(
                                relativeCutTopChild ?: return@setChildCornerCutProvider false
                            ) != 0
                        ) return@setChildCornerCutProvider false
                        val width = rectF.width()
                        val height = rectF.height()
                        cutout.moveTo(rectF.centerX(), rectF.top)
                        cutout.lineTo(rectF.centerX() + 0.125F * width, rectF.top + 0.38F * height)
                        cutout.lineTo(rectF.centerX() + 0.522F * width, rectF.top + 0.38F * height)
                        cutout.lineTo(rectF.centerX() + 0.2F * width, rectF.top + 0.612F * height)
                        cutout.lineTo(rectF.centerX() + 0.325F * width, rectF.top + 1.0F * height)
                        cutout.lineTo(rectF.centerX(), rectF.top + 0.76F * height)
                        cutout.lineTo(rectF.centerX() - 0.325F * width, rectF.top + 1.0F * height)
                        cutout.lineTo(rectF.centerX() - 0.2F * width, rectF.top + 0.612F * height)
                        cutout.lineTo(rectF.centerX() - 0.522F * width, rectF.top + 0.38F * height)
                        cutout.lineTo(rectF.centerX() - 0.125F * width, rectF.top + 0.38F * height)
                        true
                    }

                    else -> false
                }
            }
        )

        ccll_showcase_custom_view_area_provider.setCustomViewAreaProvider { view, path, rectF ->
            val offset = view[0].marginEnd
            val cornerRadius = rectF.height() / 4.0F
            val tailCircleRadius = cornerRadius / 2.0F
            val innerTailCircleRadius = tailCircleRadius / 2.0F
            val smallCornerRadius = cornerRadius / 4.0F
            path.addRoundRect(
                rectF.left,
                rectF.top,
                rectF.right - offset,
                rectF.bottom,
                cornerRadius,
                cornerRadius,
                Path.Direction.CW
            )

            path.moveTo(rectF.right - offset, rectF.top + cornerRadius)
            path.arcTo(
                rectF.right - offset,
                rectF.top + cornerRadius - smallCornerRadius,
                rectF.right - offset + smallCornerRadius * 2,
                rectF.top + cornerRadius + smallCornerRadius,
                180.0F,
                -50.0F,
                false
            )
            path.lineTo(rectF.right - tailCircleRadius, rectF.centerY() - innerTailCircleRadius)
            path.lineTo(
                rectF.right - offset + innerTailCircleRadius,
                rectF.centerY() - innerTailCircleRadius
            )
            path.arcTo(
                rectF.right - offset,
                rectF.centerY() - innerTailCircleRadius,
                rectF.right - offset + innerTailCircleRadius * 2,
                rectF.centerY() + innerTailCircleRadius,
                270.0F,
                -180.0F,
                false
            )
            path.lineTo(rectF.right - tailCircleRadius, rectF.centerY() + innerTailCircleRadius)
            path.arcTo(
                rectF.right - offset,
                rectF.bottom - cornerRadius - smallCornerRadius,
                rectF.right - offset + smallCornerRadius * 2,
                rectF.bottom - cornerRadius + smallCornerRadius,
                230.0F,
                -50.0F,
                false
            )
            path.lineTo(rectF.right - offset, rectF.top + cornerRadius)

            // circle
            path.addCircle(
                rectF.right - tailCircleRadius,
                rectF.centerY(),
                tailCircleRadius,
                Path.Direction.CW
            )
            path.addCircle(
                rectF.right - tailCircleRadius,
                rectF.centerY(),
                innerTailCircleRadius,
                Path.Direction.CCW
            )
        }

        val tempPath = Path()
        val tempRectF = RectF()

        ccll_showcase_custom_view_area_provider_2.setCustomViewAreaProvider { view, path, _ ->
            view.forEach {
                tempPath.rewind()
                if (it is CornerCutLinearLayout) {
                    tempPath.offset(-it.left.toFloat(), -it.top.toFloat())
                    tempPath.addPath(it.viewAreaPath)
                    tempPath.transform(it.matrix)
                    tempPath.offset(it.left.toFloat(), it.top.toFloat())
                } else {
                    tempRectF.set(
                        it.left.toFloat(),
                        it.top.toFloat(),
                        it.right.toFloat(),
                        it.bottom.toFloat()
                    )
                    val childCornerRadius = min(tempRectF.width(), tempRectF.height()) / 6.0F

                    tempPath.addRoundRect(
                        tempRectF,
                        childCornerRadius,
                        childCornerRadius,
                        Path.Direction.CW
                    )
                    tempPath.offset(-it.left.toFloat(), -it.top.toFloat())
                    tempPath.transform(it.matrix)
                    tempPath.offset(it.left.toFloat(), it.top.toFloat())
                }
                path.op(tempPath, Path.Op.UNION)
            }
        }

        ccll_showcase_custom_view_area_provider_2_child_1.addCustomCutoutProvider { _, cutout, rectF ->
            val width = rectF.width()
            val height = rectF.height()
            cutout.moveTo(rectF.centerX(), rectF.top)
            cutout.lineTo(rectF.centerX() + 0.125F * width, rectF.top + 0.38F * height)
            cutout.lineTo(rectF.centerX() + 0.522F * width, rectF.top + 0.38F * height)
            cutout.lineTo(rectF.centerX() + 0.2F * width, rectF.top + 0.612F * height)
            cutout.lineTo(rectF.centerX() + 0.325F * width, rectF.top + 1.0F * height)
            cutout.lineTo(rectF.centerX(), rectF.top + 0.76F * height)
            cutout.lineTo(rectF.centerX() - 0.325F * width, rectF.top + 1.0F * height)
            cutout.lineTo(rectF.centerX() - 0.2F * width, rectF.top + 0.612F * height)
            cutout.lineTo(rectF.centerX() - 0.522F * width, rectF.top + 0.38F * height)
            cutout.lineTo(rectF.centerX() - 0.125F * width, rectF.top + 0.38F * height)
        }

        val waveLineCutWidth = resources.getDimension(R.dimen.offset_12)
        val waveLineHeight = resources.getDimension(R.dimen.offset_48)
        val halfWaveLineHeight = waveLineHeight/2.0F
        val halfWaveLineCutWidth = waveLineCutWidth/2.0F
        ccll_showcase_custom_view_area_provider_2.addCustomCutoutProvider { _, cutout, rectF ->
            cutout.moveTo(rectF.left, rectF.centerY() - halfWaveLineCutWidth)
            cutout.lineTo(
                rectF.left + rectF.width()/4.0F,
                rectF.centerY() - halfWaveLineCutWidth - halfWaveLineHeight
            )
            cutout.lineTo(
                rectF.right - rectF.width()/4.0F,
                rectF.centerY() - halfWaveLineCutWidth + halfWaveLineHeight
            )
            cutout.lineTo(rectF.right, rectF.centerY() - halfWaveLineCutWidth)
            cutout.lineTo(rectF.right, rectF.centerY() + halfWaveLineCutWidth)
            cutout.lineTo(
                rectF.right - rectF.width()/4.0F,
                rectF.centerY() + halfWaveLineCutWidth + halfWaveLineHeight
            )
            cutout.lineTo(
                rectF.left + rectF.width()/4.0F,
                rectF.centerY() + halfWaveLineCutWidth - halfWaveLineHeight
            )
            cutout.lineTo(rectF.left, rectF.centerY() + halfWaveLineCutWidth)
            cutout.lineTo(rectF.left, rectF.centerY() - halfWaveLineCutWidth)
        }


        ccll_showcase_custom_view_area_provider_2.doOnNonNullSizeLayout {
            val firstChild = it[0]
            val lastChild = it[2]
            val middleChild = it[1]
            val lastChildTravelX = it.width.toFloat() - lastChild.width.toFloat()
            val middleChildTravelY = it.height.toFloat()
            middleChild.translationY = -(middleChild.top.toFloat() + middleChild.height/2.0F)
            lastChild.translationX = -lastChild.left.toFloat()
            fun animateFirstChild() {
                firstChild
                    .animate()
                    .rotationBy(360.0F)
                    .setDuration(4000L)
                    .withEndAction {
                        firstChild
                            .animate()
                            .rotationXBy(360.0F)
                            .setDuration(2000L)
                            .withEndAction {
                                firstChild
                                    .animate()
                                    .rotationYBy(360.0F)
                                    .setDuration(2000L)
                                    .withEndAction {
                                        animateFirstChild()
                                    }
                            }
                    }
            }
            fun animateMiddleChild() {
                middleChild
                    .animate()
                    .translationYBy(middleChildTravelY)
                    .setDuration((2500L..5000L).random())
                    .setInterpolator(CycleInterpolator(0.5F))
                    .withEndAction { animateMiddleChild() }
            }
            fun animateLastChild() {
                lastChild
                    .animate()
                    .translationXBy(lastChildTravelX)
                    .setInterpolator(CycleInterpolator(0.5F))
                    .setDuration(4000L)
                    .setUpdateListener {
                        ccll_showcase_custom_view_area_provider_2.invalidateCornerCutPath()
                    }
                    .withEndAction {
                        animateLastChild()
                    }
            }
            animateFirstChild()
            animateMiddleChild()
            animateLastChild()
        }

        //endregion

        //region Cut Properties
        ccll_showcase_depth_and_length.doOnNonNullSizeLayout {
            val depth =
                (ccll_showcase_depth_and_length.width - ccll_showcase_depth_and_length.paddingEnd - ccll_showcase_depth_and_length.childEndSideCornerCutDepth / 2.0F).roundToInt()
            v_showcase_bounds_depth_offset?.updateLayoutParams<FrameLayout.LayoutParams> {
                marginStart = depth
            }
        }

        ccll_showcase_depth_and_length_offset.doOnNonNullSizeLayout {
            ccll_showcase_depth_and_length_offset.childEndSideCornerCutDepthOffset =
                it.paddedBounds.width() - it.childStartSideCornerCutDepth
        }


        v_showcase_max_cut.duplicateViewSizeContinuously(
            ccll_showcase_max_cut,
            transformWidth = { (ccll_showcase_max_cut.paddedBounds.width() / 2).roundToInt() },
            transformHeight = { (ccll_showcase_max_cut.paddedBounds.height() / 2).roundToInt() },
        )

        v_showcase_max_cut_equal.duplicateViewSizeContinuously(
            ccll_showcase_max_cut_equal,
            transformWidth = {
                ccll_showcase_max_cut_equal.paddedBounds.let { min(it.width(), it.height()) / 2 }
                    .roundToInt()
            },
            transformHeight = {
                ccll_showcase_max_cut_equal.paddedBounds.let { min(it.width(), it.height()) / 2 }
                    .roundToInt()
            },
        )

        val pureRawSize = resources.getDimension(R.dimen.offset_32)
        val action: (view: CornerCutLinearLayout) -> Unit = { ccll ->
            var isAnimationRunning = false
            val maxWidth = ccll.width.toFloat()
            val maxHeight = ccll.height.toFloat()
            val minWidth = pureRawSize + ccll.horizontalPadding
            val minHeight = pureRawSize + ccll.verticalPadding

            @SuppressLint("SetTextI18n")
            fun playAnimation() {
                if (isAnimationRunning) return
                isAnimationRunning = true
                val textView = (ccll as? ViewGroup)?.getOrNull<TextView>(1)
                textView?.text = ""
                cycleViewWidth(ccll, minWidth, maxWidth) {
                    cycleViewHeight(ccll, minHeight, maxHeight) {
                        cycleViewWidthAndHeight(ccll, minWidth, maxWidth, minHeight, maxHeight) {
                            textView?.text = "CLICK"
                            isAnimationRunning = false
                        }
                    }
                }
            }
            ccll.setOnClickListener {
                playAnimation()
            }
        }
        ccll_showcase_max_cut.doOnNonNullSizeLayout(action)
        ccll_showcase_max_cut_equal.doOnNonNullSizeLayout(action)
        //endregion

        //region Custom Divider
        val topDividerTrianglePath = Path()
        val bottomDividerTrianglePath = Path()
        val circleDotDividerPath = Path()
        val diamondDotDividerPath = Path()
        val triangleBaseWidth = resources.getDimension(R.dimen.offset_16)
        val triangleHeight =
            triangleBaseWidth * sqrt(3.0F) / 2.0F  /*extra height multiplier*/ * 2.0F
        val circleRadius = resources.getDimension(R.dimen.offset_6)

        topDividerTrianglePath.moveTo(0.0F, 0.0F)
        topDividerTrianglePath.lineTo(triangleBaseWidth, 0.0F)
        topDividerTrianglePath.lineTo(triangleBaseWidth / 2.0F, triangleHeight)
        topDividerTrianglePath.close()

        bottomDividerTrianglePath.moveTo(0.0F, 0.0F)
        bottomDividerTrianglePath.lineTo(triangleBaseWidth / 2.0F, -triangleHeight)
        bottomDividerTrianglePath.lineTo(triangleBaseWidth, 0.0F)
        bottomDividerTrianglePath.close()

        circleDotDividerPath.addCircle(0.0F, 0.0F, circleRadius, Path.Direction.CW)
        circleDotDividerPath.close()

        diamondDotDividerPath.moveTo(0.0F, 0.0F)
        diamondDotDividerPath.lineTo(triangleBaseWidth / 2.0F, -triangleBaseWidth / 2.0F)
        diamondDotDividerPath.lineTo(triangleBaseWidth, 0.0F)
        diamondDotDividerPath.lineTo(triangleBaseWidth / 2.0F, triangleBaseWidth / 2.0F)
        diamondDotDividerPath.close()
        diamondDotDividerPath.offset(-triangleBaseWidth / 2.0F, 0.0F)

        ccll_showcase_custom_divider_provider.setCustomDividerProvider { _, dividerPath, dividerPaint, showDividerFlag, dividerTypeIndex, rectF ->
            when (showDividerFlag) {
                CornerCutLinearLayout.CustomDividerShowFlag.CONTAINER_BEGINNING -> {
                    dividerPaint.style = Paint.Style.STROKE
                    dividerPaint.strokeWidth = triangleHeight
                    dividerPaint.pathEffect = PathDashPathEffect(
                        topDividerTrianglePath,
                        triangleBaseWidth,
                        0.0F,
                        PathDashPathEffect.Style.TRANSLATE
                    )
                    dividerPath.moveTo(rectF.left, rectF.top)
                    dividerPath.lineTo(rectF.right, rectF.top)
                }

                CornerCutLinearLayout.CustomDividerShowFlag.MIDDLE -> {
                    dividerPaint.style = Paint.Style.STROKE
                    if (dividerTypeIndex == 0) {
                        dividerPaint.strokeWidth = circleRadius
                        dividerPaint.pathEffect = PathDashPathEffect(
                            circleDotDividerPath,
                            triangleBaseWidth,
                            0.0F,
                            PathDashPathEffect.Style.TRANSLATE
                        )
                        dividerPath.moveTo(rectF.left, rectF.centerY())
                        dividerPath.lineTo(rectF.right + triangleBaseWidth, rectF.centerY())
                    } else {
                        dividerPaint.strokeWidth = circleRadius
                        dividerPaint.pathEffect = PathDashPathEffect(
                            diamondDotDividerPath,
                            triangleBaseWidth,
                            0.0F,
                            PathDashPathEffect.Style.TRANSLATE
                        )
                        dividerPath.moveTo(rectF.left, rectF.centerY())
                        dividerPath.lineTo(rectF.right + triangleBaseWidth, rectF.centerY())
                    }
                }

                CornerCutLinearLayout.CustomDividerShowFlag.CONTAINER_END -> {
                    dividerPaint.style = Paint.Style.STROKE
                    dividerPaint.strokeWidth = triangleHeight
                    dividerPaint.pathEffect = PathDashPathEffect(
                        bottomDividerTrianglePath,
                        triangleBaseWidth,
                        0.0F,
                        PathDashPathEffect.Style.TRANSLATE
                    )
                    dividerPath.moveTo(rectF.left, rectF.top)
                    dividerPath.lineTo(rectF.right, rectF.top)
                }
            }
            true
        }

        val wavePath = Path()
        val halfWaveWidth = resources.getDimension(R.dimen.offset_16)
        val halfWaveHeight = resources.getDimension(R.dimen.offset_8)
        wavePath.moveTo(0.0F, 0.0F)
        wavePath.arcTo(0.0F, -halfWaveHeight, halfWaveWidth, halfWaveHeight, 180.0F, 180.0F, true)
        wavePath.arcTo(
            halfWaveWidth,
            -halfWaveHeight,
            halfWaveWidth * 2.0F,
            halfWaveHeight,
            180.0F,
            -180.0F,
            true
        )

        ccll_showcase_custom_divider_provider_mixed.setCustomDividerProvider { _, dividerPath, dividerPaint, showDividerFlag, dividerTypeIndex, rectF ->
            when (showDividerFlag) {
                CornerCutLinearLayout.CustomDividerShowFlag.MIDDLE -> {
                    dividerPaint.style = Paint.Style.STROKE
                    when (dividerTypeIndex) {
                        0 -> {
                            dividerPaint.shader =
                                RadialGradient(
                                    rectF.centerX(),
                                    rectF.centerY(),
                                    rectF.width() / 2.0F,
                                    Color.GREEN,
                                    Color.RED,
                                    Shader.TileMode.MIRROR
                                )
                            dividerPaint.strokeWidth = circleRadius
                            dividerPaint.pathEffect = PathDashPathEffect(
                                diamondDotDividerPath,
                                triangleBaseWidth,
                                0.0F,
                                PathDashPathEffect.Style.TRANSLATE
                            )
                            dividerPath.moveTo(rectF.left, rectF.centerY())
                            dividerPath.lineTo(rectF.right + triangleBaseWidth, rectF.centerY())
                            return@setCustomDividerProvider true
                        }
                        2 -> {
                            dividerPaint.shader =
                                LinearGradient(
                                    rectF.centerX(),
                                    rectF.centerY() - halfWaveHeight,
                                    rectF.centerX(),
                                    rectF.centerY() + halfWaveHeight,
                                    Color.BLUE,
                                    Color.YELLOW,
                                    Shader.TileMode.CLAMP
                                )
                            dividerPaint.strokeWidth = halfWaveHeight * 2.0F
                            dividerPaint.pathEffect = PathDashPathEffect(
                                wavePath,
                                halfWaveWidth * 2.0F,
                                0.0F,
                                PathDashPathEffect.Style.TRANSLATE
                            )
                            dividerPath.moveTo(rectF.left, rectF.centerY())
                            dividerPath.lineTo(rectF.right, rectF.centerY())
                            return@setCustomDividerProvider true
                        }
                        else -> return@setCustomDividerProvider false
                    }
                }
            }
            false
        }
        //endregion
    }

    private fun cycleViewWidth(view: View, minWidth: Float, maxWidth: Float, onEnd: () -> Unit) {
        view
            .animate()
            .translationX(0.5F)
            .setDuration(2000L)
            .setInterpolator(CycleInterpolator(0.5F))
            .setUpdateListener {
                val fraction = it.animatedFraction
                view.updateLayoutParams {
                    width = MathUtils.lerp(maxWidth, minWidth, fraction).roundToInt()
                }
            }
            .withEndAction { onEnd() }
    }

    private fun cycleViewHeight(view: View, minHeight: Float, maxHeight: Float, onEnd: () -> Unit) {
        view
            .animate()
            .translationX(0.5F)
            .setDuration(2000L)
            .setInterpolator(CycleInterpolator(0.5F))
            .setUpdateListener {
                val fraction = it.animatedFraction
                view.updateLayoutParams {
                    height = MathUtils.lerp(maxHeight, minHeight, fraction).roundToInt()
                }
            }
            .withEndAction { onEnd() }
    }

    private fun cycleViewWidthAndHeight(
        view: View,
        minWidth: Float,
        maxWidth: Float,
        minHeight: Float,
        maxHeight: Float,
        onEnd: () -> Unit
    ) {
        view
            .animate()
            .translationX(0.5F)
            .setDuration(2000L)
            .setInterpolator(CycleInterpolator(0.5F))
            .setUpdateListener {
                val fraction = it.animatedFraction
                view.updateLayoutParams {
                    width = MathUtils.lerp(maxWidth, minWidth, fraction).roundToInt()
                    height = MathUtils.lerp(maxHeight, minHeight, fraction).roundToInt()
                }
            }
            .withEndAction { onEnd() }
    }

    override fun onResume() {
        super.onResume()
        if (::runnable.isInitialized) {
            handler.removeCallbacks(runnable)
            handler.post(runnable)
        }
        handler.post(lineCountRunnable)
    }

    override fun onPause() {
        super.onPause()
        if (::runnable.isInitialized) {
            handler.removeCallbacks(runnable)
        }
        handler.removeCallbacks(lineCountRunnable)
    }
}