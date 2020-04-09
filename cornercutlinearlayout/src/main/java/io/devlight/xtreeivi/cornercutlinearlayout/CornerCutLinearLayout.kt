package io.devlight.xtreeivi.cornercutlinearlayout

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.Size
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.alpha
import androidx.core.view.*
import io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout.CornerCutFlag.END_BOTTOM
import io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout.CornerCutFlag.END_TOP
import io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout.CornerCutFlag.START_BOTTOM
import io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout.CornerCutFlag.START_TOP
import io.devlight.xtreeivi.cornercutlinearlayout.util.delegate.NonNullDelegate
import io.devlight.xtreeivi.cornercutlinearlayout.util.delegate.SimpleNonNullDelegate
import io.devlight.xtreeivi.cornercutlinearlayout.util.extension.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Suppress("unused", "MemberVisibilityCanBePrivate")
class CornerCutLinearLayout : LinearLayout {

    //region Temp & Internal Properties
    private val defaultChildCornerCutSize by lazy { context.resources.displayMetrics.density * 16.0F }

    private val paddedBoundsF = RectF()
    val paddedBounds get() = RectF(paddedBoundsF)

    /**
     * Resulting path with cutouts (if any).
     * [viewCornerCutPath] & [childCornerCutPath] & [customCutoutsPath] are merged into this path,
     * before this [CornerCutLinearLayout] being "masked" (clipped) by the [childCornerCutPath].
     */
    private val composedCornerCutPath = Path()

    /**
     * Copy of current visible area path.
     * Custom shadow are build upon this original path.
     * This path modification does not change neither clip area nor shadow.
     */
    val composedCutoutPath get() = Path(composedCornerCutPath)

    /**
     * Contains this [LinearLayout] corner cutouts.
     * Unlike [childCornerCutPath], corners in this path are strictly positioned to this
     * view padded (including shadow reserved padding) bounds.
     */
    private val viewCornerCutPath = Path()

    /**
     * Temporary path that contains user defined data.
     * Used with [cornerCutProvider]
     * @see [CornerCutProvider]
     */
    private val tempCustomCutPath = Path()

    /**
     * Temporary [RectF] that contains respective corner cut bounds.
     * Used with [cornerCutProvider]
     * @see [CornerCutProvider]
     */
    private val tempRectF = RectF()

    /**
     * Path that contains cutouts between children.
     * Note that these cutouts could of different shape, size, rotation and position.
     * Only by default they look like corner cuts between child (bevel, rounded corners, etc.)
     */
    private val childCornerCutPath = Path()

    /**
     * Path that contains all custom cutouts between children.
     */
    private val customCutoutsPath = Path()

    /**
     * Contains child contact y from top to bottom ([LinearLayout.VERTICAL] orientation) or
     * child contact x from start to end ([LinearLayout.HORIZONTAL] orientation)
     * Note that each center takes into consideration contact view's margins and
     * custom divider width or height (respectively for orientation)
     *
     * View 1
     * |       |                        |
     * ⎣_______⎦ - View 1 bottom        |
     *                                  |
     * --------- - View 1 margin bottom |
     * ///////// ⎤                      |
     * ///////// | - Divider height     | - contact center x
     * ///////// ⎦                      |
     * --------- - View 2 margin top    |
     *                                  |
     * ⎡-------⎤ - View 2 top           |
     * |       |                        |
     */
    private val childContactCutCenters = mutableSetOf<Float>()

    /**
     * Contains each child overridden corner cut info (in any) for start side
     * [Pair.first] [CornerCutType] stands for top/start part cutout
     * [Pair.second] [CornerCutType] stands for bottom/end part cutout
     * @see [CornerCutType]
     * @see [ChildSideCutFlag]
     */
    private val childStartSideCutTypes = mutableListOf<Pair<CornerCutType?, CornerCutType?>>()

    /**
     * Contains each child overridden corner cut info (in any) for end side
     * [Pair.first] [CornerCutType] stands for top/start part cutout
     * [Pair.second] [CornerCutType] stands for bottom/end part cutout
     * @see [CornerCutType]
     * @see [ChildSideCutFlag]
     */
    private val childEndSideCutTypes = mutableListOf<Pair<CornerCutType?, CornerCutType?>>()

    /**
     * Contains:
     * [LinearLayout.VERTICAL]: y position for each custom divider (from top to bottom)
     * [LinearLayout.HORIZONTAL]: x position for each custom divider (from start to end)
     *
     * Size always the same between [customDividerPoints] & [customDividerTypes] & [customDividerTypedIndexes]
     *
     * @see childContactCutCenters
     * @see [CustomDividerShowFlag]
     * @see [CustomDividerProvider]
     */
    private val customDividerPoints = mutableListOf<Float>()

    /**
     * Contains types of each divider from first to last shown divider
     * Size always the same between [customDividerPoints] & [customDividerTypes] & [customDividerTypedIndexes]
     *
     * @see [CustomDividerShowFlag]
     * @see [CustomDividerProvider]
     */
    private val customDividerTypes = mutableListOf<Int>()

    /**
     * Contains typed index of each divider type from first to last shown divider
     * Size always the same between [customDividerPoints] & [customDividerTypes] & [customDividerTypedIndexes]
     *
     * @see [CustomDividerShowFlag]
     * @see [CustomDividerProvider]
     */
    private val customDividerTypedIndexes = mutableListOf<Int>()

    /**
     * User defined padding. We need this array to preserve user defined padding because
     * custom shadow can add padding itself, which otherwise would conflict with user padding logic
     * @see [isCustomShadowAutoPaddingEnabled]
     * @see [couldDrawCustomShadowOverUserDefinedPadding]
     */
    private val userDefinedPadding = IntArray(4) { UNDEFINED }

    /**
     * Real padding set to this [CornerCutLinearLayout] that are composed of real
     * user defined padding and padding reserved for custom shadow
     * @see [isCustomShadowAutoPaddingEnabled]
     * @see [couldDrawCustomShadowOverUserDefinedPadding]
     */
    private val composedPadding = IntArray(4) { 0 }

    /**
     * Start from left top clockwise: 0 - depth, 1 - length...
     * Note that each corner of padded view bounds could have its own dimensions
     * [LinearLayout.VERTICAL]: depth - width (x) of cutout, length (y) - height of cutout
     * [LinearLayout.HORIZONTAL]: depth - width (y) of cutout, length (x) - height of cutout
     *
     * Default cutout positioning ([LinearLayout.VERTICAL] orientation, LTR end side (right side)):
     *
     * ---------
     *          |                          depth (width in this orientation)
     *          |                            |
     *          |                       ⌜⎺⎺⎺⎺⎺⎺⎺⎺⎺⌝
     *     -----------                  ----------- ⎤
     * ----|         |      where:      |         | | - length (height in this orientation)
     *     -----------                  ----------- ⎦
     *          |
     *          |
     *----------
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_top_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_top_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_top_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_top_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_bottom_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_bottom_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_bottom_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_bottom_length]
     *
     * Convenience attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_size]
     *
     * Relative attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_top_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_top_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_top_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_top_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_bottom_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_bottom_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_bottom_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_bottom_length]
     */
    private val cornerCutDimensions = FloatArray(8) { 0.0F }
    //endregion

    //region Corner Cut
    /**
     * [CornerCutType] for view start top corner cutout.
     * Depends on this view layout direction.
     * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
     * @see [CornerCutType]
     * @see [leftTopCornerCutType]
     * @see [rightTopCornerCutType]
     * Also see convenience functions.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_start_top_corner_cut_type]
     *
     * Note that (in LTR, for example) [R.styleable.CornerCutLinearLayout_ccll_left_top_corner_cut_type] takes precedence
     * over [R.styleable.CornerCutLinearLayout_ccll_start_top_corner_cut_type]
     */
    var startTopCornerCutType by Delegate(DEFAULT_CORNER_CUT_TYPE)

    /**
     * [CornerCutType] for view end top corner cutout.
     * Depends on this view layout direction.
     * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
     * @see [CornerCutType]
     * @see [rightTopCornerCutType]
     * @see [leftTopCornerCutType]
     * Also see convenience functions.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_end_top_corner_cut_type]
     *
     * Note that (in LTR, for example) [R.styleable.CornerCutLinearLayout_ccll_right_top_corner_cut_type] takes precedence
     * over [R.styleable.CornerCutLinearLayout_ccll_end_top_corner_cut_type]
     */
    var endTopCornerCutType by Delegate(DEFAULT_CORNER_CUT_TYPE)

    /**
     * [CornerCutType] for view right bottom corner cutout.
     * Depends on this view layout direction.
     * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
     * @see [CornerCutType]
     * @see [leftBottomCornerCutType]
     * @see [rightBottomCornerCutType]
     * Also see convenience functions.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_end_bottom_corner_cut_type]
     *
     * Note that (in LTR, for example) [R.styleable.CornerCutLinearLayout_ccll_right_bottom_corner_cut_type] takes precedence
     * over [R.styleable.CornerCutLinearLayout_ccll_end_bottom_corner_cut_type]
     */
    var endBottomCornerCutType by Delegate(DEFAULT_CORNER_CUT_TYPE)

    /**
     * [CornerCutType] for view start bottom corner cutout.
     * Depends on this view layout direction.
     * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
     * @see [CornerCutType]
     * @see [leftBottomCornerCutType]
     * @see [rightBottomCornerCutType]
     * Also see convenience functions.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_start_bottom_corner_cut_type]
     *
     * Note that (in LTR, for example) [R.styleable.CornerCutLinearLayout_ccll_left_bottom_corner_cut_type] takes precedence
     * over [R.styleable.CornerCutLinearLayout_ccll_start_bottom_corner_cut_type]
     */
    var startBottomCornerCutType by Delegate(DEFAULT_CORNER_CUT_TYPE)

    /**
     * [CornerCutType] for view left top corner cutout.
     * Does not depend on this view layout direction.
     * @see [CornerCutType]
     * @see [startTopCornerCutType]
     * Also see convenience functions.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_left_top_corner_cut_type]
     *
     * Note that (in LTR, for example) [R.styleable.CornerCutLinearLayout_ccll_left_top_corner_cut_type] takes precedence
     * over [R.styleable.CornerCutLinearLayout_ccll_start_top_corner_cut_type]
     */
    var leftTopCornerCutType: CornerCutType
        set(value) = when (isLtr) {
            true -> startTopCornerCutType = value
            false -> endTopCornerCutType = value
        }
        get() = when (isLtr) {
            true -> startTopCornerCutType
            false -> endTopCornerCutType
        }

    /**
     * [CornerCutType] for view right top corner cutout.
     * Does not depend on this view layout direction.
     * @see [CornerCutType]
     * @see [endTopCornerCutType]
     * Also see convenience functions.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_right_top_corner_cut_type]
     *
     * Note that (in LTR, for example) [R.styleable.CornerCutLinearLayout_ccll_right_top_corner_cut_type] takes precedence
     * over [R.styleable.CornerCutLinearLayout_ccll_end_top_corner_cut_type]
     */
    var rightTopCornerCutType: CornerCutType
        set(value) = when (isLtr) {
            true -> endTopCornerCutType = value
            false -> startTopCornerCutType = value
        }
        get() = when (isLtr) {
            true -> endTopCornerCutType
            false -> startTopCornerCutType
        }

    /**
     * [CornerCutType] for view right bottom corner cutout.
     * Does not depend on this view layout direction.
     * @see [CornerCutType]
     * @see [endBottomCornerCutType]
     * Also see convenience functions.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_right_bottom_corner_cut_type]
     *
     * Note that (in LTR, for example) [R.styleable.CornerCutLinearLayout_ccll_right_bottom_corner_cut_type] takes precedence
     * over [R.styleable.CornerCutLinearLayout_ccll_end_bottom_corner_cut_type]
     */
    var rightBottomCornerCutType: CornerCutType
        set(value) = when (isLtr) {
            true -> endBottomCornerCutType = value
            false -> startBottomCornerCutType = value
        }
        get() = when (isLtr) {
            true -> endBottomCornerCutType
            false -> startBottomCornerCutType
        }

    /**
     * [CornerCutType] for view left bottom corner cutout.
     * Does not depend on this view layout direction.
     * @see [CornerCutType]
     * @see [startBottomCornerCutType]
     * Also see convenience functions.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_left_bottom_corner_cut_type]
     *
     * Note that (in LTR, for example) [R.styleable.CornerCutLinearLayout_ccll_left_bottom_corner_cut_type] takes precedence
     * over [R.styleable.CornerCutLinearLayout_ccll_start_bottom_corner_cut_type]
     */
    var leftBottomCornerCutType: CornerCutType
        set(value) = when (isLtr) {
            true -> startBottomCornerCutType = value
            false -> endBottomCornerCutType = value
        }
        get() = when (isLtr) {
            true -> startBottomCornerCutType
            false -> endBottomCornerCutType
        }

    /**
     * User defined cutout corner flag for this [CornerCutLinearLayout] padded bounds.
     * @see [CornerCutFlag]
     * @see [CornerCutType]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_flag]
     */
    var cornerCutFlag = combineFlags(START_TOP, END_TOP, END_BOTTOM, START_BOTTOM)
        set(value) {
            field = max(0, value)
            invalidateCornerCutPath()
        }

    /**
     * Depth of corner radius for [CornerCutType.RECTANGLE] or [CornerCutType.RECTANGLE_INVERSE] corner cut types.
     * Note that this corner radius depth could be less or equal to respective corner's cut depth.
     * In this case it would be visually the same as [CornerCutType.OVAL]
     * and [CornerCutType.OVAL_INVERSE], respectively.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_rectangle_type_corner_cut_radius_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_rectangle_type_corner_cut_radius]
     */
    var rectangleTypeCornerCutRoundCornerRadiusDepth by Delegate(0.0F) { coerceAtLeast(0.0F) }

    /**
     * Length of corner radius for [CornerCutType.RECTANGLE] or [CornerCutType.RECTANGLE_INVERSE] corner cut types.
     * Note that this corner radius length could be less or equal to respective corner's cut length. In this case it would be visually the same as [CornerCutType.OVAL]
     * and [CornerCutType.OVAL_INVERSE], respectively.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_rectangle_type_corner_cut_radius_length]
     * [R.styleable.CornerCutLinearLayout_ccll_rectangle_type_corner_cut_radius]
     */
    var rectangleTypeCornerCutRoundCornerRadiusLength by Delegate(0.0F) { coerceAtLeast(0.0F) }

    /**
     * This parameter force to override each corner size (depth and length)
     * to its max admissible (max) values (depth and length).
     * For instance, when orientation is [LinearLayout.VERTICAL]:
     * max depth = half of view padded bound's width
     * max length = half of view padded bound's height
     * Default is false.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_should_use_max_allowed_corner_cut_size]
     */
    var shouldUseMaxAllowedCornerCutSize by Delegate(false)

    /**
     * Works only when [shouldUseMaxAllowedCornerCutSize] is set to true (enabled)
     * This force to pick half of this view min width of height size as depth and length for corner cut size
     * Default is false.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_should_use_max_allowed_corner_cut_depth_or_length_to_be_equal]
     *
     * Example (LTR):
     * [shouldUseMaxAllowedCornerCutSize] = true
     * [leftTopCornerCutType] = [CornerCutType.RECTANGLE_INVERSE]
     * [cornerCutFlag] = [CornerCutFlag.START_TOP]
     * [shouldUseMaxAllowedCornerCutDepthOrLengthToBeEqual] = false
     *
     *              ___________
     *             |           | - depth < length
     *             |           | - depth = half padded view width
     *             |           | - length = half padded view height
     *             |           |
     *             |           |
     *             |           |
     *  ------------ - - - - - | - padded view half height
     *  |                      |
     *  |                      |
     *  |                      |
     *  |                      |
     *  |                      |
     *  |                      |
     *  ------------------------
     *
     * [shouldUseMaxAllowedCornerCutSize] = true
     * [leftTopCornerCutType] = [CornerCutType.RECTANGLE_INVERSE]
     * [cornerCutFlag] = [CornerCutFlag.START_TOP]
     * [shouldUseMaxAllowedCornerCutDepthOrLengthToBeEqual] = true
     *
     *              ___________
     *             |           | - depth = length = half padded view width = half min view dimension
     *             |           |
     *             |           |
     *  ------------           |
     *  |                      |
     *  |                      |
     *  |  - - - - - - - - - - | - padded view half height
     *  |                      |
     *  |                      |
     *  |                      |
     *  |                      |
     *  |                      |
     *  |                      |
     *  ------------------------
     */
    var shouldUseMaxAllowedCornerCutDepthOrLengthToBeEqual by Delegate(false)
    //endregion

    //region Children Corner Cut
    /**
     * Flag that defines which child side would be cut out.
     * @see [ChildSideCutFlag]
     * By default both sides are cut out.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_child_side_cut_flag]
     */
    var childSideCutFlag = combineFlags(ChildSideCutFlag.START, ChildSideCutFlag.END)
        set(value) {
            field = max(0, value)
            invalidateCornerCutPath()
        }

    /**
     * Global [CornerCutType] for start side.
     * Note that it could be overridden by each child specifically.
     * @see [childStartSideCutTypes]
     * @see [ChildSideCutFlag]
     * @see [CornerCutType]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_type]
     */
    var childStartSideCornerCutType by Delegate(DEFAULT_CHILD_CORNER_CUT_TYPE)

    /**
     * Global [CornerCutType] for end side.
     * Note that it could be overridden by each child specifically.
     * @see [childStartSideCutTypes]
     * @see [ChildSideCutFlag]
     * @see [CornerCutType]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_type]
     */
    var childEndSideCornerCutType by Delegate(DEFAULT_CHILD_CORNER_CUT_TYPE)

    /**
     * The same as [cornerCutDimensions] depth's, but works specifically for children start side
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_size]
     * [R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_size]
     */
    var childStartSideCornerCutDepth by Delegate(0.0F) { coerceAtLeast(0.0F) }

    /**
     * The same as [cornerCutDimensions] length's, but works specifically for children start side
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_length]
     * [R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_size]
     * [R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_length]
     * [R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_size]
     */
    var childStartSideCornerCutLength by Delegate(0.0F) { coerceAtLeast(0.0F) }

    /**
     * The same as [cornerCutDimensions] depth's, but works specifically for children end side
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_size]
     * [R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_size]
     */
    var childEndSideCornerCutDepth by Delegate(0.0F) { coerceAtLeast(0.0F) }

    /**
     * The same as [cornerCutDimensions] length's, but works specifically for children end side
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_length]
     * [R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_size]
     * [R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_length]
     * [R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_size]
     */
    var childEndSideCornerCutLength by Delegate(0.0F) { coerceAtLeast(0.0F) }

    /**
     * Similar to [rectangleTypeCornerCutRoundCornerRadiusDepth], but works specifically for children
     * @see [rectangleTypeCornerCutRoundCornerRadiusDepth]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_child_rectangle_type_corner_cut_radius_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_child_rectangle_type_corner_cut_radius]
     */
    var childRectangleTypeCornerCutRoundCornerRadiusDepth by Delegate(0.0F) { coerceAtLeast(0.0F) }

    /**
     * Similar to [rectangleTypeCornerCutRoundCornerRadiusLength], but works specifically for children
     * @see [rectangleTypeCornerCutRoundCornerRadiusLength]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_child_rectangle_type_corner_cut_radius_length]
     * [R.styleable.CornerCutLinearLayout_ccll_child_rectangle_type_corner_cut_radius]
     */
    var childRectangleTypeCornerCutRoundCornerRadiusLength by Delegate(0.0F) { coerceAtLeast(0.0F) }

    /**
     * Depth offset for all start side cutouts.
     * For LTR [LinearLayout.VERTICAL] orientation this means x offset.
     * Note that positive value always shift cutout toward view center
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_depth_offset]
     * [R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_depth_offset]
     *
     * Example ([LinearLayout.VERTICAL], LTR):
     *
     * [childStartSideCornerCutDepthOffset] = 0 | [childStartSideCornerCutDepthOffset] = 4
     *                                          |
     *       ----------                         |            -----------
     *       |                                  |            |
     *       |                                  |            |
     *       |                                  |            |
     *  -----------                             |           -----------
     *  |         |-----                        |           |         |--
     *  -----------                             |           -----------
     *       |                                  |            |
     *       |                                  |            |
     *       -----------                        |            ------------
     *
     * Example ([LinearLayout.HORIZONTAL], LTR):
     *
     * [childStartSideCornerCutDepthOffset] = 0  | [childStartSideCornerCutDepthOffset] = -2
     *                                           |
     *   |              |             |          |      |              |             |
     *   |              |             |          |      |              |             |
     *   |              |             |          |      |              |             |
     *   |        |-----------|       |          |      |              |             |
     *   |        |           |       |          |      |              |             |
     *   |________|           |_______|          |      |________|-----------|_______|
     *            |           |                  |               |           |
     *            |           |                  |               |           |
     *            |-----------|                  |               |           |
     *                                           |               |           |
     *                                           |               |-----------|
     *                                           |
     */
    var childStartSideCornerCutDepthOffset by Delegate(0.0F)

    /**
     * Length offset for all start side cutouts.
     * For [LinearLayout.VERTICAL] orientation this means y offset.
     *
     * [LinearLayout.VERTICAL]: positive values shifts cutouts down (same as view's translation, etc.)
     * [LinearLayout.HORIZONTAL]: positive values shifts cutouts toward end direction (from start to end).
     * In LTR positive values would shift cutout to right.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_length_offset]
     * [R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_length_offset]
     *
     * Example ([LinearLayout.VERTICAL], LTR):
     *
     * [childStartSideCornerCutLengthOffset] = 0 | [childStartSideCornerCutLengthOffset] = -2
     *                                           |
     *       ----------                          |            -----------
     *       |                                   |            |
     *       |                                   |       -----------
     *       |                                   |       |         |
     *  -----------                              |       -----------
     *  |         |-----                         |            |-----------
     *  -----------                              |            |
     *       |                                   |            |
     *       |                                   |            |
     *       -----------                         |            ------------
     *
     * Example ([LinearLayout.HORIZONTAL], LTR):
     *
     * [childStartSideCornerCutLengthOffset] = 0 | [childStartSideCornerCutLengthOffset] = 5
     *                                           |
     *   |       |               |               |      |       |               |
     *   |       |               |               |      |       |               |
     *   |       |               |               |      |       |               |
     *   |    |-----|            |               |      |       |  |-----|      |
     *   |    |     |            |               |      |       |  |     |      |
     *   |____|     |____________|               |      |_______|__|     |______|
     *        |     |                            |                 |     |
     *        |     |                            |                 |     |
     *        |-----|                            |                 |-----|
     *                                           |
     */
    var childStartSideCornerCutLengthOffset by Delegate(0.0F)

    /**
     * Depth offset for all end side cutouts.
     * For [LinearLayout.VERTICAL] orientation this means x offset.
     * Note that positive value always shift cutout toward view center
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_depth_offset]
     * [R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_depth_offset]
     *
     * Example ([LinearLayout.VERTICAL], LTR):
     *
     * [childEndSideCornerCutDepthOffset] = 0    | [childEndSideCornerCutDepthOffset] = 4
     *                                           |
     *  ----------                               |      -----------
     *           |                               |                |
     *           |                               |                |
     *           |                               |                |
     *      -----------                          |       -----------
     *  ----|         |                          |     --|         |
     *      -----------                          |       -----------
     *           |                               |                |
     *           |                               |                |
     * -----------                               |     ------------
     *
     * Example ([LinearLayout.HORIZONTAL], LTR):
     *
     * [childEndSideCornerCutDepthOffset] = 0    | [childEndSideCornerCutDepthOffset] = 2
     *                                           |
     *            |-----------|                  |
     *            |           |                  |
     *    ________|           |_______           |       ________|-----------|_______
     *   |        |           |       |          |      |        |           |       |
     *   |        |           |       |          |      |        |           |       |
     *   |        |-----------|       |          |      |        |           |       |
     *   |              |             |          |      |        |           |       |
     *   |              |             |          |      |        |-----------|       |
     *   |              |             |          |      |              |             |
     *                                           |
     *
     */
    var childEndSideCornerCutDepthOffset by Delegate(0.0F)

    /**
     * Length offset for all end side cutouts.
     * For [LinearLayout.VERTICAL] orientation this means y offset.
     *
     * [LinearLayout.VERTICAL]: positive values shifts cutouts down (same as view's translation, etc.)
     * [LinearLayout.HORIZONTAL]: positive values shifts cutouts toward end direction (from start to end).
     * In LTR positive values would shift cutout to right.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_length_offset]
     * [R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_length_offset]
     *
     * Example ([LinearLayout.VERTICAL], LTR):
     *
     * [childEndSideCornerCutLengthOffset] = 0    | [childEndSideCornerCutLengthOffset] = -3
     *                                           |
     *  ----------                               |      ----------|
     *           |                               |          -----------
     *           |                               |          |         |
     *           |                               |          -----------
     *      -----------                          |                |
     *  ----|         |                          |     -----------|
     *      -----------                          |                |
     *           |                               |                |
     *           |                               |                |
     * -----------                               |     ------------
     *
     * Example ([LinearLayout.HORIZONTAL], LTR):
     *
     * [childEndSideCornerCutLengthOffset] = 0    | [childEndSideCornerCutLengthOffset] = 5
     *                                           |
     *        |-----|                            |                 |-----|
     *        |     |                            |                 |     |
     *   _____|     |____________                |      ___________|     |_______
     *   |    |     |           |                |      |       |  |     |      |
     *   |    |     |           |                |      |       |  |     |      |
     *   |    |-----|           |                |      |       |  |-----|      |
     *   |       |              |                |      |       |               |
     *   |       |              |                |      |       |               |
     */
    var childEndSideCornerCutLengthOffset by Delegate(0.0F)

    /**
     * Rotate child start cutouts clockwise in degrees around respective cutout implicit bounds.
     * Note that rotation occurs only after [childStartSideCornerCutDepthOffset] & [childStartSideCornerCutLengthOffset] are applied.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_rotation_degree]
     * [R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_rotation_degree]
     */
    var childStartSideCornerCutRotationDegree by Delegate(0.0F) { normalizeDegree() }

    /**
     * Rotate child end cutouts clockwise in degrees around respective cutout implicit bounds.
     * Note that rotation occurs only after [childStartSideCornerCutDepthOffset] & [childStartSideCornerCutLengthOffset] are applied.
     * This values is ignored when [isChildCornerCutEndRotationMirroredFromStartRotation] enabled.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_rotation_degree]
     * [R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_rotation_degree]
     */
    var childEndSideCornerCutRotationDegree by Delegate(0.0F) { normalizeDegree() }

    /**
     *  Virtually mirror [childStartSideCornerCutRotationDegree] values as [childEndSideCornerCutRotationDegree].
     *  If enabled [childEndSideCornerCutRotationDegree] real values are ignored, though stored.
     *
     *  For example if [childStartSideCornerCutRotationDegree] = 45 degrees,
     *  then (only visually) [childEndSideCornerCutRotationDegree] = 315 degrees (-45 degrees).
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_is_child_corner_cut_end_rotation_mirrored_from_start_rotation]
     */
    var isChildCornerCutEndRotationMirroredFromStartRotation by Delegate(false)
    //endregion

    //region Custom Shadow
    /**
     * Shadow bitmap that are drawn on software layer
     */
    private var customShadowBitmap: Bitmap? = null

    /**
     * [Canvas] that responsible to draw a custom shadow on [customShadowBitmap]
     */
    private val customShadowCanvas = Canvas()

    /**
     * [Paint] that responsible for colors, offsets, radius of custom shadow.
     * Shadow actually are created by setting this layer shadowLayer ([Paint.setShadowLayer])
     * that requires itself [View.LAYER_TYPE_SOFTWARE] layer type during drawing.
     */
    private var customShadowPaint: Paint = object : Paint(
        combineFlags(Paint.DITHER_FLAG, Paint.ANTI_ALIAS_FLAG, Paint.FILTER_BITMAP_FLAG)
    ) {
        init {
            color = Color.TRANSPARENT
        }
    }

    /**
     * Custom shadow radius (expanded equally to each side).
     * This shadow could be offset by [customShadowOffsetDx] & [customShadowOffsetDy]
     *
     * @see [userDefinedPadding]
     * @see [composedPadding]
     * @see [isCustomShadowAutoPaddingEnabled]
     * @see [couldDrawCustomShadowOverUserDefinedPadding]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_custom_shadow_radius]
     */
    var customShadowRadius = 0.0F
        set(value) {
            val newValue = value.coerceAtLeast(0.0F)
            if (field == newValue) return
            field = newValue
            customShadowPaint.setShadowLayer(
                newValue,
                customShadowOffsetDx,
                customShadowOffsetDy,
                customShadowColor
            )
            invalidateComposedPadding()
            invalidateCornerCutPath()
        }

    /**
     * Horizontal offset (dx) for custom shadow.
     * Note that this property is independent from this [CornerCutLinearLayout] orientation.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_custom_shadow_offset_dx]
     */
    var customShadowOffsetDx = 0.0F
        set(value) {
            if (field == value) return
            field = value
            customShadowPaint.setShadowLayer(
                customShadowRadius,
                value,
                customShadowOffsetDy,
                customShadowColor
            )
            invalidateComposedPadding()
            invalidateCornerCutPath()
        }

    /**
     * Vertical offset (dy) for custom shadow.
     * Note that this property is independent from this [CornerCutLinearLayout] orientation.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_custom_shadow_offset_dy]
     */
    var customShadowOffsetDy = 0.0F
        set(value) {
            if (field == value) return
            field = value
            customShadowPaint.setShadowLayer(
                customShadowRadius,
                customShadowOffsetDx,
                value,
                customShadowColor
            )
            invalidateComposedPadding()
            invalidateCornerCutPath()
        }

    /**
     * The initial color of custom shadow that along its distance ([customShadowRadius]) fades to transparency.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_custom_shadow_color]
     */
    @ColorInt
    var customShadowColor = ColorUtils.setAlphaComponent(Color.BLACK, 255 / 3)
        set(value) {
            val newColor = if (value.alpha < 255) value else ColorUtils.setAlphaComponent(
                value,
                254
            ) /*workaround*/
            if (field == newColor) return
            field = value
            customShadowPaint.setShadowLayer(
                customShadowRadius,
                customShadowOffsetDx,
                customShadowOffsetDy,
                newColor
            )
            invalidateCornerCutPath()
        }

    /**
     * This property allows this view to manage and override (if needed) extra padding for custom shadow.
     * @see [couldDrawCustomShadowOverUserDefinedPadding]
     * @see [userDefinedPadding]
     * @see [composedPadding]
     *
     * Default is true.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_is_custom_shadow_auto_padding_enabled]
     */
    var isCustomShadowAutoPaddingEnabled = true
        set(value) {
            if (field == value) return
            field = value
            invalidateComposedPadding()
            invalidateCornerCutPath()
        }

    /**
     * This property works only when [isCustomShadowAutoPaddingEnabled] = true
     * This property allows to expand user defined padding only when it is necessary to draw and fit custom shadow
     * Setting this property to false forces to reserve extra padding for custom shadow and do not overlap user defined padding ([userDefinedPadding]).
     * Setting this property to true allow to draw custom shadow over user defined padding.
     * Note that if there is not enough space while [couldDrawCustomShadowOverUserDefinedPadding] = true &
     * [isCustomShadowAutoPaddingEnabled] = true extra padding to fit shadow will automatically be added.
     * For instance:
     * [isCustomShadowAutoPaddingEnabled] = true
     * [couldDrawCustomShadowOverUserDefinedPadding] = true
     * paddingLeft = 16dp
     * [customShadowRadius] = 24dp [customShadowOffsetDx] = 4dp
     * resulting left padding will will be 20dp (4dp added extra over paddingLeft (16dp))
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_could_draw_custom_shadow_over_user_defined_padding]
     */
    var couldDrawCustomShadowOverUserDefinedPadding = true
        set(value) {
            if (field == value) return
            field = value
            invalidateComposedPadding()
            invalidateCornerCutPath()
        }
    //endregion

    //region Custom Divider
    /**
     * Path that holds temporary divider bounds and shape.
     * Used to visually place divider and take into consideration its [customCustomDividerGravity]
     * @see [CustomDividerGravity]
     */
    private val customDividerPath = Path()

    /**
     * Paint responsible for custom divider's color, width, dash/gap, line cap, etc.
     */
    val customDividerPaint = object : Paint(
        combineFlags(Paint.DITHER_FLAG, Paint.ANTI_ALIAS_FLAG, Paint.FILTER_BITMAP_FLAG)
    ) {
        init {
            isDither = true
            isAntiAlias = true
            style = Style.STROKE
        }
    }

    /**
     * Height of custom divider line (or dashed line). In other words, stroke width.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_custom_divider_height]
     */
    var customDividerHeight by SimpleNonNullDelegate(
        initialValue = 0.0F,
        beforeSetPredicate = { coerceAtLeast(0.0F) },
        afterSetPredicate = { customDividerPaint.strokeWidth = this; invalidate() }
    )

    /**
     * Custom divider start padding (from padded view bounds).
     * For [LinearLayout.HORIZONTAL]:
     * LTR - offset from bottom toward view center,
     * RTL - offset from top toward view center
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_custom_divider_padding_start]
     * [R.styleable.CornerCutLinearLayout_ccll_custom_divider_padding]
     */
    var customDividerPaddingStart by SimpleNonNullDelegate(
        initialValue = 0.0F,
        beforeSetPredicate = { coerceAtLeast(0.0F) },
        afterSetPredicate = { invalidateCustomDividerIfNeeded() }
    )

    /**
     * Custom divider end padding (from padded view bounds).
     * For [LinearLayout.HORIZONTAL]:
     * LTR - offset from top toward view center,
     * RTL - offset from bottom toward view center
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_custom_divider_padding_end]
     * [R.styleable.CornerCutLinearLayout_ccll_custom_divider_padding]
     */
    var customDividerPaddingEnd by SimpleNonNullDelegate(
        initialValue = 0.0F,
        beforeSetPredicate = { coerceAtLeast(0.0F) },
        afterSetPredicate = { invalidateCustomDividerIfNeeded() }
    )

    /**
     * @ColorInt
     * Custom divider color.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_custom_divider_color]
     */
    var customDividerColor by SimpleNonNullDelegate(
        initialValue = Color.LTGRAY,
        afterSetPredicate = {
            customDividerPaint.color = this
            invalidateCustomDividerIfNeeded()
        }
    )

    /**
     * Custom Divider line (or dashed line) cap.
     * @see [Paint.Cap] values.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_custom_divider_line_cap]
     */
    var customDividerLineCap by SimpleNonNullDelegate(
        initialValue = Paint.Cap.BUTT,
        afterSetPredicate = {
            customDividerPaint.strokeCap = this
            invalidateCustomDividerIfNeeded()
        }
    )

    /**
     * Flag to define where to show custom divider. Similar to [LinearLayout].SHOW_DIVIDER_* flags,
     * but slightly more complex because support also divider for view start and end
     * (respectively [CustomDividerShowFlag.CONTAINER_BEGINNING] & [CustomDividerShowFlag.CONTAINER_END]).
     * @see [CustomDividerShowFlag]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_custom_divider_show_flag]
     */
    private var customDividerShowFlag by SimpleNonNullDelegate(
        initialValue = CustomDividerShowFlag.NONE,
        afterSetPredicate = { invalidateCustomDividerIfNeeded() }
    )

    /**
     * Custom divider dash width.
     *
     * @see [setCustomDividerDash]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_custom_divider_dash_width]
     */
    private var customDividerDashWidth: Float = 0.0F

    /**
     * Custom divider dash gap.
     *
     * @see [setCustomDividerDash]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_custom_divider_dash_gap]
     */
    private var customDividerDashGap: Float = 0.0F

    /**
     * Only useful when drawing dashed divider.
     *
     * @see [CustomDividerGravity]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_custom_divider_gravity]
     *
     * Examples.
     *
     * Where:
     * === === === - custom dashed divider
     *
     * |             |
     * |             | - View (view width)
     * |             |
     *
     * [CustomDividerGravity.START] (LTR)
     *
     *                    |                 |
     *   |             |  |  |            | |  |           |
     *   | === === === |  |  | === === == | |  | === === = |
     *   |             |  |  |            | |  |           |
     *                    |                 |
     *
    [CustomDividerGravity.CENTER]
     *
     *                    |                 |
     *   |             |  |  |           |  |  |         |
     *   | === === === |  |  | == === == |  |  | = === = |
     *   |             |  |  |           |  |  |         |
     *
     * [CustomDividerGravity.END] (LTR)
     *
     *                    |                 |
     *   |             |  |  |            | |  |           |
     *   | === === === |  |  | == === === | |  | = === === |
     *   |             |  |  |            | |  |           |
     *                    |                 |
     */
    var customCustomDividerGravity by SimpleNonNullDelegate(
        initialValue = CustomDividerGravity.CENTER,
        afterSetPredicate = { invalidateCustomDividerIfNeeded() }
    )

    //region Custom Divider Provider Properties
    /**
     * Path that is used in [CustomDividerProvider].
     * Could also be modified from provider [CustomDividerProvider.getDivider] dividerPath param.
     * Could be useful when it is possible and efficient to set provider's path globally
     * and not each time during [CustomDividerProvider.getDivider] function's call.
     * @see [CustomDividerProvider]
     */
    val customDividerProviderPath = Path()

    /**
     * Paint that is used in [CustomDividerProvider].
     * Could also be modified from provider [CustomDividerProvider.getDivider] dividerPaint param.
     * Could be useful when it is possible and efficient to set provider's paint setting globally
     * and not each time during [CustomDividerProvider.getDivider] function's call.
     * @see [CustomDividerProvider]
     */
    val customDividerProviderPaint = Paint(
        combineFlags(Paint.DITHER_FLAG, Paint.ANTI_ALIAS_FLAG, Paint.FILTER_BITMAP_FLAG)
    )
    //endregion
    //endregion

    var cornerCutProvider: CornerCutProvider? by NullableDelegate(null)
    var childCornerCutProvider: ChildCornerCutProvider? by NullableDelegate(null)
    private val customCutoutProviders = mutableSetOf<CustomCutoutProvider>()

    var customDividerProvider: CustomDividerProvider? by NullableDelegate(null)

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet? = null) {
        setWillNotDraw(false)

        try {
            //region Attributes
            val typedArray =
                context.obtainStyledAttributes(attrs, R.styleable.CornerCutLinearLayout)
            try {
                //region Global View Corner Cut Properties
                //region Corner Cut Types
                val cornerCutType = CornerCutType.values()[typedArray.get(
                    attr = R.styleable.CornerCutLinearLayout_ccll_corner_cut_type,
                    default = DEFAULT_CORNER_CUT_TYPE.ordinal
                )]

                val startCornersCutType = CornerCutType.values().getOrNull(
                    typedArray.get(
                        attr = R.styleable.CornerCutLinearLayout_ccll_start_corners_cut_type,
                        default = -1
                    )
                )

                val endCornersCutType = CornerCutType.values().getOrNull(
                    typedArray.get(
                        attr = R.styleable.CornerCutLinearLayout_ccll_end_corners_cut_type,
                        default = -1
                    )
                )

                val leftCornersCutType = CornerCutType.values().getOrNull(
                    typedArray.get(
                        attr = R.styleable.CornerCutLinearLayout_ccll_left_corners_cut_type,
                        default = -1
                    )
                )

                val rightCornersCutType = CornerCutType.values().getOrNull(
                    typedArray.get(
                        attr = R.styleable.CornerCutLinearLayout_ccll_right_corners_cut_type,
                        default = -1
                    )
                )

                val topCornersCutType = CornerCutType.values().getOrNull(
                    typedArray.get(
                        attr = R.styleable.CornerCutLinearLayout_ccll_top_corners_cut_type,
                        default = -1
                    )
                )

                val bottomCornersCutType = CornerCutType.values().getOrNull(
                    typedArray.get(
                        attr = R.styleable.CornerCutLinearLayout_ccll_bottom_corners_cut_type,
                        default = -1
                    )
                )

                val leftTopCornerCutType = CornerCutType.values().getOrNull(
                    typedArray.get(
                        attr = R.styleable.CornerCutLinearLayout_ccll_left_top_corner_cut_type,
                        default = -1
                    )
                )

                val rightTopCornerCutType = CornerCutType.values().getOrNull(
                    typedArray.get(
                        attr = R.styleable.CornerCutLinearLayout_ccll_right_top_corner_cut_type,
                        default = -1
                    )
                )

                val rightBottomCornerCutType = CornerCutType.values().getOrNull(
                    typedArray.get(
                        attr = R.styleable.CornerCutLinearLayout_ccll_right_bottom_corner_cut_type,
                        default = -1
                    )
                )

                val leftBottomCornerCutType = CornerCutType.values().getOrNull(
                    typedArray.get(
                        attr = R.styleable.CornerCutLinearLayout_ccll_left_bottom_corner_cut_type,
                        default = -1
                    )
                )

                val startTopCornerCutType = CornerCutType.values().getOrNull(
                    typedArray.get(
                        attr = R.styleable.CornerCutLinearLayout_ccll_start_top_corner_cut_type,
                        default = -1
                    )
                )

                val endTopCornerCutType = CornerCutType.values().getOrNull(
                    typedArray.get(
                        attr = R.styleable.CornerCutLinearLayout_ccll_end_top_corner_cut_type,
                        default = -1
                    )
                )

                val endBottomCornerCutType = CornerCutType.values().getOrNull(
                    typedArray.get(
                        attr = R.styleable.CornerCutLinearLayout_ccll_end_bottom_corner_cut_type,
                        default = -1
                    )
                )

                val startBottomCornerCutType = CornerCutType.values().getOrNull(
                    typedArray.get(
                        attr = R.styleable.CornerCutLinearLayout_ccll_start_bottom_corner_cut_type,
                        default = -1
                    )
                )
                //endregion

                //region Corner Cut Dimensions (Depth, Length & Size)

                //region Priority 1 (cornerCutSize)
                val cornerCutSize = typedArray.getDimension(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_size,
                    DEFAULT_CORNER_CUT_SIZE
                )
                //endregion

                //region Priority 2 (startCornersCutSize, endCornersCutSize)
                val startCornersCutSize = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_start_corners_cut_size,
                )

                val endCornersCutSize = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_end_corners_cut_size,
                )
                //endregion

                //region Priority 3 (leftCornersCutSize, rightCornersCutSize)
                val leftCornersCutSize = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_left_corners_cut_size,
                )
                val rightCornersCutSize = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_left_corners_cut_size,
                )
                //endregion

                //region Priority 4 (topCornersCutSize, bottomCornersCutSize)
                val topCornersCutSize = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_top_corners_cut_size,
                )

                val bottomCornersCutSize = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_bottom_corners_cut_size,
                )
                //endregion

                //region Priority 5 (cornerCutDepth, cornerCutLength)
                val cornerCutDepth = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_depth,
                )

                val cornerCutLength = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_length,
                )
                //endregion

                //region Priority 6 (startCornersCutDepth, endCornersCutDepth, startCornersCutLength, endCornersCutLength)
                val startCornersCutDepth = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_start_corners_cut_depth,
                )

                val endCornersCutDepth = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_end_corners_cut_depth,
                )

                val startCornersCutLength = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_start_corners_cut_length,
                )

                val endCornersCutLength = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_end_corners_cut_length,
                )
                //endregion

                //region Priority 7 (leftCornersCutDepth, rightCornersCutDepth, leftCornersCutLength, rightCornersCutLength)
                val leftCornersCutDepth = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_left_corners_cut_depth,
                )

                val rightCornersCutDepth = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_right_corners_cut_depth,
                )

                val leftCornersCutLength = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_left_corners_cut_length,
                )

                val rightCornersCutLength = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_right_corners_cut_length,
                )
                //endregion

                //region Priority 8 (topCornersCutDepth, bottomCornersCutDepth, topCornersCutLength, bottomCornersCutLength)
                val topCornersCutDepth = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_top_corners_cut_depth,
                )

                val bottomCornersCutDepth = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_bottom_corners_cut_depth,
                )

                val topCornersCutLength = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_top_corners_cut_length,
                )

                val bottomCornersCutLength = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_bottom_corners_cut_length,
                )
                //endregion

                //region Priority 9 (cornerCutStartTopSize, cornerCutEndTopSize, cornerCutEndBottomSize, cornerCutStartBottomSize)
                val cornerCutStartTopSize = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_top_size,
                )

                val cornerCutEndTopSize = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_top_size,
                )

                val cornerCutEndBottomSize = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_bottom_size,
                )

                val cornerCutStartBottomSize = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_bottom_size,
                )
                //endregion

                //region Priority 10 (cornerCutLeftTopSize, cornerCutRightTopSize, cornerCutLeftBottomSize, cornerCutRightBottomSize)
                val cornerCutLeftTopSize = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_top_size,
                )

                val cornerCutRightTopSize = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_top_size,
                )

                val cornerCutLeftBottomSize = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_bottom_size,
                )

                val cornerCutRightBottomSize = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_bottom_size,
                )
                //endregion

                //region Priority 11 (cornerCutStartTopDepth, cornerCutEndTopDepth, cornerCutEndBottomDepth, cornerCutStartBottomDepth, cornerCutStartTopLength, cornerCutEndTopLength, cornerCutEndBottomLength, cornerCutStartBottomLength)
                val cornerCutStartTopDepth = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_top_depth,
                )

                val cornerCutEndTopDepth = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_top_depth,
                )

                val cornerCutEndBottomDepth = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_bottom_depth,
                )

                val cornerCutStartBottomDepth = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_bottom_depth,
                )

                val cornerCutStartTopLength = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_top_length,
                )

                val cornerCutEndTopLength = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_top_length,
                )

                val cornerCutEndBottomLength = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_bottom_length,
                )

                val cornerCutStartBottomLength = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_bottom_length,
                )
                //endregion

                //region Priority 12 (cornerCutLeftTopDepth, cornerCutRightTopDepth, cornerCutLeftBottomDepth, cornerCutRightBottomDepth, cornerCutLeftTopLength, cornerCutRightTopLength, cornerCutLeftBottomLength, cornerCutRightBottomLength)
                val cornerCutLeftTopDepth = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_top_depth,
                )

                val cornerCutRightTopDepth = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_top_depth,
                )

                val cornerCutLeftBottomDepth = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_bottom_depth,
                )

                val cornerCutRightBottomDepth = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_bottom_depth,
                )

                val cornerCutLeftTopLength = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_top_length,
                )

                val cornerCutRightTopLength = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_top_length,
                )

                val cornerCutLeftBottomLength = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_bottom_length,
                )

                val cornerCutRightBottomLength = typedArray.getDimensionIfHas(
                    R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_bottom_length,
                )
                //endregion
                //endregion

                //region Other Global View Corner Cut Attributes
                if (typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_should_use_max_allowed_corner_cut_size)) {
                    shouldUseMaxAllowedCornerCutSize = typedArray.getBoolean(
                        R.styleable.CornerCutLinearLayout_ccll_should_use_max_allowed_corner_cut_size,
                        shouldUseMaxAllowedCornerCutSize
                    )
                }

                if (typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_should_use_max_allowed_corner_cut_depth_or_length_to_be_equal)) {
                    shouldUseMaxAllowedCornerCutDepthOrLengthToBeEqual = typedArray.getBoolean(
                        R.styleable.CornerCutLinearLayout_ccll_should_use_max_allowed_corner_cut_depth_or_length_to_be_equal,
                        shouldUseMaxAllowedCornerCutDepthOrLengthToBeEqual
                    )
                }

                if (typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_corner_cut_flag)) {
                    cornerCutFlag = typedArray.getInt(
                        R.styleable.CornerCutLinearLayout_ccll_corner_cut_flag,
                        cornerCutFlag
                    )
                }

                val rectangleTypeCornerCutRadius = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_rectangle_type_corner_cut_radius) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_rectangle_type_corner_cut_radius,
                            DEFAULT_RECTANGLE_TYPE_CORNER_CUT_RADIUS
                        )
                    }
                    else -> DEFAULT_RECTANGLE_TYPE_CORNER_CUT_RADIUS
                }

                rectangleTypeCornerCutRoundCornerRadiusDepth = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_rectangle_type_corner_cut_radius_depth) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_rectangle_type_corner_cut_radius_depth,
                            DEFAULT_RECTANGLE_TYPE_CORNER_CUT_RADIUS
                        )
                    }
                    else -> rectangleTypeCornerCutRadius
                }
                rectangleTypeCornerCutRoundCornerRadiusLength = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_rectangle_type_corner_cut_radius_length) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_rectangle_type_corner_cut_radius_length,
                            DEFAULT_RECTANGLE_TYPE_CORNER_CUT_RADIUS
                        )
                    }
                    else -> rectangleTypeCornerCutRadius
                }
                //endregion
                //endregion

                //region Child Corner Cut Properties
                //region Child Cut Types
                if (typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_side_cut_flag)) {
                    childSideCutFlag =
                        typedArray.getInt(
                            R.styleable.CornerCutLinearLayout_ccll_child_side_cut_flag,
                            childSideCutFlag
                        )
                }

                val childCornerCutType = CornerCutType.values()[typedArray.get(
                    attr = R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_type,
                    default = DEFAULT_CHILD_CORNER_CUT_TYPE.ordinal
                )]

                childStartSideCornerCutType = CornerCutType.values()[typedArray.get(
                    attr = R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_type,
                    default = childCornerCutType.ordinal
                )]

                childEndSideCornerCutType = CornerCutType.values()[typedArray.get(
                    attr = R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_type,
                    default = childCornerCutType.ordinal
                )]
                //endregion

                //region Child Corner Cut Depth, Length & Size
                val childCornerCutSize = typedArray.getDimension(
                    R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_size,
                    defaultChildCornerCutSize
                )

                val childCornerCutDepth = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_depth) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_depth,
                            defaultChildCornerCutSize
                        )
                    }
                    else -> null
                }

                val childCornerCutLength = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_length) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_length,
                            defaultChildCornerCutSize
                        )
                    }
                    else -> null
                }

                val childStartSideCornerCutSize = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_size) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_size,
                            defaultChildCornerCutSize
                        )
                    }
                    else -> null
                }

                val childEndSideCornerCutSize = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_size) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_size,
                            defaultChildCornerCutSize
                        )
                    }
                    else -> null
                }

                childStartSideCornerCutDepth = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_depth) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_depth,
                            defaultChildCornerCutSize
                        )
                    }
                    else -> childStartSideCornerCutSize ?: childCornerCutDepth ?: childCornerCutSize
                }

                childStartSideCornerCutLength = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_length) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_length,
                            defaultChildCornerCutSize
                        )
                    }
                    else -> childStartSideCornerCutSize ?: childCornerCutLength
                    ?: childCornerCutSize
                }

                childEndSideCornerCutDepth = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_depth) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_depth,
                            defaultChildCornerCutSize
                        )
                    }
                    else -> childEndSideCornerCutSize ?: childCornerCutDepth ?: childCornerCutSize
                }

                childEndSideCornerCutLength = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_length) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_length,
                            defaultChildCornerCutSize
                        )
                    }
                    else -> childEndSideCornerCutSize ?: childCornerCutLength ?: childCornerCutSize
                }
                //endregion

                //region Child Rectangle Cut Type Corner Radii
                val childRectangleTypeCornerCutRadius = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_rectangle_type_corner_cut_radius) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_rectangle_type_corner_cut_radius,
                            DEFAULT_RECTANGLE_TYPE_CORNER_CUT_RADIUS
                        )
                    }
                    else -> DEFAULT_RECTANGLE_TYPE_CORNER_CUT_RADIUS
                }

                childRectangleTypeCornerCutRoundCornerRadiusDepth = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_rectangle_type_corner_cut_radius_depth) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_rectangle_type_corner_cut_radius_depth,
                            DEFAULT_RECTANGLE_TYPE_CORNER_CUT_RADIUS
                        )
                    }
                    else -> childRectangleTypeCornerCutRadius
                }
                childRectangleTypeCornerCutRoundCornerRadiusLength = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_rectangle_type_corner_cut_radius_length) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_rectangle_type_corner_cut_radius_length,
                            DEFAULT_RECTANGLE_TYPE_CORNER_CUT_RADIUS
                        )
                    }
                    else -> childRectangleTypeCornerCutRadius
                }
                //endregion

                //region Child Corner Cut Rotation
                if (typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_is_child_corner_cut_end_rotation_mirrored_from_start_rotation)) {
                    isChildCornerCutEndRotationMirroredFromStartRotation = typedArray.getBoolean(
                        R.styleable.CornerCutLinearLayout_ccll_is_child_corner_cut_end_rotation_mirrored_from_start_rotation,
                        isChildCornerCutEndRotationMirroredFromStartRotation
                    )
                }

                val childCornerCutRotationDegree = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_rotation_degree) -> {
                        typedArray.getFloat(
                            R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_rotation_degree,
                            DEFAULT_CHILD_CORNER_CUT_ROTATION_DEGREE
                        )
                    }
                    else -> DEFAULT_CHILD_CORNER_CUT_ROTATION_DEGREE
                }

                childStartSideCornerCutRotationDegree = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_rotation_degree) -> {
                        typedArray.getFloat(
                            R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_rotation_degree,
                            DEFAULT_CHILD_CORNER_CUT_ROTATION_DEGREE
                        )
                    }
                    else -> childCornerCutRotationDegree
                }

                childEndSideCornerCutRotationDegree = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_rotation_degree) -> {
                        typedArray.getFloat(
                            R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_rotation_degree,
                            DEFAULT_CHILD_CORNER_CUT_ROTATION_DEGREE
                        )
                    }
                    else -> childCornerCutRotationDegree
                }
                //endregion

                //region Child Cut Side Offsets
                val childCornerCutDepthOffset = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_depth_offset) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_depth_offset,
                            Float.MIN_VALUE
                        ).takeIf { it != Float.MIN_VALUE }
                    }
                    else -> null
                }

                val childCornerCutLengthOffset = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_length_offset) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_corner_cut_length_offset,
                            Float.MIN_VALUE
                        ).takeIf { it != Float.MIN_VALUE }
                    }
                    else -> null
                }

                childStartSideCornerCutDepthOffset = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_depth_offset) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_depth_offset,
                            childStartSideCornerCutDepthOffset
                        )
                    }
                    else -> childCornerCutDepthOffset ?: childStartSideCornerCutDepthOffset
                }

                childStartSideCornerCutLengthOffset = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_length_offset) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_start_side_corner_cut_length_offset,
                            childStartSideCornerCutLengthOffset
                        )
                    }
                    else -> childCornerCutLengthOffset ?: childStartSideCornerCutLengthOffset
                }


                childEndSideCornerCutDepthOffset = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_depth_offset) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_depth_offset,
                            childEndSideCornerCutDepthOffset
                        )
                    }
                    else -> childCornerCutDepthOffset ?: childEndSideCornerCutDepthOffset
                }

                childEndSideCornerCutLengthOffset = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_length_offset) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_child_end_side_corner_cut_length_offset,
                            childEndSideCornerCutLengthOffset
                        )
                    }
                    else -> childCornerCutLengthOffset ?: childEndSideCornerCutLengthOffset
                }
                //endregion
                //endregion

                //region Custom Divider Dash
                if (typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_custom_divider_show_flag)) {
                    customDividerShowFlag = typedArray.getInt(
                        R.styleable.CornerCutLinearLayout_ccll_custom_divider_show_flag,
                        customDividerShowFlag
                    )
                }

                if (typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_custom_divider_line_cap)) {
                    customDividerLineCap = Paint.Cap.values().getOrElse(
                        typedArray.getInt(
                            R.styleable.CornerCutLinearLayout_ccll_custom_divider_line_cap,
                            customDividerLineCap.ordinal
                        )
                    ) {
                        customDividerLineCap
                    }
                }

                if (typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_custom_divider_height)) {
                    customDividerHeight = typedArray.getDimension(
                        R.styleable.CornerCutLinearLayout_ccll_custom_divider_height,
                        customDividerHeight
                    )
                }

                val customDividerPadding = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_custom_divider_padding) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_custom_divider_padding,
                            0.0F
                        )
                    }
                    else -> 0.0F
                }

                customDividerPaddingStart = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_custom_divider_padding_start) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_custom_divider_padding_start,
                            customDividerPadding
                        )
                    }
                    else -> customDividerPadding
                }

                customDividerPaddingEnd = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_custom_divider_padding_end) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_custom_divider_padding_end,
                            customDividerPadding
                        )
                    }
                    else -> customDividerPadding
                }

                if (typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_custom_divider_color)) {
                    customDividerColor = typedArray.getColor(
                        R.styleable.CornerCutLinearLayout_ccll_custom_divider_color,
                        customDividerColor
                    )
                }

                if (typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_custom_divider_gravity)) {
                    customCustomDividerGravity = CustomDividerGravity.values().getOrElse(
                        typedArray.getInt(
                            R.styleable.CornerCutLinearLayout_ccll_custom_divider_gravity,
                            customCustomDividerGravity.ordinal
                        )
                    ) {
                        customCustomDividerGravity
                    }
                }

                val customDividerDashWidth = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_custom_divider_dash_width) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_custom_divider_dash_width,
                            0.0F
                        )
                    }
                    else -> null
                }
                val customDividerDashGap = when {
                    typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_custom_divider_dash_gap) -> {
                        typedArray.getDimension(
                            R.styleable.CornerCutLinearLayout_ccll_custom_divider_dash_gap,
                            0.0F
                        )
                    }
                    else -> null
                }

                setCustomDividerDash(customDividerDashWidth, customDividerDashGap)
                //endregion

                //region Custom Shadow
                if (typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_custom_shadow_radius)) {
                    customShadowRadius = typedArray.getDimension(
                        R.styleable.CornerCutLinearLayout_ccll_custom_shadow_radius,
                        customShadowRadius
                    )
                }

                if (typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_custom_shadow_offset_dx)) {
                    customShadowOffsetDx = typedArray.getDimension(
                        R.styleable.CornerCutLinearLayout_ccll_custom_shadow_offset_dx,
                        customShadowOffsetDx
                    )
                }

                if (typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_custom_shadow_offset_dy)) {
                    customShadowOffsetDy = typedArray.getDimension(
                        R.styleable.CornerCutLinearLayout_ccll_custom_shadow_offset_dy,
                        customShadowOffsetDy
                    )
                }

                if (typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_custom_shadow_color)) {
                    customShadowColor = typedArray.getColor(
                        R.styleable.CornerCutLinearLayout_ccll_custom_shadow_color,
                        customShadowColor
                    )
                }

                if (typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_is_custom_shadow_auto_padding_enabled)) {
                    isCustomShadowAutoPaddingEnabled = typedArray.getBoolean(
                        R.styleable.CornerCutLinearLayout_ccll_is_custom_shadow_auto_padding_enabled,
                        isCustomShadowAutoPaddingEnabled
                    )
                }

                if (typedArray.hasValue(R.styleable.CornerCutLinearLayout_ccll_could_draw_custom_shadow_over_user_defined_padding)) {
                    couldDrawCustomShadowOverUserDefinedPadding = typedArray.getBoolean(
                        R.styleable.CornerCutLinearLayout_ccll_could_draw_custom_shadow_over_user_defined_padding,
                        couldDrawCustomShadowOverUserDefinedPadding
                    )
                }
                //endregion

                // Apply All Layout Direction Related Attributes after layout direction getting resolved
                // Layout Orientation is Resolved Only after window attach
                doOnLayout {
                    val isLtr = it.isLtr
                    val isRtl = !isLtr

                    //region resolve and apply Child Cut Type
                    val layoutDirectionAwareLeftCornersCutType =
                        leftCornersCutType ?: if (isLtr) startCornersCutType else endCornersCutType
                    val layoutDirectionAwareRightCornersCutType =
                        rightCornersCutType ?: if (isLtr) endCornersCutType else startCornersCutType

                    this.leftTopCornerCutType = leftTopCornerCutType
                        ?: startTopCornerCutType.takeIf { isLtr }
                                ?: endTopCornerCutType.takeIf { isRtl }
                                ?: topCornersCutType
                                ?: layoutDirectionAwareLeftCornersCutType
                                ?: cornerCutType

                    this.rightTopCornerCutType = rightTopCornerCutType
                        ?: startTopCornerCutType.takeIf { isRtl }
                                ?: endTopCornerCutType.takeIf { isLtr }
                                ?: topCornersCutType
                                ?: layoutDirectionAwareRightCornersCutType
                                ?: cornerCutType

                    this.rightBottomCornerCutType = rightBottomCornerCutType
                        ?: startBottomCornerCutType.takeIf { isRtl }
                                ?: endBottomCornerCutType.takeIf { isLtr }
                                ?: bottomCornersCutType
                                ?: layoutDirectionAwareRightCornersCutType
                                ?: cornerCutType

                    this.leftBottomCornerCutType = leftBottomCornerCutType
                        ?: startBottomCornerCutType.takeIf { isLtr }
                                ?: endBottomCornerCutType.takeIf { isRtl }
                                ?: bottomCornersCutType
                                ?: layoutDirectionAwareLeftCornersCutType
                                ?: cornerCutType
                    //endregion

                    //region resolve and apply Corner Cut Dimensions
                    val layoutDirectionAwareLeftCornersCutSize =
                        leftCornersCutSize ?: if (isLtr) startCornersCutSize else endCornersCutSize
                    val layoutDirectionAwareRightCornersCutSize =
                        rightCornersCutSize ?: if (isLtr) endCornersCutSize else startCornersCutSize

                    val layoutDirectionAwareLeftCornersCutDepth = leftCornersCutDepth
                        ?: if (isLtr) startCornersCutDepth else endCornersCutDepth
                    val layoutDirectionAwareRightCornersCutDepth = rightCornersCutDepth
                        ?: if (isLtr) endCornersCutDepth else startCornersCutDepth
                    val layoutDirectionAwareLeftCornersCutLength = leftCornersCutLength
                        ?: if (isLtr) startCornersCutLength else endCornersCutLength
                    val layoutDirectionAwareRightCornersCutLength = rightCornersCutLength
                        ?: if (isLtr) endCornersCutLength else startCornersCutLength

                    val layoutDirectionAwareCornerCutLeftTopSize = cornerCutLeftTopSize
                        ?: if (isLtr) cornerCutStartTopSize else cornerCutEndTopSize
                    val layoutDirectionAwareCornerCutRightTopSize = cornerCutRightTopSize
                        ?: if (isLtr) cornerCutEndTopSize else cornerCutStartTopSize
                    val layoutDirectionAwareCornerCutLeftBottomSize = cornerCutLeftBottomSize
                        ?: if (isLtr) cornerCutStartBottomSize else cornerCutEndBottomSize
                    val layoutDirectionAwareCornerCutRightBottomSize = cornerCutRightBottomSize
                        ?: if (isLtr) cornerCutEndBottomSize else cornerCutStartBottomSize

                    val layoutDirectionAwareCornerCutLeftTopDepth = cornerCutLeftTopDepth
                        ?: if (isLtr) cornerCutStartTopDepth else cornerCutEndTopDepth
                    val layoutDirectionAwareCornerCutRightTopDepth = cornerCutRightTopDepth
                        ?: if (isLtr) cornerCutEndTopDepth else cornerCutStartTopDepth
                    val layoutDirectionAwareCornerCutLeftBottomDepth = cornerCutLeftBottomDepth
                        ?: if (isLtr) cornerCutStartBottomDepth else cornerCutEndBottomDepth
                    val layoutDirectionAwareCornerCutRightBottomDepth = cornerCutRightBottomDepth
                        ?: if (isLtr) cornerCutEndBottomDepth else cornerCutStartBottomDepth
                    val layoutDirectionAwareCornerCutLeftTopLength = cornerCutLeftTopLength
                        ?: if (isLtr) cornerCutStartTopLength else cornerCutEndTopLength
                    val layoutDirectionAwareCornerCutRightTopLength = cornerCutRightTopLength
                        ?: if (isLtr) cornerCutEndTopLength else cornerCutStartTopLength
                    val layoutDirectionAwareCornerCutLeftBottomLength = cornerCutLeftBottomLength
                        ?: if (isLtr) cornerCutStartBottomLength else cornerCutEndBottomLength
                    val layoutDirectionAwareCornerCutRightBottomLength = cornerCutRightBottomLength
                        ?: if (isLtr) cornerCutEndBottomLength else cornerCutStartBottomLength

                    val leftTopDepth = layoutDirectionAwareCornerCutLeftTopDepth
                        ?: layoutDirectionAwareCornerCutLeftTopSize
                        ?: topCornersCutDepth
                        ?: layoutDirectionAwareLeftCornersCutDepth
                        ?: cornerCutDepth
                        ?: topCornersCutSize
                        ?: layoutDirectionAwareLeftCornersCutSize
                        ?: cornerCutSize

                    val leftTopLength = layoutDirectionAwareCornerCutLeftTopLength
                        ?: layoutDirectionAwareCornerCutLeftTopSize
                        ?: topCornersCutLength
                        ?: layoutDirectionAwareLeftCornersCutLength
                        ?: cornerCutLength
                        ?: topCornersCutSize
                        ?: layoutDirectionAwareLeftCornersCutSize
                        ?: cornerCutSize

                    val rightTopDepth = layoutDirectionAwareCornerCutRightTopDepth
                        ?: layoutDirectionAwareCornerCutRightTopSize
                        ?: topCornersCutDepth
                        ?: layoutDirectionAwareRightCornersCutDepth
                        ?: cornerCutDepth
                        ?: topCornersCutSize
                        ?: layoutDirectionAwareRightCornersCutSize
                        ?: cornerCutSize

                    val rightTopLength = layoutDirectionAwareCornerCutRightTopLength
                        ?: layoutDirectionAwareCornerCutRightTopSize
                        ?: topCornersCutLength
                        ?: layoutDirectionAwareRightCornersCutLength
                        ?: cornerCutLength
                        ?: topCornersCutSize
                        ?: layoutDirectionAwareRightCornersCutSize
                        ?: cornerCutSize

                    val leftBottomDepth = layoutDirectionAwareCornerCutLeftBottomDepth
                        ?: layoutDirectionAwareCornerCutLeftBottomSize
                        ?: bottomCornersCutDepth
                        ?: layoutDirectionAwareLeftCornersCutDepth
                        ?: cornerCutDepth
                        ?: bottomCornersCutSize
                        ?: layoutDirectionAwareLeftCornersCutSize
                        ?: cornerCutSize

                    val leftBottomLength = layoutDirectionAwareCornerCutLeftBottomLength
                        ?: layoutDirectionAwareCornerCutLeftBottomSize
                        ?: bottomCornersCutLength
                        ?: layoutDirectionAwareLeftCornersCutLength
                        ?: cornerCutLength
                        ?: bottomCornersCutSize
                        ?: layoutDirectionAwareLeftCornersCutSize
                        ?: cornerCutSize

                    val rightBottomDepth = layoutDirectionAwareCornerCutRightBottomDepth
                        ?: layoutDirectionAwareCornerCutRightBottomSize
                        ?: bottomCornersCutDepth
                        ?: layoutDirectionAwareRightCornersCutDepth
                        ?: cornerCutDepth
                        ?: bottomCornersCutSize
                        ?: layoutDirectionAwareRightCornersCutSize
                        ?: cornerCutSize

                    val rightBottomLength = layoutDirectionAwareCornerCutRightBottomLength
                        ?: layoutDirectionAwareCornerCutRightBottomSize
                        ?: bottomCornersCutLength
                        ?: layoutDirectionAwareRightCornersCutLength
                        ?: cornerCutLength
                        ?: bottomCornersCutSize
                        ?: layoutDirectionAwareRightCornersCutSize
                        ?: cornerCutSize

                    setCornerCutDimensions(
                        leftTopDepth,
                        leftTopLength,
                        rightTopDepth,
                        rightTopLength,
                        rightBottomDepth,
                        rightBottomLength,
                        leftBottomDepth,
                        leftBottomLength
                    )
                    //endregion
                }
            } finally {
                typedArray.recycle()
            }
            //endregion
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    //region Convenience Global (View) Corner Cut Related Functions
    //region Convenience View Corner Cut Type Functions
    /**
     * Convenience function for setting [CornerCutType] to each corner of this [CornerCutLinearLayout].
     * @see [CornerCutType]
     * Also see other convenience functions.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_type]
     */
    fun setCornerCutType(type: CornerCutType) {
        leftTopCornerCutType = type
        rightTopCornerCutType = type
        rightBottomCornerCutType = type
        leftBottomCornerCutType = type
    }

    /**
     * Convenience function for setting [CornerCutType] to start side of this [CornerCutLinearLayout].
     * Depends on this view layout direction.
     * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
     * @see [CornerCutType]
     * Also see other convenience functions.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_start_corners_cut_type]
     */
    fun setStartCornersCutType(type: CornerCutType) {
        if (isLtr) {
            leftTopCornerCutType = type
            leftBottomCornerCutType = type
        } else {
            rightTopCornerCutType = type
            rightBottomCornerCutType = type
        }
    }

    /**
     * Convenience function for setting [CornerCutType] to left side of this [CornerCutLinearLayout].
     * Does not depend on this view layout direction.
     * @see [CornerCutType]
     * @see [setStartCornersCutType]
     * Also see other convenience functions.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_left_corners_cut_type]
     */
    fun setLeftCornersCutType(type: CornerCutType) {
        leftTopCornerCutType = type
        leftBottomCornerCutType = type
    }

    /**
     * Convenience function for setting [CornerCutType] to end side of this [CornerCutLinearLayout].
     * Depends on this view layout direction.
     * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
     * @see [CornerCutType]
     * Also see other convenience functions.
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_end_corners_cut_type]
     */
    fun setEndCornersCutType(type: CornerCutType) {
        if (isLtr) {
            rightTopCornerCutType = type
            rightBottomCornerCutType = type
        } else {
            leftTopCornerCutType = type
            leftBottomCornerCutType = type
        }
    }

    /**
     * Convenience function for setting [CornerCutType] to left side of this [CornerCutLinearLayout].
     * Does not depend on this view layout direction.
     * @see [CornerCutType]
     * @see [setEndCornersCutType]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_right_corners_cut_type]
     */
    fun setRightCornersCutType(type: CornerCutType) {
        rightTopCornerCutType = type
        rightBottomCornerCutType = type
    }

    /**
     * Convenience function for setting [CornerCutType] to top side of this [CornerCutLinearLayout].
     * @see [CornerCutType]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_top_corners_cut_type]
     */
    fun setTopCornersCutType(type: CornerCutType) {
        leftTopCornerCutType = type
        rightTopCornerCutType = type
    }

    /**
     * Convenience function for setting [CornerCutType] to bottom side of this [CornerCutLinearLayout].
     * @see [CornerCutType]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_bottom_corners_cut_type]
     */
    fun setBottomCornersCutType(type: CornerCutType) {
        leftBottomCornerCutType = type
        rightBottomCornerCutType = type
    }

    /**
     * Convenience function for optionally setting [CornerCutType] to each corner of this [CornerCutLinearLayout].
     * Passing null will skip respective parameters, leaving previously defined values.
     * @see [CornerCutType]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_left_top_corner_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_right_top_corner_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_right_bottom_corner_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_left_bottom_corner_cut_type]
     */
    fun setCornersCutType(
        leftTop: CornerCutType? = null,
        rightTop: CornerCutType? = null,
        rightBottom: CornerCutType? = null,
        leftBottom: CornerCutType? = null
    ) {
        leftTop?.apply { leftTopCornerCutType = this }
        rightTop?.apply { rightTopCornerCutType = this }
        rightBottom?.apply { rightBottomCornerCutType = this }
        leftBottom?.apply { leftBottomCornerCutType = this }
    }

    /**
     * Convenience function for optionally setting [CornerCutType] to each corner of this [CornerCutLinearLayout].
     * Passing null will skip respective parameters, leaving previously defined values.
     * @see [CornerCutType]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_start_top_corner_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_end_top_corner_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_end_bottom_corner_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_start_bottom_corner_cut_type]
     */
    fun setRelativeCornersCutType(
        startTop: CornerCutType? = null,
        endTop: CornerCutType? = null,
        endBottom: CornerCutType? = null,
        startBottom: CornerCutType? = null
    ) {
        startTop?.apply { if (isLtr) leftTopCornerCutType = this else rightTopCornerCutType = this }
        endTop?.apply { if (isLtr) rightTopCornerCutType = this else leftTopCornerCutType = this }
        endBottom?.apply {
            if (isLtr) rightBottomCornerCutType = this else leftBottomCornerCutType = this
        }
        startBottom?.apply {
            if (isLtr) leftBottomCornerCutType = this else rightBottomCornerCutType = this
        }
    }
    //endregion

    //region Convenience View Corner Cut Dimensions (Depth, Length, Size)
    /**
     * Convenience function for setting @param [depth] to each view corner.
     * Does not depend on this view layout direction.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_depth]
     */
    fun setCornerCutDepth(depth: Float) {
        setCornerCutDimensions(
            leftTopDepth = depth,
            rightTopDepth = depth,
            rightBottomDepth = depth,
            leftBottomDepth = depth
        )
    }

    /**
     * Convenience function for optionally setting specific depth to each view corner.
     * Passing null argument will skip respective parameter, preserving previous value.
     * Does not depend on this view layout direction.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_top_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_top_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_bottom_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_bottom_depth]
     */
    fun setCornerCutDepth(
        leftTop: Float? = null,
        rightTop: Float? = null,
        rightBottom: Float? = null,
        leftBottom: Float? = null
    ) {
        setCornerCutDimensions(
            leftTopDepth = leftTop,
            rightTopDepth = rightTop,
            rightBottomDepth = rightBottom,
            leftBottomDepth = leftBottom
        )
    }

    /**
     * Convenience function for optionally setting specific depth to each view corner.
     * Passing null argument will skip respective parameter, preserving previous value.
     *
     * Depends on this view layout direction.
     * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_top_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_top_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_bottom_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_bottom_depth]
     */
    fun setRelativeCornerCutDepth(
        startTop: Float? = null,
        endTop: Float? = null,
        endBottom: Float? = null,
        startBottom: Float? = null
    ) {
        setRelativeCornerCutDimensions(
            startTopDepth = startTop,
            endTopDepth = endTop,
            endBottomDepth = endBottom,
            startBottomDepth = startBottom
        )
    }

    /**
     * Convenience function for setting @param [length] to each view corner.
     * Does not depend on this view layout direction.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_length]
     */
    fun setCornerCutLength(length: Float) {
        setCornerCutDimensions(
            leftTopLength = length,
            rightTopLength = length,
            rightBottomLength = length,
            leftBottomLength = length
        )
    }

    /**
     * Convenience function for optionally setting specific length to each view corner.
     * Passing null argument will skip respective parameter, preserving previous value.
     * Does not depend on this view layout direction.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_top_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_top_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_bottom_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_bottom_length]
     */
    fun setCornerCutLength(
        leftTop: Float? = null,
        rightTop: Float? = null,
        rightBottom: Float? = null,
        leftBottom: Float? = null
    ) {
        setCornerCutDimensions(
            leftTopLength = leftTop,
            rightTopLength = rightTop,
            rightBottomLength = rightBottom,
            leftBottomLength = leftBottom
        )
    }

    /**
     * Convenience function for optionally setting specific length to each view corner.
     * Passing null argument will skip respective parameter, preserving previous value.
     *
     * Depends on this view layout direction.
     * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_top_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_top_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_bottom_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_bottom_length]
     */
    fun setRelativeCornerCutLength(
        startTop: Float? = null,
        endTop: Float? = null,
        endBottom: Float? = null,
        startBottom: Float? = null
    ) {
        setRelativeCornerCutDimensions(
            startTopLength = startTop,
            endTopLength = endTop,
            endBottomLength = endBottom,
            startBottomLength = startBottom
        )
    }

    /**
     * Convenience function for setting @param [depth] to each view start side corner.
     *
     * Depends on this view layout direction.
     * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_start_corners_cut_depth]
     */
    fun setStartCornersCutDepth(depth: Float) {
        setRelativeCornerCutDimensions(
            startTopDepth = depth,
            startBottomDepth = depth
        )
    }

    /**
     * Convenience function for setting @param [depth] to each view end side corner.
     *
     * Depends on this view layout direction.
     * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_end_corners_cut_depth]
     */
    fun setEndCornersCutDepth(depth: Float) {
        setRelativeCornerCutDimensions(
            endTopDepth = depth,
            endBottomDepth = depth
        )
    }

    /**
     * Convenience function for setting @param [depth] to each view left side corner.
     * Does not depend on this view layout direction.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_left_corners_cut_depth]
     */
    fun setLeftCornersCutDepth(depth: Float) {
        setCornerCutDimensions(
            leftTopDepth = depth,
            leftBottomDepth = depth
        )
    }

    /**
     * Convenience function for setting @param [depth] to each view right side corner.
     * Does not depend on this view layout direction.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_right_corners_cut_depth]
     */
    fun setRightCornersCutDepth(depth: Float) {
        setCornerCutDimensions(
            rightTopDepth = depth,
            rightBottomDepth = depth
        )
    }

    /**
     * Convenience function for setting @param [length] to each view start side corner.
     *
     * Depends on this view layout direction.
     * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_start_corners_cut_length]
     */
    fun setStartCornersCutLength(length: Float) {
        setRelativeCornerCutDimensions(
            startTopLength = length,
            startBottomLength = length
        )
    }

    /**
     * Convenience function for setting @param [length] to each view end side corner.
     *
     * Depends on this view layout direction.
     * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_end_corners_cut_length]
     */
    fun setEndCornersCutLength(length: Float) {
        setRelativeCornerCutDimensions(
            endTopLength = length,
            endBottomLength = length
        )
    }

    /**
     * Convenience function for setting @param [length] to each view left side corner.
     * Does not depend on this view layout direction.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_left_corners_cut_length]
     */
    fun setLeftCornersCutLength(length: Float) {
        setCornerCutDimensions(
            leftTopLength = length,
            leftBottomLength = length
        )
    }

    /**
     * Convenience function for setting @param [length] to each view right side corner.
     * Does not depend on this view layout direction.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_right_corners_cut_length]
     */
    fun setRightCornersCutLength(length: Float) {
        setCornerCutDimensions(
            rightTopLength = length,
            rightBottomLength = length
        )
    }

    /**
     * Convenience function for setting @param [depth] to each view top side corner.
     * Does not depend on this view layout direction.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_top_corners_cut_depth]
     */
    fun setTopCornersCutDepth(depth: Float) {
        setCornerCutDimensions(
            leftTopDepth = depth,
            rightTopDepth = depth
        )
    }

    /**
     * Convenience function for setting @param [depth] to each view bottom side corner.
     * Does not depend on this view layout direction.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_bottom_corners_cut_depth]
     */
    fun setBottomCornersCutDepth(depth: Float) {
        setCornerCutDimensions(
            leftBottomDepth = depth,
            rightBottomDepth = depth
        )
    }

    /**
     * Convenience function for setting @param [length] to each view top side corner.
     * Does not depend on this view layout direction.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_top_corners_cut_length]
     */
    fun setTopCornersCutLength(length: Float) {
        setCornerCutDimensions(
            leftTopLength = length,
            rightTopLength = length
        )
    }

    /**
     * Convenience function for setting @param [length] to each view bottom side corner.
     * Does not depend on this view layout direction.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_bottom_corners_cut_length]
     */
    fun setBottomCornersCutLength(length: Float) {
        setCornerCutDimensions(
            leftBottomLength = length,
            rightBottomLength = length
        )
    }

    /**
     * Convenience function for setting @param [size] (depth & length) to each view start side corner.
     *
     * Depends on this view layout direction.
     * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_start_corners_cut_size]
     */
    fun setStartCornersCutSize(size: Float) {
        setRelativeCornerCutDimensions(
            startTopDepth = size,
            startTopLength = size,
            startBottomDepth = size,
            startBottomLength = size
        )
    }

    /**
     * Convenience function for setting @param [size] (depth & length) to each view end side corner.
     *
     * Depends on this view layout direction.
     * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_end_corners_cut_size]
     */
    fun setEndCornersCutSize(size: Float) {
        setRelativeCornerCutDimensions(
            endTopDepth = size,
            endTopLength = size,
            endBottomDepth = size,
            endBottomLength = size
        )
    }

    /**
     * Convenience function for setting @param [size] (depth & length) to each view left side corner.
     * Does not depend on this view layout direction.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_left_corners_cut_size]
     */
    fun setLeftCornersCutSize(size: Float) {
        setCornerCutDimensions(
            leftTopDepth = size,
            leftTopLength = size,
            leftBottomDepth = size,
            leftBottomLength = size
        )
    }

    /**
     * Convenience function for setting @param [size] (depth & length) to each view right side corner.
     * Does not depend on this view layout direction.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_right_corners_cut_size]
     */
    fun setRightCornersCutSize(size: Float) {
        setCornerCutDimensions(
            rightTopDepth = size,
            rightTopLength = size,
            rightBottomDepth = size,
            rightBottomLength = size
        )
    }

    /**
     * Convenience function for setting @param [size] (depth & length) to each view top side corner.
     * Does not depend on this view layout direction.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_top_corners_cut_size]
     */
    fun setTopCornersCutSize(size: Float) {
        setCornerCutDimensions(
            leftTopDepth = size,
            leftTopLength = size,
            rightTopDepth = size,
            rightTopLength = size
        )
    }

    /**
     * Convenience function for setting @param [size] (depth & length) to each view bottom side corner.
     * Does not depend on this view layout direction.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_bottom_corners_cut_size]
     */
    fun setBottomCornersCutSize(size: Float) {
        setCornerCutDimensions(
            leftBottomDepth = size,
            leftBottomLength = size,
            rightBottomDepth = size,
            rightBottomLength = size
        )
    }

    /**
     * Convenience function for @param [size] (depth and length) to each view corner.
     * Does not depend on this view layout direction.
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_size]
     */
    fun setCornerCutSize(size: Float) {
        setCornerCutDimensions(FloatArray(8) { size })
    }

    /**
     * Convenience function for optionally setting specific size to each view corner.
     * Passing null argument will skip respective parameter, preserving previous value.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_top_size]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_top_size]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_bottom_size]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_bottom_size]
     */
    fun setCornerCutSize(
        leftTop: Float? = null,
        rightTop: Float? = null,
        rightBottom: Float? = null,
        leftBottom: Float? = null
    ) {
        setCornerCutDimensions(
            leftTopDepth = leftTop,
            leftTopLength = leftTop,
            rightTopDepth = rightTop,
            rightTopLength = rightTop,
            rightBottomDepth = rightBottom,
            rightBottomLength = rightBottom,
            leftBottomDepth = leftBottom,
            leftBottomLength = leftBottom
        )
    }

    /**
     * Convenience function for optionally setting specific size to each view corner.
     * Passing null argument will skip respective parameter, preserving previous value.
     *
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_top_size]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_top_size]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_bottom_size]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_bottom_size]
     */
    fun setRelativeCornerCutSize(
        startTop: Float? = null,
        endTop: Float? = null,
        endBottom: Float? = null,
        startBottom: Float? = null
    ) {
        setRelativeCornerCutDimensions(
            startTopDepth = startTop,
            startTopLength = startTop,
            endTopDepth = endTop,
            endTopLength = endTop,
            endBottomDepth = endBottom,
            endBottomLength = endBottom,
            startBottomDepth = startBottom,
            startBottomLength = startBottom
        )
    }

    /**
     * Convenience function for setting depths and length pairs for each
     * view corner starting clockwise from left top corner.
     * Does not depend on this view layout direction.
     * @see [cornerCutDimensions]
     */
    fun setCornerCutDimensions(@Size(8) dimensions: FloatArray) {
        require(dimensions.size == 8) { "must be exact 8 length size" }
        setCornerCutDimensions(
            dimensions[0], dimensions[1],
            dimensions[2], dimensions[3],
            dimensions[4], dimensions[5],
            dimensions[6], dimensions[7]
        )
    }

    /**
     * Convenience function for optionally setting specific depth or length to each view corner.
     * Passing null argument will skip respective parameter, preserving previous value.
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_top_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_top_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_top_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_top_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_bottom_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_right_bottom_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_bottom_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_left_bottom_length]
     */
    fun setCornerCutDimensions(
        leftTopDepth: Float? = null,
        leftTopLength: Float? = null,
        rightTopDepth: Float? = null,
        rightTopLength: Float? = null,
        rightBottomDepth: Float? = null,
        rightBottomLength: Float? = null,
        leftBottomDepth: Float? = null,
        leftBottomLength: Float? = null
    ) {
        leftTopDepth?.apply { cornerCutDimensions[0] = this.coerceAtLeast(0.0F) }
        leftTopLength?.apply { cornerCutDimensions[1] = this.coerceAtLeast(0.0F) }
        rightTopDepth?.apply { cornerCutDimensions[2] = this.coerceAtLeast(0.0F) }
        rightTopLength?.apply { cornerCutDimensions[3] = this.coerceAtLeast(0.0F) }
        rightBottomDepth?.apply { cornerCutDimensions[4] = this.coerceAtLeast(0.0F) }
        rightBottomLength?.apply { cornerCutDimensions[5] = this.coerceAtLeast(0.0F) }
        leftBottomDepth?.apply { cornerCutDimensions[6] = this.coerceAtLeast(0.0F) }
        leftBottomLength?.apply { cornerCutDimensions[7] = this.coerceAtLeast(0.0F) }
        invalidateCornerCutPath()
    }

    /**
     * Convenience function for optionally setting specific depth or length to each view corner.
     * Passing null argument will skip respective parameter, preserving previous value.
     * @see [cornerCutDimensions]
     *
     * Attributes:
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_top_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_top_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_top_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_top_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_bottom_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_end_bottom_length]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_bottom_depth]
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_start_bottom_length]
     */
    fun setRelativeCornerCutDimensions(
        startTopDepth: Float? = null,
        startTopLength: Float? = null,
        endTopDepth: Float? = null,
        endTopLength: Float? = null,
        endBottomDepth: Float? = null,
        endBottomLength: Float? = null,
        startBottomDepth: Float? = null,
        startBottomLength: Float? = null
    ) {
        if (isLtr) {
            setCornerCutDimensions(
                leftTopDepth = startTopDepth,
                leftTopLength = startTopLength,
                rightTopDepth = endTopDepth,
                rightTopLength = endTopLength,
                rightBottomDepth = endBottomDepth,
                rightBottomLength = endBottomLength,
                leftBottomDepth = startBottomDepth,
                leftBottomLength = startBottomLength
            )
        } else {
            setCornerCutDimensions(
                leftTopDepth = endTopDepth,
                leftTopLength = endTopLength,
                rightTopDepth = startTopDepth,
                rightTopLength = startTopLength,
                rightBottomDepth = startBottomDepth,
                rightBottomLength = startBottomLength,
                leftBottomDepth = endBottomDepth,
                leftBottomLength = endBottomLength
            )
        }
    }
    //endregion

    /**
     * Convenience function for setting @param [radius] for [CornerCutType.RECTANGLE] and [CornerCutType.RECTANGLE_INVERSE] corner cut types.
     * @see [rectangleTypeCornerCutRoundCornerRadiusDepth]
     * @see [rectangleTypeCornerCutRoundCornerRadiusLength]
     * @see [CornerCutType.RECTANGLE]
     * @see [CornerCutType.RECTANGLE_INVERSE]
     */
    fun setRectangleTypeCornerCutRoundCornerRadius(radius: Float) {
        rectangleTypeCornerCutRoundCornerRadiusDepth = radius
        rectangleTypeCornerCutRoundCornerRadiusLength = radius
    }
    //endregion

    //region Convenience Child Corner Cut Related Functions
    /**
     * Convenience function for setting [CornerCutType] to each child corner of this [CornerCutLinearLayout].
     * @see [CornerCutType]
     */
    fun setChildCornerCutType(type: CornerCutType) {
        childStartSideCornerCutType = type
        childEndSideCornerCutType = type
    }

    /**
     * Convenience function for setting @param [size] (both depth and length) to each child start side of this [CornerCutLinearLayout].
     * Depends on this view layout direction.
     * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
     * @see [CornerCutType]
     * @see [ChildSideCutFlag]
     */
    fun setChildStartSideCornerCutSize(size: Float) {
        childStartSideCornerCutDepth = size
        childStartSideCornerCutLength = size
    }

    /**
     * Convenience function for setting @param [size] (both depth and length) to each child end side of this [CornerCutLinearLayout].
     * Depends on this view layout direction.
     * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
     * @see [CornerCutType]
     * @see [ChildSideCutFlag]
     */
    fun setChildEndSideCornerCutSize(size: Float) {
        childEndSideCornerCutDepth = size
        childEndSideCornerCutLength = size
    }

    /**
     * Convenience function for setting @param [size] (both depth and length) to every side of each child of this [CornerCutLinearLayout].
     * @see [CornerCutType]
     * @see [ChildSideCutFlag]
     * Also see other convenience functions.
     */
    fun setChildCornerCutSize(size: Float) {
        setChildStartSideCornerCutSize(size)
        setChildEndSideCornerCutSize(size)
    }

    /**
     * Convenience function for setting @param [radius] for [CornerCutType.RECTANGLE] and [CornerCutType.RECTANGLE_INVERSE] child corner cut types.
     * @see [childRectangleTypeCornerCutRoundCornerRadiusDepth]
     * @see [childRectangleTypeCornerCutRoundCornerRadiusLength]
     * @see [CornerCutType.RECTANGLE]
     * @see [CornerCutType.RECTANGLE_INVERSE]
     */
    fun setChildRectangleTypeCornerCutRoundCornerRadius(radius: Float) {
        childRectangleTypeCornerCutRoundCornerRadiusDepth = radius
        childRectangleTypeCornerCutRoundCornerRadiusLength = radius
    }

    /**
     * Convenience function for setting rotation @param [degree] to child cutouts.
     * @see [childStartSideCornerCutRotationDegree]
     * @see [childEndSideCornerCutRotationDegree]
     */
    fun setChildCornerCutRotationDegree(degree: Float) {
        childStartSideCornerCutRotationDegree = degree
        childEndSideCornerCutRotationDegree = degree
    }
    //endregion

    //region Custom Divider Functions
    /**
     * Function to set custom divider dash and gap.
     * Both params must be > 0, otherwise dash/gap data will be cleared.
     * Passing null either to @param [dashWidth] or [dashGap] will clear dash/gap line as well,
     * resulting in simple line.
     */
    @Suppress("NAME_SHADOWING")
    fun setCustomDividerDash(dashWidth: Float? = null, dashGap: Float? = null) {
        val dashWidth = dashWidth?.takeIf { it > 0.0F }
        val dashGap = dashGap?.takeIf { it > 0.0F }
        if (dashWidth == null || dashGap == null) {
            customDividerPaint.pathEffect = null
        } else {
            customDividerPaint.pathEffect = DashPathEffect(
                arrayOf(dashWidth, dashGap).toFloatArray(),
                0.0F
            )
        }
        customDividerDashWidth = dashWidth ?: 0.0F
        customDividerDashGap = dashGap ?: 0.0F
        invalidateCustomDividerIfNeeded()
    }

    /**
     * Convenience function for setting padding value
     * to both [customDividerPaddingStart] & [customDividerPaddingEnd] properties.
     * @see [customDividerPaddingStart]
     * @see [customDividerPaddingEnd]
     */
    fun setCustomDividerPadding(padding: Float) {
        customDividerPaddingStart = padding
        customDividerPaddingEnd = padding
    }

    /**
     * @see [CustomDividerProvider]
     */
    inline fun setCustomDividerProvider(
        crossinline getDivider: (view: CornerCutLinearLayout, dividerPath: Path, dividerPaint: Paint, showDividerFlag: Int, dividerTypeIndex: Int, rectF: RectF) -> Boolean
    ) {
        customDividerProvider = object : CustomDividerProvider {
            override fun getDivider(
                view: CornerCutLinearLayout,
                dividerPath: Path,
                dividerPaint: Paint,
                showDividerFlag: Int,
                dividerTypeIndex: Int,
                rectF: RectF
            ): Boolean {
                return getDivider(
                    view,
                    dividerPath,
                    dividerPaint,
                    showDividerFlag,
                    dividerTypeIndex,
                    rectF
                )
            }
        }
    }

    private fun invalidateCustomDividerIfNeeded() {
        if (customDividerHeight > 0.0F) invalidate()
    }
    //endregion

    //region Cutout Providers Functions
    /**
     * @see [CornerCutProvider]
     */
    inline fun setCornerCutProvider(
        noinline getPathTransformationMatrix: ((view: CornerCutLinearLayout, cutout: Path, cutEdge: Int, rectF: RectF) -> Matrix?)? = null,
        crossinline getCornerCut: (view: CornerCutLinearLayout, cutout: Path, cutEdge: Int, rectF: RectF) -> Boolean = { _, _, _, _ -> false }
    ) {
        cornerCutProvider = object : CornerCutProvider {
            override fun getCornerCut(
                view: CornerCutLinearLayout,
                cutout: Path,
                cutEdge: Int,
                rectF: RectF
            ): Boolean {
                return getCornerCut(view, cutout, cutEdge, rectF)
            }

            override fun getPathTransformationMatrix(
                view: CornerCutLinearLayout,
                cutout: Path,
                cutEdge: Int,
                rectF: RectF
            ): Matrix? {
                return getPathTransformationMatrix?.invoke(
                    this@CornerCutLinearLayout,
                    cutout,
                    cutEdge,
                    rectF
                ) ?: super.getPathTransformationMatrix(
                    this@CornerCutLinearLayout,
                    cutout,
                    cutEdge,
                    rectF
                )
            }
        }
    }

    /**
     * @see [ChildCornerCutProvider]
     */
    inline fun setChildCornerCutProvider(
        noinline getPathTransformationMatrix: ((view: CornerCutLinearLayout, cutout: Path, cutSide: Int, rectF: RectF, relativeCutTopChild: View?, relativeCutBottomChild: View?) -> Matrix?)? = null,
        crossinline getCornerCut: (view: CornerCutLinearLayout, cutout: Path, cutSide: Int, rectF: RectF, relativeCutTopChild: View?, relativeCutBottomChild: View?) -> Boolean = { _, _, _, _, _, _ -> false }
    ) {
        childCornerCutProvider = object : ChildCornerCutProvider {
            override fun getCornerCut(
                view: CornerCutLinearLayout,
                cutout: Path,
                cutSide: Int,
                rectF: RectF,
                relativeCutTopChild: View?,
                relativeCutBottomChild: View?
            ): Boolean {
                return getCornerCut(
                    view,
                    cutout,
                    cutSide,
                    rectF,
                    relativeCutTopChild,
                    relativeCutBottomChild
                )
            }

            override fun getPathTransformationMatrix(
                view: CornerCutLinearLayout,
                cutout: Path,
                cutSide: Int,
                rectF: RectF,
                relativeCutTopChild: View?,
                relativeCutBottomChild: View?
            ): Matrix? {
                return getPathTransformationMatrix?.invoke(
                    view,
                    cutout,
                    cutSide,
                    rectF,
                    relativeCutTopChild,
                    relativeCutBottomChild
                )
                    ?: super.getPathTransformationMatrix(
                        view,
                        cutout,
                        cutSide,
                        rectF,
                        relativeCutTopChild,
                        relativeCutBottomChild
                    )
            }
        }
    }

    /**
     * Add specified provider if not found in collection.
     * @param provider - [CustomCutoutProvider] to add to collection
     * @see [CustomCutoutProvider]
     */
    fun addCustomCutoutProvider(provider: CustomCutoutProvider) {
        if (customCutoutProviders.add(provider)) invalidateCornerCutPath()
    }

    /**
     * Add specified provider if not found in collection.
     * @param provider - [CustomCutoutProvider] to add to collection
     * @see [CustomCutoutProvider]
     */
    fun addCustomCutoutProvider(
        getPathTransformationMatrix: ((view: CornerCutLinearLayout, cutout: Path, rectF: RectF) -> Matrix?)? = null,
        getCutout: (view: CornerCutLinearLayout, cutout: Path, rectF: RectF) -> Unit
    ): CustomCutoutProvider {
        val provider = object : CustomCutoutProvider {
            override fun getCutout(view: CornerCutLinearLayout, cutout: Path, rectF: RectF) {
                getCutout(view, cutout, rectF)
            }

            override fun getPathTransformationMatrix(
                view: CornerCutLinearLayout,
                cutout: Path,
                rectF: RectF
            ): Matrix? {
                return getPathTransformationMatrix?.invoke(view, cutout, rectF)
                    ?: super.getPathTransformationMatrix(view, cutout, rectF)
            }
        }
        customCutoutProviders.add(provider)
        return provider
    }

    /**
     * Remove specified provider if found in collection.
     * @param provider - [CustomCutoutProvider] to remove from collection
     * @see [CustomCutoutProvider]
     */
    fun removeCustomCutoutProvider(provider: CustomCutoutProvider) {
        if (customCutoutProviders.remove(provider)) invalidateCornerCutPath()
    }

    /**
     * Remove all custom cutout providers if any.
     * @see [CustomCutoutProvider]
     */
    fun removeAllCustomCutoutProviders() {
        if (customCutoutProviders.isNotEmpty()) {
            customCutoutProviders.clear()
            invalidateCornerCutPath()
        }
    }
    //endregion

    /**
     * Function to resolve and invalidate padding
     * @see [userDefinedPadding]
     * @see [composedPadding]
     * @see [isCustomShadowAutoPaddingEnabled]
     * @see [couldDrawCustomShadowOverUserDefinedPadding]
     * See also custom shadow properties
     */
    private fun invalidateComposedPadding() {
        val isShadowAutoPaddingEnabled =
            isCustomShadowAutoPaddingEnabled && customShadowRadius > 0.0F
        if (isShadowAutoPaddingEnabled) {
            if (couldDrawCustomShadowOverUserDefinedPadding) {
                composedPadding[0] =
                    max(
                        userDefinedPadding[0],
                        (customShadowRadius - customShadowOffsetDx).roundToInt()
                    )
                        .coerceAtLeast(userDefinedPadding[0])
                composedPadding[1] =
                    max(
                        userDefinedPadding[1],
                        (customShadowRadius - customShadowOffsetDy).roundToInt()
                    )
                        .coerceAtLeast(userDefinedPadding[1])
                composedPadding[2] =
                    max(
                        userDefinedPadding[2],
                        (customShadowRadius + customShadowOffsetDx).roundToInt()
                    )
                        .coerceAtLeast(userDefinedPadding[2])
                composedPadding[3] =
                    max(
                        userDefinedPadding[3],
                        (customShadowRadius + customShadowOffsetDy).roundToInt()
                    )
                        .coerceAtLeast(userDefinedPadding[3])
            } else {
                composedPadding[0] =
                    (userDefinedPadding[0] + customShadowRadius - customShadowOffsetDx).roundToInt()
                        .coerceAtLeast(userDefinedPadding[0])
                composedPadding[1] =
                    (userDefinedPadding[1] + customShadowRadius - customShadowOffsetDy).roundToInt()
                        .coerceAtLeast(userDefinedPadding[1])
                composedPadding[2] =
                    (userDefinedPadding[2] + customShadowRadius + customShadowOffsetDx).roundToInt()
                        .coerceAtLeast(userDefinedPadding[2])
                composedPadding[3] =
                    (userDefinedPadding[3] + customShadowRadius + customShadowOffsetDy).roundToInt()
                        .coerceAtLeast(userDefinedPadding[3])
            }
        } else {
            composedPadding[0] = userDefinedPadding[0]
            composedPadding[1] = userDefinedPadding[1]
            composedPadding[2] = userDefinedPadding[2]
            composedPadding[3] = userDefinedPadding[3]
        }

        if (width != 0 && height != 0) {
            super.setPadding(
                composedPadding[0],
                composedPadding[1],
                composedPadding[2],
                composedPadding[3]
            )

            paddedBoundsF.set(
                paddingLeft.toFloat(),
                paddingTop.toFloat(),
                width.toFloat() - paddingRight,
                height.toFloat() - paddingBottom
            )

            if (paddedBoundsF.left > paddedBoundsF.right) paddedBoundsF.left =
                paddedBoundsF.right
            if (paddedBoundsF.top > paddedBoundsF.bottom) paddedBoundsF.top =
                paddedBoundsF.bottom

            invalidateCornerCutPath()
        }
    }

//    var shouldBuildCustomShadowUponPaddedBounds: Boolean by Delegate(true)
//    var shouldBuildCustomShadowUponOnlyPaddedChildren: Boolean by Delegate(true)
//    val customShadowChildTempPath = Path()

    /**
     * Function to rebuild [composedCornerCutPath] for whole view and force redraw
     */
    fun invalidateCornerCutPath() {
        if (!isAttachedToWindow) return

        childContactCutCenters.clear()
        customDividerPoints.clear()
        customDividerTypes.clear()
        customDividerTypedIndexes.clear()
        childStartSideCutTypes.clear()
        childEndSideCutTypes.clear()
        val isLtr = isLtr

        var floatContactCutCenter: Float
        val lastChildIndex = childCount - 1

        val paddedViewWidth = paddedBoundsF.width()
        val paddedViewHeight = paddedBoundsF.height()

        val firstChild = getOrNull<View>(0)
        val lastChild = getOrNull<View>(childCount - 1)
        val firstChildLayoutParams = firstChild?.layoutParams as? LayoutParams
        val lastChildLayoutParams = lastChild?.layoutParams as? LayoutParams

        val leftTopCornerCutType: CornerCutType
        val rightTopCornerCutType: CornerCutType
        val rightBottomCornerCutType: CornerCutType
        val leftBottomCornerCutType: CornerCutType

        val halfDividerDrawableHeight: Float
        val halfBeginningDividerDrawableHeight: Float
        val halfMiddleDividerDrawableHeight: Float
        val halfEndDividerDrawableHeight: Float

        val maxAllowedDepth: Float
        val maxAllowedLength: Float
        val leftTopX: Float
        val leftTopY: Float
        val rightTopX: Float
        val rightTopY: Float
        val rightBottomX: Float
        val rightBottomY: Float
        val leftBottomX: Float
        val leftBottomY: Float

        val paddedViewLeft = paddedBoundsF.left
        val paddedViewRight = paddedBoundsF.right
        val paddedViewStart = if (isLtr) paddedViewLeft else paddedViewRight
        val paddedViewTop = paddedBoundsF.top
        val paddedViewEnd = if (isLtr) paddedViewRight else paddedViewLeft
        val paddedViewBottom = paddedBoundsF.bottom

        val childStartSideDepthOffset: Float
        val childEndSideDepthOffset: Float
        val childStartSideLengthOffset: Float
        val childEndSideLengthOffset: Float

        val showMiddleCustomDivider =
            customDividerShowFlag containsFlag CustomDividerShowFlag.MIDDLE

        val rectangleTypeCornerCutRoundCornerRadiusX: Float
        val rectangleTypeCornerCutRoundCornerRadiusY: Float

        //begin from left top
        composedCornerCutPath.rewind()
        composedCornerCutPath.addRect(paddedBoundsF, Path.Direction.CW)

        childCornerCutPath.rewind()
        viewCornerCutPath.rewind()
        customCutoutsPath.rewind()

        if (orientation == VERTICAL) {
            halfDividerDrawableHeight = (dividerDrawable?.intrinsicHeight?.toFloat() ?: 0.0F) / 2.0F
            halfBeginningDividerDrawableHeight = halfDividerDrawableHeight
                .takeIf { showDividers containsFlag SHOW_DIVIDER_BEGINNING } ?: 0.0F
            halfMiddleDividerDrawableHeight = halfDividerDrawableHeight
                .takeIf { showDividers containsFlag SHOW_DIVIDER_MIDDLE } ?: 0.0F
            halfEndDividerDrawableHeight = halfDividerDrawableHeight
                .takeIf { showDividers containsFlag SHOW_DIVIDER_END } ?: 0.0F

            val firstChildTop =
                firstChild?.let { it.top - it.marginTop - halfBeginningDividerDrawableHeight }
            val isTopAligned = paddedViewTop == firstChildTop
            val lastChildBottom =
                lastChild?.let { it.bottom + it.marginBottom + halfEndDividerDrawableHeight }
            val isBottomAligned = paddedViewBottom == lastChildBottom

            leftTopCornerCutType = firstChildLayoutParams?.leftTopCornerCutType
                ?.takeIf { firstChildLayoutParams.couldEdgeChildOverrideParentCornerCutTypeIfEdgeAligned && isTopAligned }
                ?: this.leftTopCornerCutType
            rightTopCornerCutType = firstChildLayoutParams?.rightTopCornerCutType
                ?.takeIf { firstChildLayoutParams.couldEdgeChildOverrideParentCornerCutTypeIfEdgeAligned && isTopAligned }
                ?: this.rightTopCornerCutType

            rightBottomCornerCutType =
                lastChildLayoutParams?.rightBottomCornerCutType
                    ?.takeIf { lastChildLayoutParams.couldEdgeChildOverrideParentCornerCutTypeIfEdgeAligned && isBottomAligned }
                    ?: this.rightBottomCornerCutType
            leftBottomCornerCutType = lastChildLayoutParams?.leftBottomCornerCutType
                ?.takeIf { lastChildLayoutParams.couldEdgeChildOverrideParentCornerCutTypeIfEdgeAligned && isBottomAligned }
                ?: this.leftBottomCornerCutType

            if (customDividerShowFlag containsFlag CustomDividerShowFlag.CONTAINER_BEGINNING) {
                customDividerPoints.add(paddedBoundsF.top + customDividerHeight/2.0F)
                customDividerTypes.add(CustomDividerShowFlag.CONTAINER_BEGINNING)
                customDividerTypedIndexes.add(0)
            }

            forEachIndexed { index, view ->

//                customShadowChildTempPath.rewind()
//                customShadowChildTempPath.addRect(view.left.toFloat(), view.top.toFloat(), view.right.toFloat(), view.bottom.toFloat(), Path.Direction.CW)
//                customShadowChildTempPath.transform(view.matrix)
//                composedCornerCutPath.addPath(customShadowChildTempPath)


                if (index < lastChildIndex) {
                    if (index == 0 && !isTopAligned) {
                        childContactCutCenters.add(firstChildTop!!)

                        val lp = (view.layoutParams as? LayoutParams)

                        val startTopCutType = lp?.edgeChildParentContactStartTopChildCornerCutType
                        val startBottomCutType = lp?.startTopCornerCutType
                        val endTopCutType = lp?.edgeChildParentContactEndTopChildCornerCutType
                        val endBottomCutType = lp?.endTopCornerCutType

                        childStartSideCutTypes.add(startTopCutType to startBottomCutType)
                        childEndSideCutTypes.add(endTopCutType to endBottomCutType)
                    }

                    floatContactCutCenter =
                        view.bottom + view.marginBottom + halfMiddleDividerDrawableHeight
                    childContactCutCenters.add(floatContactCutCenter)

                    val topLp = (view.layoutParams as? LayoutParams)
                    val bottomLp = (getOrNull<View>(index + 1)?.layoutParams as? LayoutParams)

                    val startTopCutType = topLp?.leftBottomCornerCutType
                    val startBottomCutType = bottomLp?.leftTopCornerCutType
                    val endTopCutType = topLp?.rightBottomCornerCutType
                    val endBottomCutType = bottomLp?.rightTopCornerCutType

                    childStartSideCutTypes.add(startTopCutType to startBottomCutType)
                    childEndSideCutTypes.add(endTopCutType to endBottomCutType)

                    if (index == 0 && customDividerShowFlag containsFlag CustomDividerShowFlag.BEGINNING) {
                        customDividerPoints.add(view.top - view.marginTop - halfBeginningDividerDrawableHeight)
                        customDividerTypes.add(CustomDividerShowFlag.BEGINNING)
                        customDividerTypedIndexes.add(0)
                    }

                    if (showMiddleCustomDivider) {
                        customDividerPoints.add(floatContactCutCenter)
                        customDividerTypes.add(CustomDividerShowFlag.MIDDLE)
                        customDividerTypedIndexes.add(index)
                    }
                } else {
                    if (!isBottomAligned) {
                        childContactCutCenters.add(lastChildBottom!!)

                        val lp = (view.layoutParams as? LayoutParams)

                        val startTopCutType = lp?.startBottomCornerCutType
                        val startBottomCutType =
                            lp?.edgeChildParentContactStartBottomChildCornerCutType
                        val endTopCutType = lp?.endBottomCornerCutType
                        val endBottomCutType = lp?.edgeChildParentContactEndBottomChildCornerCutType

                        childStartSideCutTypes.add(startTopCutType to startBottomCutType)
                        childEndSideCutTypes.add(endTopCutType to endBottomCutType)
                    }

                    if (customDividerShowFlag containsFlag CustomDividerShowFlag.END) {
                        customDividerPoints.add(view.bottom + view.marginBottom + halfEndDividerDrawableHeight)
                        customDividerTypes.add(CustomDividerShowFlag.END)
                        customDividerTypedIndexes.add(0)
                    }
                }
            }

            if (customDividerShowFlag containsFlag CustomDividerShowFlag.CONTAINER_END) {
                customDividerPoints.add(paddedBoundsF.bottom - customDividerHeight/2.0F)
                customDividerTypes.add(CustomDividerShowFlag.CONTAINER_END)
                customDividerTypedIndexes.add(0)
            }

            if (shouldUseMaxAllowedCornerCutSize) {
                if (shouldUseMaxAllowedCornerCutDepthOrLengthToBeEqual) {
                    maxAllowedDepth = min(paddedViewHeight, paddedViewWidth) / 2.0F
                    maxAllowedLength = maxAllowedDepth
                } else {
                    maxAllowedDepth = paddedViewWidth / 2.0F
                    maxAllowedLength = paddedViewHeight / 2.0F
                }

                leftTopX = maxAllowedDepth
                leftTopY = maxAllowedLength
                rightTopX = maxAllowedDepth
                rightTopY = maxAllowedLength
                rightBottomX = maxAllowedDepth
                rightBottomY = maxAllowedLength
                leftBottomX = maxAllowedDepth
                leftBottomY = maxAllowedLength
            } else {
                maxAllowedDepth = paddedViewWidth / 2.0F
                maxAllowedLength = paddedViewHeight / 2.0F

                leftTopX = cornerCutDimensions[0].coerceAtMost(maxAllowedDepth)
                leftTopY = cornerCutDimensions[1].coerceAtMost(maxAllowedLength)
                rightTopX = cornerCutDimensions[2].coerceAtMost(maxAllowedDepth)
                rightTopY = cornerCutDimensions[3].coerceAtMost(maxAllowedLength)
                rightBottomX = cornerCutDimensions[4].coerceAtMost(maxAllowedDepth)
                rightBottomY = cornerCutDimensions[5].coerceAtMost(maxAllowedLength)
                leftBottomX = cornerCutDimensions[6].coerceAtMost(maxAllowedDepth)
                leftBottomY = cornerCutDimensions[7].coerceAtMost(maxAllowedLength)
            }

            rectangleTypeCornerCutRoundCornerRadiusX =
                this.rectangleTypeCornerCutRoundCornerRadiusDepth
            rectangleTypeCornerCutRoundCornerRadiusY =
                this.rectangleTypeCornerCutRoundCornerRadiusLength

            //region Child Cut
            //region Child Cut Start Side
            if (childSideCutFlag containsFlag ChildSideCutFlag.START &&
                childStartSideCornerCutDepth > 0.0F &&
                childStartSideCornerCutLength > 0.0F
            ) {

                childStartSideDepthOffset =
                    if (isLtr) childStartSideCornerCutDepthOffset else -childStartSideCornerCutDepthOffset
                childStartSideLengthOffset = childStartSideCornerCutLengthOffset

                val cutDepth = childStartSideCornerCutDepth.coerceAtMost(maxAllowedDepth * 2)
                val cutLength = childStartSideCornerCutLength.coerceAtMost(maxAllowedLength * 2)
                val halfCutDepth = cutDepth / 2.0F
                val halfCutLength = cutLength / 2.0F

                val depthCenter = paddedViewStart + childStartSideDepthOffset
                childContactCutCenters.forEachIndexed { index, it ->
                    val lengthCenter = it + childStartSideLengthOffset

                    val indexFix = if (isTopAligned) 0 else -1
                    tempCustomCutPath.rewind()
                    tempRectF.set(
                        depthCenter - halfCutDepth,
                        lengthCenter - halfCutLength,
                        depthCenter + halfCutDepth,
                        lengthCenter + halfCutLength
                    )
                    val topContactView = getOrNull<View>(index + indexFix)
                    val bottomContactView = getOrNull<View>(index + indexFix + 1)
                    if (childCornerCutProvider?.getCornerCut(
                            this@CornerCutLinearLayout,
                            tempCustomCutPath,
                            ChildSideCutFlag.START,
                            tempRectF,
                            topContactView,
                            bottomContactView
                        ) == true
                    ) {
                        childCornerCutProvider?.getPathTransformationMatrix(
                            this@CornerCutLinearLayout,
                            tempCustomCutPath,
                            ChildSideCutFlag.START,
                            tempRectF,
                            topContactView,
                            bottomContactView
                        )?.apply {
                            tempCustomCutPath.transform(this)
                        }
                        tempCustomCutPath.close()
                        childCornerCutPath.addPath(tempCustomCutPath)
                    } else {
                        val topPartCornerCutType = childStartSideCutTypes[index].first
                            ?: childStartSideCornerCutType
                        val bottomPartCornerCutType = childStartSideCutTypes[index].second
                            ?: childStartSideCornerCutType

                        //skip cut for specific combinations
                        when {
                            topPartCornerCutType == CornerCutType.RECTANGLE &&
                                    bottomPartCornerCutType == CornerCutType.RECTANGLE
                                    && (childRectangleTypeCornerCutRoundCornerRadiusDepth == 0.0F || childRectangleTypeCornerCutRoundCornerRadiusDepth == 0.0F) -> {
                                return@forEachIndexed
                            }
                        }

                        if (childStartSideCornerCutRotationDegree != 0.0F) {
                            val matrix = Matrix()
                            matrix.postRotate(
                                -childStartSideCornerCutRotationDegree,
                                depthCenter,
                                lengthCenter
                            )
                            childCornerCutPath.transform(matrix)
                        }

                        childCornerCutPath.moveTo(
                            depthCenter - halfCutDepth,
                            lengthCenter
                        )
                        when (topPartCornerCutType) {
                            CornerCutType.OVAL -> {
                                childCornerCutPath.arcTo(
                                    depthCenter - cutDepth,
                                    lengthCenter - cutLength,
                                    depthCenter,
                                    lengthCenter,
                                    90.0F,
                                    -90.0F,
                                    false
                                )
                                childCornerCutPath.arcTo(
                                    depthCenter,
                                    lengthCenter - cutLength,
                                    depthCenter + cutDepth,
                                    lengthCenter,
                                    180.0F,
                                    -90.0F,
                                    false
                                )

                            }
                            CornerCutType.OVAL_INVERSE -> {
                                childCornerCutPath.arcTo(
                                    depthCenter - halfCutDepth,
                                    lengthCenter - halfCutLength,
                                    depthCenter + halfCutDepth,
                                    lengthCenter + halfCutLength,
                                    180.0F,
                                    180.0F,
                                    false
                                )
                            }
                            CornerCutType.BEVEL -> {
                                childCornerCutPath.lineTo(
                                    depthCenter,
                                    lengthCenter - halfCutLength
                                )
                                childCornerCutPath.lineTo(
                                    depthCenter + halfCutDepth,
                                    lengthCenter
                                )
                            }
                            CornerCutType.RECTANGLE -> {
                                val radiusDepth =
                                    childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                        cutDepth
                                    )
                                val radiusLength =
                                    childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                        cutLength
                                    )
                                if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                    childCornerCutPath.lineTo(
                                        depthCenter - radiusDepth,
                                        lengthCenter
                                    )
                                    childCornerCutPath.arcTo(
                                        depthCenter - radiusDepth * 2.0F,
                                        lengthCenter - radiusLength * 2.0F,
                                        depthCenter,
                                        lengthCenter,
                                        90.0F,
                                        -90.0F,
                                        false
                                    )
                                    childCornerCutPath.arcTo(
                                        depthCenter,
                                        lengthCenter - radiusLength * 2.0F,
                                        depthCenter + radiusDepth * 2.0F,
                                        lengthCenter,
                                        180.0F,
                                        -90.0F,
                                        false
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter + halfCutDepth,
                                        lengthCenter
                                    )
                                } else {
                                    childCornerCutPath.lineTo(
                                        depthCenter + halfCutDepth,
                                        lengthCenter
                                    )
                                }
                            }
                            CornerCutType.RECTANGLE_INVERSE -> {
                                val radiusDepth =
                                    childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                        cutDepth
                                    )
                                val radiusLength =
                                    childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                        cutLength
                                    )
                                if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                    childCornerCutPath.lineTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter - halfCutLength + radiusLength
                                    )
                                    childCornerCutPath.arcTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter - halfCutLength,
                                        depthCenter - halfCutDepth + radiusDepth * 2.0F,
                                        lengthCenter - halfCutLength + radiusLength * 2.0F,
                                        180.0F,
                                        90.0F,
                                        false
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter + halfCutDepth - radiusDepth,
                                        lengthCenter - halfCutLength
                                    )
                                    childCornerCutPath.arcTo(
                                        depthCenter + halfCutDepth - radiusDepth * 2.0F,
                                        lengthCenter - halfCutLength,
                                        depthCenter + halfCutDepth,
                                        lengthCenter - halfCutLength + radiusLength * 2.0F,
                                        270.0F,
                                        90.0F,
                                        false
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter + halfCutDepth,
                                        lengthCenter
                                    )
                                } else {
                                    childCornerCutPath.lineTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter - halfCutLength
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter + halfCutDepth,
                                        lengthCenter - halfCutLength
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter + halfCutDepth,
                                        lengthCenter
                                    )
                                }
                            }
                        }

                        when (bottomPartCornerCutType) {
                            CornerCutType.OVAL -> {
                                childCornerCutPath.arcTo(
                                    depthCenter,
                                    lengthCenter,
                                    depthCenter + cutDepth,
                                    lengthCenter + cutLength,
                                    270.0F,
                                    -90.0F,
                                    false
                                )
                                childCornerCutPath.arcTo(
                                    depthCenter - cutDepth,
                                    lengthCenter,
                                    depthCenter,
                                    lengthCenter + cutLength,
                                    0.0F,
                                    -90.0F,
                                    false
                                )
                            }
                            CornerCutType.OVAL_INVERSE -> {
                                childCornerCutPath.arcTo(
                                    depthCenter - halfCutDepth,
                                    lengthCenter - halfCutLength,
                                    depthCenter + halfCutDepth,
                                    lengthCenter + halfCutLength,
                                    0.0F,
                                    180.0F,
                                    false
                                )
                            }
                            CornerCutType.BEVEL -> {
                                childCornerCutPath.lineTo(
                                    depthCenter,
                                    lengthCenter + halfCutLength
                                )
                                childCornerCutPath.lineTo(
                                    depthCenter - halfCutDepth,
                                    lengthCenter
                                )
                            }
                            CornerCutType.RECTANGLE -> {
                                val radiusDepth =
                                    childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                        cutDepth
                                    )
                                val radiusLength =
                                    childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                        cutLength
                                    )
                                if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                    childCornerCutPath.lineTo(
                                        depthCenter + radiusDepth,
                                        lengthCenter
                                    )
                                    childCornerCutPath.arcTo(
                                        depthCenter,
                                        lengthCenter,
                                        depthCenter + radiusDepth * 2.0F,
                                        lengthCenter + radiusLength * 2.0F,
                                        270.0F,
                                        -90.0F,
                                        false
                                    )
                                    childCornerCutPath.arcTo(
                                        depthCenter - radiusDepth * 2.0F,
                                        lengthCenter,
                                        depthCenter,
                                        lengthCenter + radiusLength * 2.0F,
                                        0.0F,
                                        -90.0F,
                                        false
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter
                                    )
                                } else {
                                    childCornerCutPath.lineTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter
                                    )
                                }
                            }
                            CornerCutType.RECTANGLE_INVERSE -> {
                                val radiusDepth =
                                    childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                        cutDepth
                                    )
                                val radiusLength =
                                    childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                        cutLength
                                    )
                                if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                    childCornerCutPath.lineTo(
                                        depthCenter + halfCutDepth,
                                        lengthCenter + halfCutLength - radiusLength
                                    )
                                    childCornerCutPath.arcTo(
                                        depthCenter + halfCutDepth - radiusDepth * 2.0F,
                                        lengthCenter + halfCutLength - radiusLength * 2.0F,
                                        depthCenter + halfCutDepth,
                                        lengthCenter + halfCutLength,
                                        0.0F,
                                        90.0F,
                                        false
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter - halfCutDepth + radiusDepth,
                                        lengthCenter + halfCutLength
                                    )
                                    childCornerCutPath.arcTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter + halfCutLength - radiusLength * 2.0F,
                                        depthCenter - halfCutDepth + radiusDepth * 2.0F,
                                        lengthCenter + halfCutLength,
                                        90.0F,
                                        90.0F,
                                        false
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter
                                    )
                                } else {
                                    childCornerCutPath.lineTo(
                                        depthCenter + halfCutDepth,
                                        lengthCenter + halfCutLength
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter + halfCutLength
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter
                                    )
                                }
                            }
                        }

                        if (childStartSideCornerCutRotationDegree != 0.0F) {
                            val matrix = Matrix()
                            matrix.postRotate(
                                childStartSideCornerCutRotationDegree,
                                depthCenter,
                                lengthCenter
                            )
                            childCornerCutPath.transform(matrix)
                        }
                    }
                }
            }
            //endregion

            //region Child Cut End Side
            if (childSideCutFlag containsFlag ChildSideCutFlag.END &&
                childEndSideCornerCutDepth > 0.0F &&
                childEndSideCornerCutLength > 0.0F
            ) {

                childEndSideDepthOffset =
                    if (isLtr) -childEndSideCornerCutDepthOffset else childEndSideCornerCutDepthOffset
                childEndSideLengthOffset = childEndSideCornerCutLengthOffset

                val cutDepth = childEndSideCornerCutDepth.coerceAtMost(maxAllowedDepth * 2)
                val cutLength = childEndSideCornerCutLength.coerceAtMost(maxAllowedLength * 2)
                val halfCutDepth = cutDepth / 2.0F
                val halfCutLength = cutLength / 2.0F

                val rotation =
                    if (isChildCornerCutEndRotationMirroredFromStartRotation) childStartSideCornerCutRotationDegree else -childEndSideCornerCutRotationDegree

                val depthCenter = paddedViewEnd + childEndSideDepthOffset
                childContactCutCenters.forEachIndexed { index, it ->
                    val lengthCenter = it + childEndSideLengthOffset

                    val indexFix = if (isTopAligned) 0 else -1
                    tempCustomCutPath.rewind()
                    tempRectF.set(
                        depthCenter - halfCutDepth,
                        lengthCenter - halfCutLength,
                        depthCenter + halfCutDepth,
                        lengthCenter + halfCutLength
                    )
                    val topContactView = getOrNull<View>(index + indexFix)
                    val bottomContactView = getOrNull<View>(index + indexFix + 1)
                    if (childCornerCutProvider?.getCornerCut(
                            this@CornerCutLinearLayout,
                            tempCustomCutPath,
                            ChildSideCutFlag.END,
                            tempRectF,
                            topContactView,
                            bottomContactView
                        ) == true
                    ) {
                        childCornerCutProvider?.getPathTransformationMatrix(
                            this@CornerCutLinearLayout,
                            tempCustomCutPath,
                            ChildSideCutFlag.END,
                            tempRectF,
                            topContactView,
                            bottomContactView
                        )?.apply {
                            tempCustomCutPath.transform(this)
                        }
                        tempCustomCutPath.close()
                        childCornerCutPath.addPath(tempCustomCutPath)
                    } else {
                        val topPartCornerCutType =
                            childEndSideCutTypes[index].first ?: childEndSideCornerCutType
                        val bottomPartCornerCutType =
                            childEndSideCutTypes[index].second ?: childEndSideCornerCutType

                        //skip cut for specific combinations
                        when {
                            topPartCornerCutType == CornerCutType.RECTANGLE &&
                                    bottomPartCornerCutType == CornerCutType.RECTANGLE
                                    && (childRectangleTypeCornerCutRoundCornerRadiusDepth == 0.0F || childRectangleTypeCornerCutRoundCornerRadiusLength == 0.0F) -> {
                                return@forEachIndexed
                            }
                        }

                        if (rotation != 0.0F) {
                            val matrix = Matrix()
                            matrix.postRotate(rotation, depthCenter, lengthCenter)
                            childCornerCutPath.transform(matrix)
                        }

                        childCornerCutPath.moveTo(
                            depthCenter + halfCutDepth,
                            lengthCenter
                        )

                        when (bottomPartCornerCutType) {
                            CornerCutType.OVAL -> {
                                childCornerCutPath.arcTo(
                                    depthCenter,
                                    lengthCenter,
                                    depthCenter + cutDepth,
                                    lengthCenter + cutLength,
                                    270.0F,
                                    -90.0F,
                                    false
                                )
                                childCornerCutPath.arcTo(
                                    depthCenter - cutDepth,
                                    lengthCenter,
                                    depthCenter,
                                    lengthCenter + cutLength,
                                    0.0F,
                                    -90.0F,
                                    false
                                )
                            }
                            CornerCutType.OVAL_INVERSE -> {
                                childCornerCutPath.arcTo(
                                    depthCenter - halfCutDepth,
                                    lengthCenter - halfCutLength,
                                    depthCenter + halfCutDepth,
                                    lengthCenter + halfCutLength,
                                    0.0F,
                                    180.0F,
                                    false
                                )
                            }
                            CornerCutType.BEVEL -> {
                                childCornerCutPath.lineTo(
                                    depthCenter,
                                    lengthCenter + halfCutLength
                                )
                                childCornerCutPath.lineTo(
                                    depthCenter - halfCutDepth,
                                    lengthCenter
                                )
                            }
                            CornerCutType.RECTANGLE -> {
                                val radiusDepth =
                                    childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                        cutDepth
                                    )
                                val radiusLength =
                                    childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                        cutLength
                                    )
                                if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                    childCornerCutPath.lineTo(
                                        depthCenter + radiusDepth,
                                        lengthCenter
                                    )
                                    childCornerCutPath.arcTo(
                                        depthCenter,
                                        lengthCenter,
                                        depthCenter + radiusDepth * 2.0F,
                                        lengthCenter + radiusLength * 2.0F,
                                        270.0F,
                                        -90.0F,
                                        false
                                    )
                                    childCornerCutPath.arcTo(
                                        depthCenter - radiusDepth * 2.0F,
                                        lengthCenter,
                                        depthCenter,
                                        lengthCenter + radiusLength * 2.0F,
                                        0.0F,
                                        -90.0F,
                                        false
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter
                                    )
                                } else {
                                    childCornerCutPath.lineTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter
                                    )
                                }
                            }
                            CornerCutType.RECTANGLE_INVERSE -> {
                                val radiusDepth =
                                    childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                        cutDepth
                                    )
                                val radiusLength =
                                    childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                        cutLength
                                    )
                                if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                    childCornerCutPath.lineTo(
                                        depthCenter + halfCutDepth,
                                        lengthCenter + halfCutLength - radiusLength
                                    )
                                    childCornerCutPath.arcTo(
                                        depthCenter + halfCutDepth - radiusDepth * 2.0F,
                                        lengthCenter + halfCutLength - radiusLength * 2.0F,
                                        depthCenter + halfCutDepth,
                                        lengthCenter + halfCutLength,
                                        0.0F,
                                        90.0F,
                                        false
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter - halfCutDepth + radiusDepth,
                                        lengthCenter + halfCutLength
                                    )
                                    childCornerCutPath.arcTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter + halfCutLength - radiusLength * 2.0F,
                                        depthCenter - halfCutDepth + radiusDepth * 2.0F,
                                        lengthCenter + halfCutLength,
                                        90.0F,
                                        90.0F,
                                        false
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter
                                    )
                                } else {
                                    childCornerCutPath.lineTo(
                                        depthCenter + halfCutDepth,
                                        lengthCenter + halfCutLength
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter + halfCutLength
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter
                                    )
                                }
                            }
                        }

                        when (topPartCornerCutType) {
                            CornerCutType.OVAL -> {
                                childCornerCutPath.arcTo(
                                    depthCenter - cutDepth,
                                    lengthCenter - cutLength,
                                    depthCenter,
                                    lengthCenter,
                                    90.0F,
                                    -90.0F,
                                    false
                                )
                                childCornerCutPath.arcTo(
                                    depthCenter,
                                    lengthCenter - cutLength,
                                    depthCenter + cutDepth,
                                    lengthCenter,
                                    180.0F,
                                    -90.0F,
                                    false
                                )

                            }
                            CornerCutType.OVAL_INVERSE -> {
                                childCornerCutPath.arcTo(
                                    depthCenter - halfCutDepth,
                                    lengthCenter - halfCutLength,
                                    depthCenter + halfCutDepth,
                                    lengthCenter + halfCutLength,
                                    180.0F,
                                    180.0F,
                                    false
                                )
                            }
                            CornerCutType.BEVEL -> {
                                childCornerCutPath.lineTo(
                                    depthCenter,
                                    lengthCenter - halfCutLength
                                )
                                childCornerCutPath.lineTo(
                                    depthCenter + halfCutDepth,
                                    lengthCenter
                                )
                            }
                            CornerCutType.RECTANGLE -> {
                                val radiusDepth =
                                    childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                        cutDepth
                                    )
                                val radiusLength =
                                    childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                        cutLength
                                    )
                                if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                    childCornerCutPath.lineTo(
                                        depthCenter - radiusDepth,
                                        lengthCenter
                                    )
                                    childCornerCutPath.arcTo(
                                        depthCenter - radiusDepth * 2.0F,
                                        lengthCenter - radiusLength * 2.0F,
                                        depthCenter,
                                        lengthCenter,
                                        90.0F,
                                        -90.0F,
                                        false
                                    )
                                    childCornerCutPath.arcTo(
                                        depthCenter,
                                        lengthCenter - radiusLength * 2.0F,
                                        depthCenter + radiusDepth * 2.0F,
                                        lengthCenter,
                                        180.0F,
                                        -90.0F,
                                        false
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter + halfCutDepth,
                                        lengthCenter
                                    )
                                } else {
                                    childCornerCutPath.lineTo(
                                        depthCenter + halfCutDepth,
                                        lengthCenter
                                    )
                                }
                            }
                            CornerCutType.RECTANGLE_INVERSE -> {
                                val radiusDepth =
                                    childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                        cutDepth
                                    )
                                val radiusLength =
                                    childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                        cutLength
                                    )
                                if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                    childCornerCutPath.lineTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter - halfCutLength + radiusLength
                                    )
                                    childCornerCutPath.arcTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter - halfCutLength,
                                        depthCenter - halfCutDepth + radiusDepth * 2.0F,
                                        lengthCenter - halfCutLength + radiusLength * 2.0F,
                                        180.0F,
                                        90.0F,
                                        false
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter + halfCutDepth - radiusDepth,
                                        lengthCenter - halfCutLength
                                    )
                                    childCornerCutPath.arcTo(
                                        depthCenter + halfCutDepth - radiusDepth * 2.0F,
                                        lengthCenter - halfCutLength,
                                        depthCenter + halfCutDepth,
                                        lengthCenter - halfCutLength + radiusLength * 2.0F,
                                        270.0F,
                                        90.0F,
                                        false
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter + halfCutDepth,
                                        lengthCenter
                                    )
                                } else {
                                    childCornerCutPath.lineTo(
                                        depthCenter - halfCutDepth,
                                        lengthCenter - halfCutLength
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter + halfCutDepth,
                                        lengthCenter - halfCutLength
                                    )
                                    childCornerCutPath.lineTo(
                                        depthCenter + halfCutDepth,
                                        lengthCenter
                                    )
                                }
                            }
                        }

                        if (rotation != 0.0F) {
                            val matrix = Matrix()
                            matrix.postRotate(-rotation, depthCenter, lengthCenter)
                            childCornerCutPath.transform(matrix)
                        }
                    }
                }
            }
            //endregion
            //endregion

        } else {
            // Horizontal orientation
            halfDividerDrawableHeight = (dividerDrawable?.intrinsicWidth?.toFloat() ?: 0.0F) / 2.0F
            halfBeginningDividerDrawableHeight = halfDividerDrawableHeight
                .takeIf { showDividers containsFlag SHOW_DIVIDER_BEGINNING } ?: 0.0F
            halfMiddleDividerDrawableHeight = halfDividerDrawableHeight
                .takeIf { showDividers containsFlag SHOW_DIVIDER_MIDDLE } ?: 0.0F
            halfEndDividerDrawableHeight = halfDividerDrawableHeight
                .takeIf { showDividers containsFlag SHOW_DIVIDER_END } ?: 0.0F

            val firstChildStart =
                firstChild?.let { if (isLtr) it.left - it.marginLeft - halfBeginningDividerDrawableHeight else it.right + it.marginRight + halfBeginningDividerDrawableHeight }
            val isStartAligned = paddedViewStart == firstChildStart
            val lastChildEnd =
                lastChild?.let { if (isLtr) it.right + it.marginRight + halfEndDividerDrawableHeight else it.left - it.marginLeft - halfEndDividerDrawableHeight }
            val isEndAligned = paddedViewEnd == lastChildEnd
            val isLeftAligned = if (isLtr) isStartAligned else isEndAligned
            val isRightAligned = if (isLtr) isEndAligned else isStartAligned

            leftTopCornerCutType = firstChildLayoutParams?.leftTopCornerCutType
                ?.takeIf { firstChildLayoutParams.couldEdgeChildOverrideParentCornerCutTypeIfEdgeAligned && isLeftAligned }
                ?: this.leftTopCornerCutType

            rightTopCornerCutType = lastChildLayoutParams?.rightTopCornerCutType
                ?.takeIf { lastChildLayoutParams.couldEdgeChildOverrideParentCornerCutTypeIfEdgeAligned && isRightAligned }
                ?: this.rightTopCornerCutType

            rightBottomCornerCutType = lastChildLayoutParams?.rightBottomCornerCutType
                ?.takeIf { lastChildLayoutParams.couldEdgeChildOverrideParentCornerCutTypeIfEdgeAligned && isRightAligned }
                ?: this.rightBottomCornerCutType

            leftBottomCornerCutType = firstChildLayoutParams?.leftBottomCornerCutType
                ?.takeIf { firstChildLayoutParams.couldEdgeChildOverrideParentCornerCutTypeIfEdgeAligned && isLeftAligned }
                ?: this.leftBottomCornerCutType

            if (customDividerShowFlag containsFlag CustomDividerShowFlag.CONTAINER_BEGINNING) {
                customDividerPoints.add(paddedViewStart + (customDividerHeight/2.0F).let { if (isLtr) it else -it })
                customDividerTypes.add(CustomDividerShowFlag.CONTAINER_BEGINNING)
                customDividerTypedIndexes.add(0)
            }

            forEachIndexed { index, view ->
                if (index < lastChildIndex) {
                    if (index == 0 && !isStartAligned) {
                        childContactCutCenters.add(firstChildStart!!)

                        val lp = (view.layoutParams as? LayoutParams)

                        val startTopCutType: CornerCutType?
                        val startBottomCutType: CornerCutType?
                        val endTopCutType: CornerCutType?
                        val endBottomCutType: CornerCutType?

                        if (isLtr) {
                            startTopCutType =
                                lp?.edgeChildParentContactStartBottomChildCornerCutType
                            startBottomCutType = lp?.startBottomCornerCutType
                            endTopCutType = lp?.edgeChildParentContactStartTopChildCornerCutType
                            endBottomCutType = lp?.startTopCornerCutType
                        } else {
                            startTopCutType = lp?.edgeChildParentContactStartTopChildCornerCutType
                            startBottomCutType = lp?.startTopCornerCutType
                            endTopCutType = lp?.edgeChildParentContactStartBottomChildCornerCutType
                            endBottomCutType = lp?.startBottomCornerCutType
                        }

                        childStartSideCutTypes.add(startTopCutType to startBottomCutType)
                        childEndSideCutTypes.add(endTopCutType to endBottomCutType)
                    }

                    floatContactCutCenter = when {
                        isLtr -> view.right + view.marginRight + halfMiddleDividerDrawableHeight
                        else -> view.left - view.marginLeft - halfMiddleDividerDrawableHeight
                    }
                    childContactCutCenters.add(floatContactCutCenter)

                    val topLp = (view.layoutParams as? LayoutParams)
                    val bottomLp = (getOrNull<View>(index + 1)?.layoutParams as? LayoutParams)

                    val startTopCutType: CornerCutType?
                    val startBottomCutType: CornerCutType?
                    val endTopCutType: CornerCutType?
                    val endBottomCutType: CornerCutType?

                    if (isLtr) {
                        startTopCutType = topLp?.endBottomCornerCutType
                        startBottomCutType = bottomLp?.startBottomCornerCutType
                        endTopCutType = topLp?.endTopCornerCutType
                        endBottomCutType = bottomLp?.startTopCornerCutType
                    } else {
                        startTopCutType = topLp?.endTopCornerCutType
                        startBottomCutType = bottomLp?.startTopCornerCutType
                        endTopCutType = topLp?.endBottomCornerCutType
                        endBottomCutType = bottomLp?.startBottomCornerCutType
                    }

                    childStartSideCutTypes.add(startTopCutType to startBottomCutType)
                    childEndSideCutTypes.add(endTopCutType to endBottomCutType)

                    if (index == 0 && customDividerShowFlag containsFlag CustomDividerShowFlag.BEGINNING) {
                        customDividerPoints.add(
                            when {
                                isLtr -> view.left - view.marginLeft - halfMiddleDividerDrawableHeight
                                else -> view.right + view.marginRight + halfMiddleDividerDrawableHeight
                            }
                        )
                        customDividerTypes.add(CustomDividerShowFlag.BEGINNING)
                        customDividerTypedIndexes.add(0)
                    }

                    if (showMiddleCustomDivider) {
                        customDividerPoints.add(floatContactCutCenter)
                        customDividerTypes.add(CustomDividerShowFlag.MIDDLE)
                        customDividerTypedIndexes.add(index)
                    }
                } else {
                    if (!isEndAligned) {
                        childContactCutCenters.add(lastChildEnd!!)

                        val lp = (view.layoutParams as? LayoutParams)

                        val startTopCutType: CornerCutType?
                        val startBottomCutType: CornerCutType?
                        val endTopCutType: CornerCutType?
                        val endBottomCutType: CornerCutType?

                        if (isLtr) {
                            startTopCutType = lp?.endBottomCornerCutType
                            startBottomCutType =
                                lp?.edgeChildParentContactEndBottomChildCornerCutType
                            endTopCutType = lp?.endTopCornerCutType
                            endBottomCutType = lp?.edgeChildParentContactEndTopChildCornerCutType
                        } else {
                            startTopCutType = lp?.endTopCornerCutType
                            startBottomCutType = lp?.edgeChildParentContactEndTopChildCornerCutType
                            endTopCutType = lp?.endBottomCornerCutType
                            endBottomCutType = lp?.edgeChildParentContactEndBottomChildCornerCutType
                        }

                        childStartSideCutTypes.add(startTopCutType to startBottomCutType)
                        childEndSideCutTypes.add(endTopCutType to endBottomCutType)
                    }

                    if (customDividerShowFlag containsFlag CustomDividerShowFlag.END) {
                        customDividerPoints.add(
                            when {
                                isLtr -> view.right + view.marginRight + halfMiddleDividerDrawableHeight
                                else -> view.left - view.marginLeft - halfMiddleDividerDrawableHeight
                            }
                        )
                        customDividerTypes.add(CustomDividerShowFlag.END)
                        customDividerTypedIndexes.add(0)
                    }
                }
            }

            if (customDividerShowFlag containsFlag CustomDividerShowFlag.CONTAINER_END) {
                customDividerPoints.add(paddedViewEnd + (customDividerHeight/2.0F).let { if (isLtr) -it else it })
                customDividerTypes.add(CustomDividerShowFlag.CONTAINER_END)
                customDividerTypedIndexes.add(0)
            }

            if (shouldUseMaxAllowedCornerCutSize) {
                if (shouldUseMaxAllowedCornerCutDepthOrLengthToBeEqual) {
                    maxAllowedDepth = min(paddedViewHeight, paddedViewWidth) / 2.0F
                    maxAllowedLength = maxAllowedDepth
                } else {
                    maxAllowedDepth = paddedViewHeight / 2.0F
                    maxAllowedLength = paddedViewWidth / 2.0F
                }

                leftTopX = maxAllowedLength
                leftTopY = maxAllowedDepth
                rightTopX = maxAllowedLength
                rightTopY = maxAllowedDepth
                rightBottomX = maxAllowedLength
                rightBottomY = maxAllowedDepth
                leftBottomX = maxAllowedLength
                leftBottomY = maxAllowedDepth
            } else {
                maxAllowedDepth = paddedViewHeight / 2.0F
                maxAllowedLength = paddedViewWidth / 2.0F

                leftTopX = cornerCutDimensions[1].coerceAtMost(maxAllowedLength)
                leftTopY = cornerCutDimensions[0].coerceAtMost(maxAllowedDepth)
                rightTopX = cornerCutDimensions[3].coerceAtMost(maxAllowedLength)
                rightTopY = cornerCutDimensions[2].coerceAtMost(maxAllowedDepth)
                rightBottomX = cornerCutDimensions[5].coerceAtMost(maxAllowedLength)
                rightBottomY = cornerCutDimensions[4].coerceAtMost(maxAllowedDepth)
                leftBottomX = cornerCutDimensions[7].coerceAtMost(maxAllowedLength)
                leftBottomY = cornerCutDimensions[6].coerceAtMost(maxAllowedDepth)
            }

            rectangleTypeCornerCutRoundCornerRadiusX =
                this.rectangleTypeCornerCutRoundCornerRadiusLength
            rectangleTypeCornerCutRoundCornerRadiusY =
                this.rectangleTypeCornerCutRoundCornerRadiusDepth

            //region Child Cut

            //region Child Cut Start Side
            if (childSideCutFlag containsFlag ChildSideCutFlag.START &&
                childStartSideCornerCutDepth > 0.0F &&
                childStartSideCornerCutLength > 0.0F
            ) {

                childStartSideDepthOffset =
                    if (isLtr) -childStartSideCornerCutDepthOffset else childStartSideCornerCutDepthOffset
                childStartSideLengthOffset =
                    if (isLtr) childStartSideCornerCutLengthOffset else -childStartSideCornerCutLengthOffset

                val cutDepth = childStartSideCornerCutDepth.coerceAtMost(maxAllowedDepth)
                val cutLength = childStartSideCornerCutLength.coerceAtMost(maxAllowedLength)
                val halfCutDepth = cutDepth / 2.0F
                val halfCutLength = cutLength / 2.0F

                val depthCenter =
                    (if (isLtr) paddedViewBottom else paddedViewTop) + childStartSideDepthOffset
                childContactCutCenters.forEachIndexed { index, it ->
                    val lengthCenter = it + childStartSideLengthOffset

                    val indexFix = if (isStartAligned) 0 else -1
                    tempCustomCutPath.rewind()
                    tempRectF.set(
                        lengthCenter - halfCutLength,
                        depthCenter - halfCutDepth,
                        lengthCenter + halfCutLength,
                        depthCenter + halfCutDepth
                    )
                    val topContactView = getOrNull<View>(index + indexFix)
                    val bottomContactView = getOrNull<View>(index + indexFix + 1)
                    if (childCornerCutProvider?.getCornerCut(
                            this@CornerCutLinearLayout,
                            tempCustomCutPath,
                            ChildSideCutFlag.START,
                            tempRectF,
                            topContactView,
                            bottomContactView
                        ) == true
                    ) {
                        childCornerCutProvider?.getPathTransformationMatrix(
                            this@CornerCutLinearLayout,
                            tempCustomCutPath,
                            ChildSideCutFlag.START,
                            tempRectF,
                            topContactView,
                            bottomContactView
                        )?.apply {
                            tempCustomCutPath.transform(this)
                        }
                        tempCustomCutPath.close()
                        childCornerCutPath.addPath(tempCustomCutPath)
                    } else {
                        val topPartCornerCutType =
                            childStartSideCutTypes[index].first ?: childStartSideCornerCutType
                        val bottomPartCornerCutType =
                            childStartSideCutTypes[index].second ?: childStartSideCornerCutType

                        //skip cut for specific combinations
                        when {
                            topPartCornerCutType == CornerCutType.RECTANGLE &&
                                    bottomPartCornerCutType == CornerCutType.RECTANGLE
                                    && (childRectangleTypeCornerCutRoundCornerRadiusDepth == 0.0F || childRectangleTypeCornerCutRoundCornerRadiusLength == 0.0F) -> {
                                return@forEachIndexed
                            }
                        }

                        if (childStartSideCornerCutRotationDegree != 0.0F) {
                            val matrix = Matrix()
                            matrix.postRotate(
                                -childStartSideCornerCutRotationDegree,
                                depthCenter,
                                lengthCenter
                            )
                            childCornerCutPath.transform(matrix)
                        }

                        childCornerCutPath.moveTo(
                            lengthCenter,
                            depthCenter + halfCutDepth
                        )

                        if (isLtr) {
                            when (topPartCornerCutType) {
                                CornerCutType.OVAL -> {
                                    childCornerCutPath.arcTo(
                                        lengthCenter - cutLength,
                                        depthCenter,
                                        lengthCenter,
                                        depthCenter + cutDepth,
                                        0.0F,
                                        -90.0F,
                                        false
                                    )
                                    childCornerCutPath.arcTo(
                                        lengthCenter - cutLength,
                                        depthCenter - cutDepth,
                                        lengthCenter,
                                        depthCenter,
                                        90.0F,
                                        -90.0F,
                                        false
                                    )

                                }
                                CornerCutType.OVAL_INVERSE -> {
                                    childCornerCutPath.arcTo(
                                        lengthCenter - halfCutLength,
                                        depthCenter - halfCutDepth,
                                        lengthCenter + halfCutLength,
                                        depthCenter + halfCutDepth,
                                        90.0F,
                                        180.0F,
                                        false
                                    )
                                }
                                CornerCutType.BEVEL -> {
                                    childCornerCutPath.lineTo(
                                        lengthCenter - halfCutLength,
                                        depthCenter
                                    )
                                    childCornerCutPath.lineTo(
                                        lengthCenter,
                                        depthCenter - halfCutDepth
                                    )
                                }
                                CornerCutType.RECTANGLE -> {
                                    val radiusDepth =
                                        childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                            cutDepth
                                        )
                                    val radiusLength =
                                        childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                            cutLength
                                        )
                                    if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + radiusDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter - radiusLength * 2.0F,
                                            depthCenter,
                                            lengthCenter,
                                            depthCenter + radiusDepth * 2.0F,
                                            0.0F,
                                            -90.0F,
                                            false
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter - radiusLength * 2.0F,
                                            depthCenter - radiusDepth * 2.0F,
                                            lengthCenter,
                                            depthCenter,
                                            90.0F,
                                            -90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - halfCutDepth
                                        )
                                    } else {
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - halfCutDepth
                                        )
                                    }
                                }
                                CornerCutType.RECTANGLE_INVERSE -> {
                                    val radiusDepth =
                                        childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                            cutDepth
                                        )
                                    val radiusLength =
                                        childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                            cutLength
                                        )
                                    if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                        childCornerCutPath.lineTo(
                                            lengthCenter - halfCutLength + radiusLength,
                                            depthCenter + halfCutDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter + halfCutDepth - radiusDepth * 2.0F,
                                            lengthCenter - halfCutLength + radiusLength * 2.0F,
                                            depthCenter + halfCutDepth,
                                            90.0F,
                                            90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter - halfCutDepth + radiusDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter - halfCutDepth,
                                            lengthCenter - halfCutLength + radiusLength * 2.0F,
                                            depthCenter - halfCutDepth + radiusDepth * 2.0F,
                                            180.0F,
                                            90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - halfCutDepth
                                        )
                                    } else {
                                        childCornerCutPath.lineTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter + halfCutDepth
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter - halfCutDepth
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - halfCutDepth
                                        )
                                    }
                                }
                            }
                            when (bottomPartCornerCutType) {
                                CornerCutType.OVAL -> {
                                    childCornerCutPath.arcTo(
                                        lengthCenter,
                                        depthCenter - cutDepth,
                                        lengthCenter + cutLength,
                                        depthCenter,
                                        180.0F,
                                        -90.0F,
                                        false
                                    )
                                    childCornerCutPath.arcTo(
                                        lengthCenter,
                                        depthCenter,
                                        lengthCenter + cutLength,
                                        depthCenter + cutDepth,
                                        270.0F,
                                        -90.0F,
                                        false
                                    )
                                }
                                CornerCutType.OVAL_INVERSE -> {
                                    childCornerCutPath.arcTo(
                                        lengthCenter - halfCutLength,
                                        depthCenter - halfCutDepth,
                                        lengthCenter + halfCutLength,
                                        depthCenter + halfCutDepth,
                                        270.0F,
                                        180.0F,
                                        false
                                    )
                                }
                                CornerCutType.BEVEL -> {
                                    childCornerCutPath.lineTo(
                                        lengthCenter + halfCutLength,
                                        depthCenter
                                    )
                                    childCornerCutPath.lineTo(
                                        lengthCenter,
                                        depthCenter + halfCutDepth
                                    )
                                }
                                CornerCutType.RECTANGLE -> {
                                    val radiusDepth =
                                        childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                            cutDepth
                                        )
                                    val radiusLength =
                                        childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                            cutLength
                                        )
                                    if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + radiusDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter,
                                            depthCenter - radiusDepth * 2.0F,
                                            lengthCenter + radiusLength * 2.0F,
                                            depthCenter,
                                            180.0F,
                                            -90.0F,
                                            false
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter,
                                            depthCenter,
                                            lengthCenter + radiusLength * 2.0F,
                                            depthCenter + radiusDepth * 2.0F,
                                            270.0F,
                                            -90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + halfCutDepth
                                        )
                                    } else {
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + halfCutDepth
                                        )
                                    }
                                }
                                CornerCutType.RECTANGLE_INVERSE -> {
                                    val radiusDepth =
                                        childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                            cutDepth
                                        )
                                    val radiusLength =
                                        childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                            cutLength
                                        )
                                    if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                        childCornerCutPath.lineTo(
                                            lengthCenter + halfCutLength - radiusLength,
                                            depthCenter - halfCutDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter + halfCutLength - radiusLength * 2.0F,
                                            depthCenter - halfCutDepth,
                                            lengthCenter + halfCutLength,
                                            depthCenter - halfCutDepth + radiusDepth * 2.0F,
                                            270.0F,
                                            90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter + halfCutLength,
                                            depthCenter + halfCutDepth - radiusDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter + halfCutLength - radiusLength * 2.0F,
                                            depthCenter + halfCutDepth - radiusDepth * 2.0F,
                                            lengthCenter + halfCutLength,
                                            depthCenter + halfCutDepth,
                                            0.0F,
                                            90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + halfCutDepth
                                        )
                                    } else {
                                        childCornerCutPath.lineTo(
                                            lengthCenter + halfCutLength,
                                            depthCenter - halfCutDepth
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter + halfCutLength,
                                            depthCenter + halfCutDepth
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + halfCutDepth
                                        )
                                    }
                                }
                            }
                        } else {
                            // copy from ltr - just do same logic for topPartCornerCutType in rtl as for bottomPartCornerCutType in ltr and vice versa
                            when (topPartCornerCutType) {
                                CornerCutType.OVAL -> {
                                    childCornerCutPath.arcTo(
                                        lengthCenter,
                                        depthCenter - cutDepth,
                                        lengthCenter + cutLength,
                                        depthCenter,
                                        180.0F,
                                        -90.0F,
                                        false
                                    )
                                    childCornerCutPath.arcTo(
                                        lengthCenter,
                                        depthCenter,
                                        lengthCenter + cutLength,
                                        depthCenter + cutDepth,
                                        270.0F,
                                        -90.0F,
                                        false
                                    )
                                }
                                CornerCutType.OVAL_INVERSE -> {
                                    childCornerCutPath.arcTo(
                                        lengthCenter - halfCutLength,
                                        depthCenter - halfCutDepth,
                                        lengthCenter + halfCutLength,
                                        depthCenter + halfCutDepth,
                                        270.0F,
                                        180.0F,
                                        false
                                    )
                                }
                                CornerCutType.BEVEL -> {
                                    childCornerCutPath.lineTo(
                                        lengthCenter + halfCutLength,
                                        depthCenter
                                    )
                                    childCornerCutPath.lineTo(
                                        lengthCenter,
                                        depthCenter + halfCutDepth
                                    )
                                }
                                CornerCutType.RECTANGLE -> {
                                    val radiusDepth =
                                        childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                            cutDepth
                                        )
                                    val radiusLength =
                                        childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                            cutLength
                                        )
                                    if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + radiusDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter,
                                            depthCenter - radiusDepth * 2.0F,
                                            lengthCenter + radiusLength * 2.0F,
                                            depthCenter,
                                            180.0F,
                                            -90.0F,
                                            false
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter,
                                            depthCenter,
                                            lengthCenter + radiusLength * 2.0F,
                                            depthCenter + radiusDepth * 2.0F,
                                            270.0F,
                                            -90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + halfCutDepth
                                        )
                                    } else {
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + halfCutDepth
                                        )
                                    }
                                }
                                CornerCutType.RECTANGLE_INVERSE -> {
                                    val radiusDepth =
                                        childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                            cutDepth
                                        )
                                    val radiusLength =
                                        childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                            cutLength
                                        )
                                    if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                        childCornerCutPath.lineTo(
                                            lengthCenter + halfCutLength - radiusLength,
                                            depthCenter - halfCutDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter + halfCutLength - radiusLength * 2.0F,
                                            depthCenter - halfCutDepth,
                                            lengthCenter + halfCutLength,
                                            depthCenter - halfCutDepth + radiusDepth * 2.0F,
                                            270.0F,
                                            90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter + halfCutLength,
                                            depthCenter + halfCutDepth - radiusDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter + halfCutLength - radiusLength * 2.0F,
                                            depthCenter + halfCutDepth - radiusDepth * 2.0F,
                                            lengthCenter + halfCutLength,
                                            depthCenter + halfCutDepth,
                                            0.0F,
                                            90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + halfCutDepth
                                        )
                                    } else {
                                        childCornerCutPath.lineTo(
                                            lengthCenter + halfCutLength,
                                            depthCenter - halfCutDepth
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter + halfCutLength,
                                            depthCenter + halfCutDepth
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + halfCutDepth
                                        )
                                    }
                                }
                            }
                            when (bottomPartCornerCutType) {
                                CornerCutType.OVAL -> {
                                    childCornerCutPath.arcTo(
                                        lengthCenter - cutLength,
                                        depthCenter,
                                        lengthCenter,
                                        depthCenter + cutDepth,
                                        0.0F,
                                        -90.0F,
                                        false
                                    )
                                    childCornerCutPath.arcTo(
                                        lengthCenter - cutLength,
                                        depthCenter - cutDepth,
                                        lengthCenter,
                                        depthCenter,
                                        90.0F,
                                        -90.0F,
                                        false
                                    )

                                }
                                CornerCutType.OVAL_INVERSE -> {
                                    childCornerCutPath.arcTo(
                                        lengthCenter - halfCutLength,
                                        depthCenter - halfCutDepth,
                                        lengthCenter + halfCutLength,
                                        depthCenter + halfCutDepth,
                                        90.0F,
                                        180.0F,
                                        false
                                    )
                                }
                                CornerCutType.BEVEL -> {
                                    childCornerCutPath.lineTo(
                                        lengthCenter - halfCutLength,
                                        depthCenter
                                    )
                                    childCornerCutPath.lineTo(
                                        lengthCenter,
                                        depthCenter - halfCutDepth
                                    )
                                }
                                CornerCutType.RECTANGLE -> {
                                    val radiusDepth =
                                        childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                            cutDepth
                                        )
                                    val radiusLength =
                                        childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                            cutLength
                                        )
                                    if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + radiusDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter - radiusLength * 2.0F,
                                            depthCenter,
                                            lengthCenter,
                                            depthCenter + radiusDepth * 2.0F,
                                            0.0F,
                                            -90.0F,
                                            false
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter - radiusLength * 2.0F,
                                            depthCenter - radiusDepth * 2.0F,
                                            lengthCenter,
                                            depthCenter,
                                            90.0F,
                                            -90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - halfCutDepth
                                        )
                                    } else {
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - halfCutDepth
                                        )
                                    }
                                }
                                CornerCutType.RECTANGLE_INVERSE -> {
                                    val radiusDepth =
                                        childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                            cutDepth
                                        )
                                    val radiusLength =
                                        childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                            cutLength
                                        )
                                    if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                        childCornerCutPath.lineTo(
                                            lengthCenter - halfCutLength + radiusLength,
                                            depthCenter + halfCutDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter + halfCutDepth - radiusDepth * 2.0F,
                                            lengthCenter - halfCutLength + radiusLength * 2.0F,
                                            depthCenter + halfCutDepth,
                                            90.0F,
                                            90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter - halfCutDepth + radiusDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter - halfCutDepth,
                                            lengthCenter - halfCutLength + radiusLength * 2.0F,
                                            depthCenter - halfCutDepth + radiusDepth * 2.0F,
                                            180.0F,
                                            90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - halfCutDepth
                                        )
                                    } else {
                                        childCornerCutPath.lineTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter + halfCutDepth
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter - halfCutDepth
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - halfCutDepth
                                        )
                                    }
                                }
                            }
                        }

                        if (childStartSideCornerCutRotationDegree != 0.0F) {
                            val matrix = Matrix()
                            matrix.postRotate(
                                childStartSideCornerCutRotationDegree,
                                depthCenter,
                                lengthCenter
                            )
                            childCornerCutPath.transform(matrix)
                        }
                    }
                }
            }
            //endregion

            //region Child Cut End Side
            if (childSideCutFlag containsFlag ChildSideCutFlag.END &&
                childEndSideCornerCutDepth > 0.0F &&
                childEndSideCornerCutLength > 0.0F
            ) {

                childEndSideDepthOffset =
                    if (isLtr) childEndSideCornerCutDepthOffset else -childEndSideCornerCutDepthOffset
                childEndSideLengthOffset =
                    if (isLtr) childEndSideCornerCutLengthOffset else -childEndSideCornerCutLengthOffset

                val cutDepth = childEndSideCornerCutDepth.coerceAtMost(maxAllowedDepth)
                val cutLength = childEndSideCornerCutLength.coerceAtMost(maxAllowedLength)
                val halfCutDepth = cutDepth / 2.0F
                val halfCutLength = cutLength / 2.0F

                val rotation =
                    if (isChildCornerCutEndRotationMirroredFromStartRotation) childStartSideCornerCutRotationDegree else -childEndSideCornerCutRotationDegree

                val depthCenter =
                    (if (isLtr) paddedViewTop else paddedViewBottom) + childEndSideDepthOffset
                childContactCutCenters.forEachIndexed { index, it ->
                    val lengthCenter = it + childEndSideLengthOffset

                    val indexFix = if (isStartAligned) 0 else -1
                    tempCustomCutPath.rewind()
                    tempRectF.set(
                        lengthCenter - halfCutLength,
                        depthCenter - halfCutDepth,
                        lengthCenter + halfCutLength,
                        depthCenter + halfCutDepth
                    )
                    val topContactView = getOrNull<View>(index + indexFix)
                    val bottomContactView = getOrNull<View>(index + indexFix + 1)
                    if (childCornerCutProvider?.getCornerCut(
                            this@CornerCutLinearLayout,
                            tempCustomCutPath,
                            ChildSideCutFlag.END,
                            tempRectF,
                            topContactView,
                            bottomContactView
                        ) == true
                    ) {
                        childCornerCutProvider?.getPathTransformationMatrix(
                            this@CornerCutLinearLayout,
                            tempCustomCutPath,
                            ChildSideCutFlag.END,
                            tempRectF,
                            topContactView,
                            bottomContactView
                        )?.apply {
                            tempCustomCutPath.transform(this)
                        }
                        tempCustomCutPath.close()
                        childCornerCutPath.addPath(tempCustomCutPath)
                    } else {

                        val topPartCornerCutType =
                            childEndSideCutTypes[index].first ?: childEndSideCornerCutType
                        val bottomPartCornerCutType =
                            childEndSideCutTypes[index].second ?: childEndSideCornerCutType

                        //skip cut for specific combinations
                        when {
                            topPartCornerCutType == CornerCutType.RECTANGLE &&
                                    bottomPartCornerCutType == CornerCutType.RECTANGLE
                                    && (childRectangleTypeCornerCutRoundCornerRadiusDepth == 0.0F || childRectangleTypeCornerCutRoundCornerRadiusLength == 0.0F) -> {
                                return@forEachIndexed
                            }
                        }

                        if (rotation != 0.0F) {
                            val matrix = Matrix()
                            matrix.postRotate(rotation, depthCenter, lengthCenter)
                            childCornerCutPath.transform(matrix)
                        }

                        childCornerCutPath.moveTo(
                            lengthCenter,
                            depthCenter - halfCutDepth
                        )

                        if (isLtr) {
                            when (bottomPartCornerCutType) {
                                CornerCutType.OVAL -> {
                                    childCornerCutPath.arcTo(
                                        lengthCenter,
                                        depthCenter - cutDepth,
                                        lengthCenter + cutLength,
                                        depthCenter,
                                        180.0F,
                                        -90.0F,
                                        false
                                    )
                                    childCornerCutPath.arcTo(
                                        lengthCenter,
                                        depthCenter,
                                        lengthCenter + cutLength,
                                        depthCenter + cutDepth,
                                        270.0F,
                                        -90.0F,
                                        false
                                    )
                                }
                                CornerCutType.OVAL_INVERSE -> {
                                    childCornerCutPath.arcTo(
                                        lengthCenter - halfCutLength,
                                        depthCenter - halfCutDepth,
                                        lengthCenter + halfCutLength,
                                        depthCenter + halfCutDepth,
                                        270.0F,
                                        180.0F,
                                        false
                                    )
                                }
                                CornerCutType.BEVEL -> {
                                    childCornerCutPath.lineTo(
                                        lengthCenter + halfCutLength,
                                        depthCenter
                                    )
                                    childCornerCutPath.lineTo(
                                        lengthCenter,
                                        depthCenter + halfCutDepth
                                    )
                                }
                                CornerCutType.RECTANGLE -> {
                                    val radiusDepth =
                                        childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                            cutDepth
                                        )
                                    val radiusLength =
                                        childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                            cutLength
                                        )
                                    if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - radiusDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter,
                                            depthCenter - radiusDepth * 2.0F,
                                            lengthCenter + radiusLength * 2.0F,
                                            depthCenter,
                                            180.0F,
                                            -90.0F,
                                            false
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter,
                                            depthCenter,
                                            lengthCenter + radiusLength * 2.0F,
                                            depthCenter + radiusDepth * 2.0F,
                                            270.0F,
                                            -90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + halfCutDepth
                                        )
                                    } else {
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + halfCutDepth
                                        )
                                    }
                                }
                                CornerCutType.RECTANGLE_INVERSE -> {
                                    val radiusDepth =
                                        childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                            cutDepth
                                        )
                                    val radiusLength =
                                        childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                            cutLength
                                        )
                                    if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                        childCornerCutPath.lineTo(
                                            lengthCenter + halfCutLength - radiusLength,
                                            depthCenter - halfCutDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter + halfCutLength - radiusLength * 2.0F,
                                            depthCenter - halfCutDepth,
                                            lengthCenter + halfCutLength,
                                            depthCenter - halfCutDepth + radiusDepth * 2.0F,
                                            270.0F,
                                            90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter + halfCutLength,
                                            depthCenter + halfCutDepth - radiusDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter + halfCutLength - radiusLength * 2.0F,
                                            depthCenter + halfCutDepth - radiusDepth * 2.0F,
                                            lengthCenter + halfCutLength,
                                            depthCenter + halfCutDepth,
                                            0.0F,
                                            90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + halfCutDepth
                                        )
                                    } else {
                                        childCornerCutPath.lineTo(
                                            lengthCenter + halfCutLength,
                                            depthCenter - halfCutDepth
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter + halfCutLength,
                                            depthCenter + halfCutDepth
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + halfCutDepth
                                        )
                                    }
                                }
                            }
                            when (topPartCornerCutType) {
                                CornerCutType.OVAL -> {
                                    childCornerCutPath.arcTo(
                                        lengthCenter - cutLength,
                                        depthCenter,
                                        lengthCenter,
                                        depthCenter + cutDepth,
                                        0.0F,
                                        -90.0F,
                                        false
                                    )
                                    childCornerCutPath.arcTo(
                                        lengthCenter - cutLength,
                                        depthCenter - cutDepth,
                                        lengthCenter,
                                        depthCenter,
                                        90.0F,
                                        -90.0F,
                                        false
                                    )

                                }
                                CornerCutType.OVAL_INVERSE -> {
                                    childCornerCutPath.arcTo(
                                        lengthCenter - halfCutLength,
                                        depthCenter - halfCutDepth,
                                        lengthCenter + halfCutLength,
                                        depthCenter + halfCutDepth,
                                        90.0F,
                                        180.0F,
                                        false
                                    )
                                }
                                CornerCutType.BEVEL -> {
                                    childCornerCutPath.lineTo(
                                        lengthCenter - halfCutLength,
                                        depthCenter
                                    )
                                    childCornerCutPath.lineTo(
                                        lengthCenter,
                                        depthCenter - halfCutDepth
                                    )
                                }
                                CornerCutType.RECTANGLE -> {
                                    val radiusDepth =
                                        childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                            cutDepth
                                        )
                                    val radiusLength =
                                        childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                            cutLength
                                        )
                                    if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + radiusDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter - radiusLength * 2.0F,
                                            depthCenter,
                                            lengthCenter,
                                            depthCenter + radiusDepth * 2.0F,
                                            0.0F,
                                            -90.0F,
                                            false
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter - radiusLength * 2.0F,
                                            depthCenter - radiusDepth * 2.0F,
                                            lengthCenter,
                                            depthCenter,
                                            90.0F,
                                            -90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - halfCutDepth
                                        )
                                    } else {
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - halfCutDepth
                                        )
                                    }
                                }
                                CornerCutType.RECTANGLE_INVERSE -> {
                                    val radiusDepth =
                                        childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                            cutDepth
                                        )
                                    val radiusLength =
                                        childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                            cutLength
                                        )
                                    if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                        childCornerCutPath.lineTo(
                                            lengthCenter - halfCutLength + radiusLength,
                                            depthCenter + halfCutDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter + halfCutDepth - radiusDepth * 2.0F,
                                            lengthCenter - halfCutLength + radiusLength * 2.0F,
                                            depthCenter + halfCutDepth,
                                            90.0F,
                                            90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter - halfCutDepth + radiusDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter - halfCutDepth,
                                            lengthCenter - halfCutLength + radiusLength * 2.0F,
                                            depthCenter - halfCutDepth + radiusDepth * 2.0F,
                                            180.0F,
                                            90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - halfCutDepth
                                        )
                                    } else {
                                        childCornerCutPath.lineTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter + halfCutDepth
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter - halfCutDepth
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - halfCutDepth
                                        )
                                    }
                                }
                            }
                        } else {
                            // copy from ltr - just do same logic for topPartCornerCutType in rtl as for bottomPartCornerCutType in ltr and vice versa
                            when (topPartCornerCutType) {
                                CornerCutType.OVAL -> {
                                    childCornerCutPath.arcTo(
                                        lengthCenter,
                                        depthCenter - cutDepth,
                                        lengthCenter + cutLength,
                                        depthCenter,
                                        180.0F,
                                        -90.0F,
                                        false
                                    )
                                    childCornerCutPath.arcTo(
                                        lengthCenter,
                                        depthCenter,
                                        lengthCenter + cutLength,
                                        depthCenter + cutDepth,
                                        270.0F,
                                        -90.0F,
                                        false
                                    )
                                }
                                CornerCutType.OVAL_INVERSE -> {
                                    childCornerCutPath.arcTo(
                                        lengthCenter - halfCutLength,
                                        depthCenter - halfCutDepth,
                                        lengthCenter + halfCutLength,
                                        depthCenter + halfCutDepth,
                                        270.0F,
                                        180.0F,
                                        false
                                    )
                                }
                                CornerCutType.BEVEL -> {
                                    childCornerCutPath.lineTo(
                                        lengthCenter + halfCutLength,
                                        depthCenter
                                    )
                                    childCornerCutPath.lineTo(
                                        lengthCenter,
                                        depthCenter + halfCutDepth
                                    )
                                }
                                CornerCutType.RECTANGLE -> {
                                    val radiusDepth =
                                        childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                            cutDepth
                                        )
                                    val radiusLength =
                                        childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                            cutLength
                                        )
                                    if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - radiusDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter,
                                            depthCenter - radiusDepth * 2.0F,
                                            lengthCenter + radiusLength * 2.0F,
                                            depthCenter,
                                            180.0F,
                                            -90.0F,
                                            false
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter,
                                            depthCenter,
                                            lengthCenter + radiusLength * 2.0F,
                                            depthCenter + radiusDepth * 2.0F,
                                            270.0F,
                                            -90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + halfCutDepth
                                        )
                                    } else {
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + halfCutDepth
                                        )
                                    }
                                }
                                CornerCutType.RECTANGLE_INVERSE -> {
                                    val radiusDepth =
                                        childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                            cutDepth
                                        )
                                    val radiusLength =
                                        childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                            cutLength
                                        )
                                    if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                        childCornerCutPath.lineTo(
                                            lengthCenter + halfCutLength - radiusLength,
                                            depthCenter - halfCutDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter + halfCutLength - radiusLength * 2.0F,
                                            depthCenter - halfCutDepth,
                                            lengthCenter + halfCutLength,
                                            depthCenter - halfCutDepth + radiusDepth * 2.0F,
                                            270.0F,
                                            90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter + halfCutLength,
                                            depthCenter + halfCutDepth - radiusDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter + halfCutLength - radiusLength * 2.0F,
                                            depthCenter + halfCutDepth - radiusDepth * 2.0F,
                                            lengthCenter + halfCutLength,
                                            depthCenter + halfCutDepth,
                                            0.0F,
                                            90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + halfCutDepth
                                        )
                                    } else {
                                        childCornerCutPath.lineTo(
                                            lengthCenter + halfCutLength,
                                            depthCenter - halfCutDepth
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter + halfCutLength,
                                            depthCenter + halfCutDepth
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + halfCutDepth
                                        )
                                    }
                                }
                            }
                            when (bottomPartCornerCutType) {
                                CornerCutType.OVAL -> {
                                    childCornerCutPath.arcTo(
                                        lengthCenter - cutLength,
                                        depthCenter,
                                        lengthCenter,
                                        depthCenter + cutDepth,
                                        0.0F,
                                        -90.0F,
                                        false
                                    )
                                    childCornerCutPath.arcTo(
                                        lengthCenter - cutLength,
                                        depthCenter - cutDepth,
                                        lengthCenter,
                                        depthCenter,
                                        90.0F,
                                        -90.0F,
                                        false
                                    )

                                }
                                CornerCutType.OVAL_INVERSE -> {
                                    childCornerCutPath.arcTo(
                                        lengthCenter - halfCutLength,
                                        depthCenter - halfCutDepth,
                                        lengthCenter + halfCutLength,
                                        depthCenter + halfCutDepth,
                                        90.0F,
                                        180.0F,
                                        false
                                    )
                                }
                                CornerCutType.BEVEL -> {
                                    childCornerCutPath.lineTo(
                                        lengthCenter - halfCutLength,
                                        depthCenter
                                    )
                                    childCornerCutPath.lineTo(
                                        lengthCenter,
                                        depthCenter - halfCutDepth
                                    )
                                }
                                CornerCutType.RECTANGLE -> {
                                    val radiusDepth =
                                        childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                            cutDepth
                                        )
                                    val radiusLength =
                                        childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                            cutLength
                                        )
                                    if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter + radiusDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter - radiusLength * 2.0F,
                                            depthCenter,
                                            lengthCenter,
                                            depthCenter + radiusDepth * 2.0F,
                                            0.0F,
                                            -90.0F,
                                            false
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter - radiusLength * 2.0F,
                                            depthCenter - radiusDepth * 2.0F,
                                            lengthCenter,
                                            depthCenter,
                                            90.0F,
                                            -90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - halfCutDepth
                                        )
                                    } else {
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - halfCutDepth
                                        )
                                    }
                                }
                                CornerCutType.RECTANGLE_INVERSE -> {
                                    val radiusDepth =
                                        childRectangleTypeCornerCutRoundCornerRadiusDepth.coerceAtMost(
                                            cutDepth
                                        )
                                    val radiusLength =
                                        childRectangleTypeCornerCutRoundCornerRadiusLength.coerceAtMost(
                                            cutLength
                                        )
                                    if (radiusDepth > 0.0F && radiusLength > 0.0F) {
                                        childCornerCutPath.lineTo(
                                            lengthCenter - halfCutLength + radiusLength,
                                            depthCenter + halfCutDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter + halfCutDepth - radiusDepth * 2.0F,
                                            lengthCenter - halfCutLength + radiusLength * 2.0F,
                                            depthCenter + halfCutDepth,
                                            90.0F,
                                            90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter - halfCutDepth + radiusDepth
                                        )
                                        childCornerCutPath.arcTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter - halfCutDepth,
                                            lengthCenter - halfCutLength + radiusLength * 2.0F,
                                            depthCenter - halfCutDepth + radiusDepth * 2.0F,
                                            180.0F,
                                            90.0F,
                                            false
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - halfCutDepth
                                        )
                                    } else {
                                        childCornerCutPath.lineTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter + halfCutDepth
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter - halfCutLength,
                                            depthCenter - halfCutDepth
                                        )
                                        childCornerCutPath.lineTo(
                                            lengthCenter,
                                            depthCenter - halfCutDepth
                                        )
                                    }
                                }
                            }
                        }

                        if (rotation != 0.0F) {
                            val matrix = Matrix()
                            matrix.postRotate(-rotation, depthCenter, lengthCenter)
                            childCornerCutPath.transform(matrix)
                        }
                    }
                }
            }
            //endregion
            //endregion
        }

        //region Top Corners

        val leftTopFlag = if (isLtr) START_TOP else END_TOP
        val rightTopFlag = if (isLtr) END_TOP else START_TOP
        val rightBottomFlag = if (isLtr) END_BOTTOM else START_BOTTOM
        val leftBottomFlag = if (isLtr) START_BOTTOM else END_BOTTOM

        //region Left Top Corner
        if (leftTopX > 0.0F && leftTopY > 0.0F && cornerCutFlag containsFlag leftTopFlag) {

            tempCustomCutPath.rewind()
            tempRectF.set(
                paddedViewLeft,
                paddedViewTop,
                paddedViewLeft + leftTopX,
                paddedViewTop + leftTopY
            )
            if (cornerCutProvider?.getCornerCut(
                    this@CornerCutLinearLayout,
                    tempCustomCutPath,
                    leftTopFlag,
                    tempRectF
                ) == true
            ) {
                cornerCutProvider?.getPathTransformationMatrix(
                    this@CornerCutLinearLayout,
                    tempCustomCutPath,
                    leftTopFlag,
                    tempRectF
                )?.apply {
                    tempCustomCutPath.transform(this)
                }
                tempCustomCutPath.close()
                viewCornerCutPath.addPath(tempCustomCutPath)
            } else {
                when (leftTopCornerCutType) {
                    CornerCutType.OVAL -> {
                        viewCornerCutPath.moveTo(paddedViewLeft, paddedViewTop)
                        viewCornerCutPath.lineTo(paddedViewLeft, paddedViewTop + leftTopY)
                        viewCornerCutPath.arcTo(
                            paddedViewLeft,
                            paddedViewTop,
                            paddedViewLeft + leftTopX * 2.0F,
                            paddedViewTop + leftTopY * 2.0F,
                            180.0F,
                            90.0F,
                            false
                        )
                        viewCornerCutPath.lineTo(paddedViewLeft, paddedViewTop)
                    }

                    CornerCutType.OVAL_INVERSE -> {
                        viewCornerCutPath.addOval(
                            paddedViewLeft - leftTopX,
                            paddedViewTop - leftTopY,
                            paddedViewLeft + leftTopX,
                            paddedViewTop + leftTopY,
                            Path.Direction.CW
                        )
                    }

                    CornerCutType.BEVEL -> {
                        viewCornerCutPath.moveTo(paddedViewLeft + leftTopX, paddedViewTop)
                        viewCornerCutPath.lineTo(paddedViewLeft, paddedViewTop + leftTopY)
                        viewCornerCutPath.lineTo(paddedViewLeft, paddedViewTop)
                        viewCornerCutPath.lineTo(paddedViewLeft + leftTopX, paddedViewTop)
                    }

                    CornerCutType.RECTANGLE -> {
                        val cornerRadiusX =
                            rectangleTypeCornerCutRoundCornerRadiusX.coerceAtMost(leftTopX)
                        val cornerRadiusY =
                            rectangleTypeCornerCutRoundCornerRadiusY.coerceAtMost(leftTopY)
                        if (cornerRadiusX > 0.0F && cornerRadiusY > 0.0F) {
                            viewCornerCutPath.moveTo(paddedViewLeft, paddedViewTop)
                            viewCornerCutPath.lineTo(
                                paddedViewLeft,
                                paddedViewTop + cornerRadiusY
                            )
                            viewCornerCutPath.arcTo(
                                paddedViewLeft,
                                paddedViewTop,
                                paddedViewLeft + cornerRadiusX * 2.0F,
                                paddedViewTop + cornerRadiusY * 2.0F,
                                180.0F,
                                90.0F,
                                false
                            )
                            viewCornerCutPath.lineTo(paddedViewLeft, paddedViewTop)
                        }
                    }

                    CornerCutType.RECTANGLE_INVERSE -> {
                        viewCornerCutPath.addRoundRect(
                            paddedViewLeft - leftTopX,
                            paddedViewTop - leftTopY,
                            paddedViewLeft + leftTopX,
                            paddedViewTop + leftTopY,
                            rectangleTypeCornerCutRoundCornerRadiusX.coerceAtMost(leftTopX),
                            rectangleTypeCornerCutRoundCornerRadiusY.coerceAtMost(leftTopY),
                            Path.Direction.CW
                        )
                    }
                }
            }
        }
        //endregion

        //region Right Top Corner
        if (rightTopX > 0.0F && rightTopY > 0.0F && cornerCutFlag containsFlag rightTopFlag) {

            tempCustomCutPath.rewind()
            tempRectF.set(
                paddedViewRight - rightTopX,
                paddedViewTop,
                paddedViewRight,
                paddedViewTop + rightTopY
            )
            if (cornerCutProvider?.getCornerCut(
                    this@CornerCutLinearLayout,
                    tempCustomCutPath,
                    rightTopFlag,
                    tempRectF
                ) == true
            ) {
                cornerCutProvider?.getPathTransformationMatrix(
                    this@CornerCutLinearLayout,
                    tempCustomCutPath,
                    rightTopFlag,
                    tempRectF
                )?.apply {
                    tempCustomCutPath.transform(this)
                }
                tempCustomCutPath.close()
                viewCornerCutPath.addPath(tempCustomCutPath)
            } else {
                when (rightTopCornerCutType) {
                    CornerCutType.OVAL -> {
                        viewCornerCutPath.moveTo(paddedViewRight - rightTopX, paddedViewTop)
                        viewCornerCutPath.arcTo(
                            paddedViewRight - rightTopX * 2.0F,
                            paddedViewTop,
                            paddedViewRight,
                            paddedViewTop + rightTopY * 2.0F,
                            270.0F,
                            90.0F,
                            false
                        )
                        viewCornerCutPath.lineTo(paddedViewRight, paddedViewTop)
                        viewCornerCutPath.moveTo(paddedViewRight - rightTopX, paddedViewTop)
                    }

                    CornerCutType.OVAL_INVERSE -> {
                        viewCornerCutPath.addOval(
                            paddedViewRight - rightTopX,
                            paddedViewTop - rightTopY,
                            paddedViewRight + rightTopX,
                            paddedViewTop + rightTopY,
                            Path.Direction.CW
                        )
                    }

                    CornerCutType.BEVEL -> {
                        viewCornerCutPath.moveTo(paddedViewRight - rightTopX, paddedViewTop)
                        viewCornerCutPath.lineTo(paddedViewRight, paddedViewTop + rightTopY)
                        viewCornerCutPath.lineTo(paddedViewRight, paddedViewTop)
                        viewCornerCutPath.lineTo(paddedViewRight - rightTopX, paddedViewTop)
                    }

                    CornerCutType.RECTANGLE -> {
                        val cornerRadiusX =
                            rectangleTypeCornerCutRoundCornerRadiusX.coerceAtMost(rightTopX)
                        val cornerRadiusY =
                            rectangleTypeCornerCutRoundCornerRadiusY.coerceAtMost(rightTopY)
                        if (cornerRadiusX > 0.0F && cornerRadiusY > 0.0F) {
                            viewCornerCutPath.moveTo(
                                paddedViewRight - cornerRadiusX,
                                paddedViewTop
                            )
                            viewCornerCutPath.arcTo(
                                paddedViewRight - cornerRadiusX * 2.0F,
                                paddedViewTop,
                                paddedViewRight,
                                paddedViewTop + cornerRadiusY * 2.0F,
                                270.0F,
                                90.0F,
                                false
                            )
                            viewCornerCutPath.lineTo(paddedViewRight, paddedViewTop)
                            viewCornerCutPath.moveTo(
                                paddedViewRight - cornerRadiusX,
                                paddedViewTop
                            )
                        }
                    }

                    CornerCutType.RECTANGLE_INVERSE -> {
                        viewCornerCutPath.addRoundRect(
                            paddedViewRight - rightTopX,
                            paddedViewTop - rightTopX,
                            paddedViewRight + rightTopY,
                            paddedViewTop + rightTopX,
                            rectangleTypeCornerCutRoundCornerRadiusX.coerceAtMost(rightTopX),
                            rectangleTypeCornerCutRoundCornerRadiusY.coerceAtMost(rightTopY),
                            Path.Direction.CW
                        )
                    }
                }
            }
        }
        //endregion
        //endregion

        //region Bottom Corners
        //region Right Bottom Corner
        if (rightBottomX > 0.0F && rightBottomY > 0.0F && cornerCutFlag containsFlag rightBottomFlag) {
            tempCustomCutPath.rewind()
            tempRectF.set(
                paddedViewRight - rightBottomX,
                paddedViewBottom - rightBottomY,
                paddedViewRight,
                paddedViewBottom
            )
            if (cornerCutProvider?.getCornerCut(
                    this@CornerCutLinearLayout,
                    tempCustomCutPath,
                    rightBottomFlag,
                    tempRectF
                ) == true
            ) {
                cornerCutProvider?.getPathTransformationMatrix(
                    this@CornerCutLinearLayout,
                    tempCustomCutPath,
                    rightBottomFlag,
                    tempRectF
                )?.apply {
                    tempCustomCutPath.transform(this)
                }
                tempCustomCutPath.close()
                viewCornerCutPath.addPath(tempCustomCutPath)
            } else {
                when (rightBottomCornerCutType) {
                    CornerCutType.OVAL -> {
                        viewCornerCutPath.moveTo(paddedViewRight, paddedViewBottom - rightBottomY)
                        viewCornerCutPath.arcTo(
                            paddedViewRight - rightBottomX * 2.0F,
                            paddedViewBottom - rightBottomY * 2.0F,
                            paddedViewRight,
                            paddedViewBottom,
                            0.0F,
                            90.0F,
                            false
                        )
                        viewCornerCutPath.lineTo(paddedViewRight, paddedViewBottom)
                        viewCornerCutPath.lineTo(paddedViewRight, paddedViewBottom - rightBottomY)
                    }

                    CornerCutType.OVAL_INVERSE -> {
                        viewCornerCutPath.addOval(
                            paddedViewRight - rightBottomX,
                            paddedViewBottom - rightBottomY,
                            paddedViewRight + rightBottomX,
                            paddedViewBottom + rightBottomY,
                            Path.Direction.CW
                        )
                    }

                    CornerCutType.BEVEL -> {
                        viewCornerCutPath.moveTo(paddedViewRight - rightBottomX, paddedViewBottom)
                        viewCornerCutPath.lineTo(paddedViewRight, paddedViewBottom - rightBottomY)
                        viewCornerCutPath.lineTo(paddedViewRight, paddedViewBottom)
                        viewCornerCutPath.lineTo(paddedViewRight - rightBottomX, paddedViewBottom)
                    }

                    CornerCutType.RECTANGLE -> {
                        val cornerRadiusX =
                            rectangleTypeCornerCutRoundCornerRadiusX.coerceAtMost(rightBottomX)
                        val cornerRadiusY =
                            rectangleTypeCornerCutRoundCornerRadiusY.coerceAtMost(rightBottomY)
                        if (cornerRadiusX > 0.0F && cornerRadiusY > 0.0F) {
                            viewCornerCutPath.moveTo(
                                paddedViewRight,
                                paddedViewBottom - cornerRadiusY
                            )
                            viewCornerCutPath.arcTo(
                                paddedViewRight - cornerRadiusX * 2.0F,
                                paddedViewBottom - cornerRadiusY * 2.0F,
                                paddedViewRight,
                                paddedViewBottom,
                                0.0F,
                                90.0F,
                                false
                            )
                            viewCornerCutPath.lineTo(paddedViewRight, paddedViewBottom)
                            viewCornerCutPath.lineTo(
                                paddedViewRight,
                                paddedViewBottom - cornerRadiusY
                            )
                        }
                    }

                    CornerCutType.RECTANGLE_INVERSE -> {
                        viewCornerCutPath.addRoundRect(
                            paddedViewRight - rightBottomX,
                            paddedViewBottom - rightBottomY,
                            paddedViewRight + rightBottomX,
                            paddedViewBottom + rightBottomY,
                            rectangleTypeCornerCutRoundCornerRadiusX.coerceAtMost(rightBottomX),
                            rectangleTypeCornerCutRoundCornerRadiusY.coerceAtMost(rightBottomY),
                            Path.Direction.CW
                        )
                    }
                }
            }
        }
        //endregion

        //region Left Bottom Corner
        if (leftBottomX > 0.0F && leftBottomY > 0.0F && cornerCutFlag containsFlag leftBottomFlag) {

            tempCustomCutPath.rewind()
            tempRectF.set(
                paddedViewLeft,
                paddedViewBottom - leftBottomY,
                paddedViewLeft + leftBottomX,
                paddedViewBottom
            )
            if (cornerCutProvider?.getCornerCut(
                    this@CornerCutLinearLayout,
                    tempCustomCutPath,
                    leftBottomFlag,
                    tempRectF
                ) == true
            ) {
                cornerCutProvider?.getPathTransformationMatrix(
                    this@CornerCutLinearLayout,
                    tempCustomCutPath,
                    leftBottomFlag,
                    tempRectF
                )?.apply {
                    tempCustomCutPath.transform(this)
                }
                tempCustomCutPath.close()
                viewCornerCutPath.addPath(tempCustomCutPath)
            } else {
                when (leftBottomCornerCutType) {
                    CornerCutType.OVAL -> {
                        viewCornerCutPath.moveTo(paddedViewLeft + leftBottomX, paddedViewBottom)
                        viewCornerCutPath.arcTo(
                            paddedViewLeft,
                            paddedViewBottom - leftBottomY * 2.0F,
                            paddedViewLeft + leftBottomX * 2.0F,
                            paddedViewBottom,
                            90.0F,
                            90.0F,
                            false
                        )
                        viewCornerCutPath.lineTo(paddedViewLeft, paddedViewBottom)
                        viewCornerCutPath.lineTo(paddedViewLeft + leftBottomX, paddedViewBottom)
                    }

                    CornerCutType.OVAL_INVERSE -> {
                        viewCornerCutPath.addOval(
                            paddedViewLeft - leftBottomX,
                            paddedViewBottom - leftBottomY,
                            paddedViewLeft + leftBottomX,
                            paddedViewBottom + leftBottomY,
                            Path.Direction.CW
                        )
                    }

                    CornerCutType.BEVEL -> {
                        viewCornerCutPath.moveTo(paddedViewLeft + leftBottomX, paddedViewBottom)
                        viewCornerCutPath.lineTo(paddedViewLeft, paddedViewBottom)
                        viewCornerCutPath.lineTo(paddedViewLeft, paddedViewBottom - leftBottomY)
                        viewCornerCutPath.lineTo(paddedViewLeft + leftBottomX, paddedViewBottom)
                    }

                    CornerCutType.RECTANGLE -> {
                        val cornerRadiusX =
                            rectangleTypeCornerCutRoundCornerRadiusX.coerceAtMost(leftBottomX)
                        val cornerRadiusY =
                            rectangleTypeCornerCutRoundCornerRadiusY.coerceAtMost(leftBottomY)
                        if (cornerRadiusX > 0.0F && cornerRadiusY > 0.0F) {
                            viewCornerCutPath.moveTo(
                                paddedViewLeft + cornerRadiusX,
                                paddedViewBottom
                            )
                            viewCornerCutPath.arcTo(
                                paddedViewLeft,
                                paddedViewBottom - cornerRadiusY * 2.0F,
                                paddedViewLeft + cornerRadiusX * 2.0F,
                                paddedViewBottom,
                                90.0F,
                                90.0F,
                                false
                            )
                            viewCornerCutPath.lineTo(paddedViewLeft, paddedViewBottom)
                            viewCornerCutPath.lineTo(
                                paddedViewLeft + cornerRadiusX,
                                paddedViewBottom
                            )
                        }
                    }

                    CornerCutType.RECTANGLE_INVERSE -> {
                        viewCornerCutPath.addRoundRect(
                            paddedViewLeft - leftBottomX,
                            paddedViewBottom - leftBottomY,
                            paddedViewLeft + leftBottomX,
                            paddedViewBottom + leftBottomY,
                            rectangleTypeCornerCutRoundCornerRadiusX.coerceAtMost(leftBottomX),
                            rectangleTypeCornerCutRoundCornerRadiusY.coerceAtMost(leftBottomY),
                            Path.Direction.CW
                        )
                    }
                }
            }
        }
        //endregion
        //endregion

        //region Custom Cutouts
        customCutoutProviders.forEach {
            val tempCustomCutPath = Path()
            tempCustomCutPath.rewind()
            tempRectF.set(paddedBoundsF)
            it.getCutout(this@CornerCutLinearLayout, tempCustomCutPath, tempRectF)
            it.getPathTransformationMatrix(this@CornerCutLinearLayout, tempCustomCutPath, tempRectF)
                ?.apply {
                    tempCustomCutPath.transform(this)
                }
            tempCustomCutPath.close()
            customCutoutsPath.addPath(tempCustomCutPath)
        }
        //endregion

        //region Compose All Cutouts
        viewCornerCutPath.close()
        composedCornerCutPath.op(viewCornerCutPath, Path.Op.DIFFERENCE)
        childCornerCutPath.close()
        composedCornerCutPath.op(childCornerCutPath, Path.Op.DIFFERENCE)
        customCutoutsPath.close()
        composedCornerCutPath.op(customCutoutsPath, Path.Op.DIFFERENCE)

        composedCornerCutPath.close()
        //endregion

        //region Custom Shadow Bitmap
        if (customShadowRadius > 0.0F) {
            customShadowBitmap?.recycle()
            customShadowBitmap = Bitmap.createBitmap(
                width.coerceAtLeast(1),
                height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            customShadowCanvas.setBitmap(customShadowBitmap)
            setLayerType(View.LAYER_TYPE_SOFTWARE, customShadowPaint)
            customShadowCanvas.drawPath(composedCornerCutPath, customShadowPaint)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        //endregion

        invalidate()
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        userDefinedPadding[0] = left
        userDefinedPadding[1] = top
        userDefinedPadding[2] = right
        userDefinedPadding[3] = bottom
        invalidateComposedPadding()
        super.setPadding(
            composedPadding[0],
            composedPadding[1],
            composedPadding[2],
            composedPadding[3]
        )
    }

    override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
        userDefinedPadding[0] = start
        userDefinedPadding[1] = top
        userDefinedPadding[2] = end
        userDefinedPadding[3] = bottom
        invalidateComposedPadding()
        super.setPaddingRelative(
            composedPadding[0],
            composedPadding[1],
            composedPadding[2],
            composedPadding[3]
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        paddedBoundsF.set(
            paddingLeft.toFloat(),
            paddingTop.toFloat(),
            w.toFloat() - paddingRight,
            h.toFloat() - paddingBottom
        )

        if (paddedBoundsF.left > paddedBoundsF.right) paddedBoundsF.left = paddedBoundsF.right
        if (paddedBoundsF.top > paddedBoundsF.bottom) paddedBoundsF.top = paddedBoundsF.bottom

        invalidateComposedPadding()
        invalidateCornerCutPath()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        invalidateComposedPadding()
        invalidateCornerCutPath()
    }

    override fun dispatchDraw(canvas: Canvas?) {
        super.dispatchDraw(canvas)
        //region Custom Divider
        val isLtr = isLtr

            var dividerDrawStartX: Float = -1.0F
            var dividerDrawEndX: Float = -1.0F
            var dividerCenterX = 0.0F
            var isDashedCenterGravity = false
            var centerGravityDashedLineStartPartStartX = 0.0F
            var centerGravityDashedLineEndPartStartX = 0.0F

            if (orientation == VERTICAL) {
                val dividerStart = if (isLtr) paddedBoundsF.left else paddedBoundsF.right
                val dividerEnd = if (isLtr) paddedBoundsF.right else paddedBoundsF.left
                val customDividerPaddingStart =
                    if (isLtr) customDividerPaddingStart else -customDividerPaddingStart
                val customDividerPaddingEnd =
                    if (isLtr) -customDividerPaddingEnd else customDividerPaddingEnd

                when (customCustomDividerGravity) {
                    CustomDividerGravity.CENTER -> {
                        dividerDrawStartX = dividerStart + customDividerPaddingStart
                        dividerDrawEndX = dividerEnd + customDividerPaddingEnd
                        val customDividerDashOffset =
                            (customDividerDashWidth / 2.0F + customDividerDashGap).let { if (isLtr) -it else it }

                        if (customDividerDashGap > 0.0F && customDividerDashWidth > 0.0F) {
                            isDashedCenterGravity = true
                            dividerCenterX = dividerDrawStartX + (dividerDrawEndX - dividerDrawStartX) / 2.0F
                            centerGravityDashedLineStartPartStartX = when {
                                isLtr -> (dividerCenterX + customDividerDashOffset).coerceAtLeast(
                                    dividerDrawStartX
                                )
                                else -> (dividerCenterX + customDividerDashOffset).coerceAtMost(
                                    dividerDrawStartX
                                )
                            }
                            centerGravityDashedLineEndPartStartX = when {
                                isLtr -> (dividerCenterX - customDividerDashOffset).coerceAtMost(
                                    dividerDrawEndX
                                )
                                else -> (dividerCenterX - customDividerDashOffset).coerceAtLeast(
                                    dividerDrawEndX
                                )
                            }
                        }
                    }

                    CustomDividerGravity.START -> {
                        dividerDrawStartX = dividerStart + customDividerPaddingStart
                        dividerDrawEndX = dividerEnd + customDividerPaddingEnd
                    }

                    CustomDividerGravity.END -> {
                        dividerDrawStartX = dividerEnd + customDividerPaddingEnd
                        dividerDrawEndX = dividerStart + customDividerPaddingStart
                    }
                }

                customDividerPoints.forEachIndexed { index, centerY ->

                    customDividerProviderPath.rewind()
                    tempRectF.set(
                        min(dividerDrawStartX, dividerDrawEndX),
                        centerY,
                        max(dividerDrawStartX, dividerDrawEndX),
                        centerY
                    )
                    if (customDividerProvider?.getDivider(
                        this@CornerCutLinearLayout,
                        customDividerProviderPath,
                            customDividerProviderPaint,
                        customDividerTypes[index],
                        customDividerTypedIndexes[index],
                        tempRectF
                    ) == true) {
                        canvas?.drawPath(customDividerProviderPath, customDividerProviderPaint)
                    } else if (customDividerHeight > 0.0F) {
                        if (isDashedCenterGravity) {
                            customDividerPath.rewind()
                            //center dash line
                            customDividerPath.moveTo(
                                dividerCenterX - customDividerDashWidth / 2.0F,
                                centerY
                            )
                            customDividerPath.lineTo(
                                dividerCenterX + customDividerDashWidth / 2.0F,
                                centerY
                            )
                            canvas?.drawPath(customDividerPath, customDividerPaint)

                            //left dashed line
                            customDividerPath.moveTo(
                                centerGravityDashedLineStartPartStartX,
                                centerY
                            )
                            customDividerPath.lineTo(dividerDrawStartX, centerY)
                            canvas?.drawPath(customDividerPath, customDividerPaint)

                            //right dashed line
                            customDividerPath.moveTo(centerGravityDashedLineEndPartStartX, centerY)
                            customDividerPath.lineTo(dividerDrawEndX, centerY)
                            canvas?.drawPath(customDividerPath, customDividerPaint)
                        } else {
                            customDividerPath.rewind()
                            customDividerPath.moveTo(dividerDrawStartX, centerY)
                            customDividerPath.lineTo(dividerDrawEndX, centerY)
                            canvas?.drawPath(customDividerPath, customDividerPaint)
                        }
                    }
                }
            } else {
                // Horizontal

                val dividerStart = if (isLtr) paddedBoundsF.bottom else paddedBoundsF.top
                val dividerEnd = if (isLtr) paddedBoundsF.top else paddedBoundsF.bottom
                val customDividerPaddingStart =
                    if (isLtr) -customDividerPaddingStart else customDividerPaddingStart
                val customDividerPaddingEnd =
                    if (isLtr) customDividerPaddingEnd else -customDividerPaddingEnd

                when (customCustomDividerGravity) {
                    CustomDividerGravity.CENTER -> {
                        dividerDrawStartX = dividerStart + customDividerPaddingStart
                        dividerDrawEndX = dividerEnd + customDividerPaddingEnd
                        val customDividerDashOffset =
                            (customDividerDashWidth / 2.0F + customDividerDashGap).let { if (isLtr) it else -it }

                        if (customDividerDashGap > 0.0F && customDividerDashWidth > 0.0F) {
                            isDashedCenterGravity = true
                            dividerCenterX = dividerDrawStartX + (dividerDrawEndX - dividerDrawStartX) / 2.0F
                            centerGravityDashedLineStartPartStartX = when {
                                isLtr -> (dividerCenterX + customDividerDashOffset).coerceAtMost(
                                    dividerDrawStartX
                                )
                                else -> (dividerCenterX + customDividerDashOffset).coerceAtLeast(
                                    dividerDrawStartX
                                )
                            }
                            centerGravityDashedLineEndPartStartX = when {
                                isLtr -> (dividerCenterX - customDividerDashOffset).coerceAtLeast(
                                    dividerDrawEndX
                                )
                                else -> (dividerCenterX - customDividerDashOffset).coerceAtMost(
                                    dividerDrawEndX
                                )
                            }
                        }
                    }

                    CustomDividerGravity.START -> {
                        dividerDrawStartX = dividerStart + customDividerPaddingStart
                        dividerDrawEndX = dividerEnd + customDividerPaddingEnd
                    }

                    CustomDividerGravity.END -> {
                        dividerDrawStartX = dividerEnd + customDividerPaddingEnd
                        dividerDrawEndX = dividerStart + customDividerPaddingStart
                    }
                }

                customDividerPoints.forEachIndexed { index, centerX ->
                    customDividerProviderPath.rewind()
                    tempRectF.set(
                        centerX,
                        min(dividerDrawStartX, dividerDrawEndX),
                        centerX,
                        max(dividerDrawStartX, dividerDrawEndX)
                    )
                    if (customDividerProvider?.getDivider(
                            this@CornerCutLinearLayout,
                            customDividerProviderPath,
                            customDividerProviderPaint,
                            customDividerTypes[index],
                            customDividerTypedIndexes[index],
                            tempRectF
                        ) == true) {
                        canvas?.drawPath(customDividerProviderPath, customDividerProviderPaint)
                    } else if (customDividerHeight > 0.0F) {
                        if (isDashedCenterGravity) {
                            customDividerPath.rewind()
                            //center dash line
                            customDividerPath.moveTo(
                                centerX,
                                dividerCenterX + customDividerDashWidth / 2.0F
                            )
                            customDividerPath.lineTo(
                                centerX,
                                dividerCenterX - customDividerDashWidth / 2.0F
                            )
                            canvas?.drawPath(customDividerPath, customDividerPaint)

                            //left dashed line
                            customDividerPath.moveTo(centerX, centerGravityDashedLineStartPartStartX)
                            customDividerPath.lineTo(centerX, dividerDrawStartX)
                            canvas?.drawPath(customDividerPath, customDividerPaint)

                            //right dashed line
                            customDividerPath.moveTo(centerX, centerGravityDashedLineEndPartStartX)
                            customDividerPath.lineTo(centerX, dividerDrawEndX)
                            canvas?.drawPath(customDividerPath, customDividerPaint)
                        } else {
                            customDividerPath.rewind()
                            customDividerPath.moveTo(centerX, dividerDrawStartX)
                            customDividerPath.lineTo(centerX, dividerDrawEndX)
                            canvas?.drawPath(customDividerPath, customDividerPaint)
                        }
                    }
                }
            }
        //endregion
    }

    override fun draw(canvas: Canvas) {
        if (customShadowRadius > 0.0F) {
            customShadowBitmap?.apply { canvas.drawBitmap(this, 0.0F, 0.0F, null) }
        }

        @Suppress("DEPRECATION")
        canvas.clipPath(composedCornerCutPath, Region.Op.INTERSECT)
        super.draw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (userDefinedPadding.any { it == UNDEFINED }) {
            userDefinedPadding[0] = paddingStart
            userDefinedPadding[1] = paddingTop
            userDefinedPadding[2] = paddingEnd
            userDefinedPadding[3] = paddingBottom
            invalidateComposedPadding()
            super.setPadding(
                composedPadding[0],
                composedPadding[1],
                composedPadding[2],
                composedPadding[3]
            )
        }
        doOnNonNullSizeLayout { invalidateCornerCutPath() }
    }

    //region Layout Params
    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean {
        return p is LayoutParams
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        return this.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return LayoutParams(context, attrs)
    }

    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): LayoutParams {
        return LayoutParams(p)
    }

    inner class LayoutParams : LinearLayout.LayoutParams {

        /**
         * Edge child means first or last child in parent view group.
         * There is also specific attributes for particular case only, that is when first or last child
         * aligned to padded view top or bottom ([LinearLayout.VERTICAL])
         * and start or end ([LinearLayout.HORIZONTAL]), respectively.
         *
         * Note that edge child specific attributes would be ignored for non edge children.
         */
        //region Edge Child Override Cut Types
        /**
         * Whether one of [leftTopCornerCutType]
         * or [rightTopCornerCutType]
         * or [rightBottomCornerCutType]
         * or [leftBottomCornerCutType] could override respective global view [CornerCutType]
         * ([CornerCutLinearLayout.leftTopCornerCutType],
         * [CornerCutLinearLayout.rightTopCornerCutType],
         * [CornerCutLinearLayout.rightBottomCornerCutType],
         * [CornerCutLinearLayout.leftBottomCornerCutType])
         * if aligned.
         *
         * Applicable for only first or last child in [CornerCutLinearLayout] when they strictly aligned top parent's edge.
         * For instance it would work for [LinearLayout.VERTICAL] when first child top (y) would be equal to this [CornerCutLinearLayout] padding top
         * Default is false.
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_could_override_parent_corner_cut_type_if_edge_aligned]
         */
        var couldEdgeChildOverrideParentCornerCutTypeIfEdgeAligned by Delegate(false)

        /**
         * Left Top edge view's contact [CornerCutType] to override parent respective ([CornerCutLinearLayout]) child corner cut type.
         * Does not depend on this view layout direction.
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_left_top_contact_corner_cut_type]
         */
        var edgeChildParentContactLeftTopChildCornerCutType: CornerCutType?
            set(value) {
                when (isLtr) {
                    true -> edgeChildParentContactStartTopChildCornerCutType = value
                    false -> edgeChildParentContactEndTopChildCornerCutType = value
                }
            }
            get() = if (isLtr) edgeChildParentContactStartTopChildCornerCutType else edgeChildParentContactEndTopChildCornerCutType

        /**
         * Right Top edge view's contact [CornerCutType] to override parent respective ([CornerCutLinearLayout]) child corner cut type.
         * Does not depend on this view layout direction.
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_right_top_contact_corner_cut_type]
         */
        var edgeChildParentContactRightTopChildCornerCutType: CornerCutType?
            set(value) {
                when (isLtr) {
                    true -> edgeChildParentContactEndTopChildCornerCutType = value
                    false -> edgeChildParentContactStartTopChildCornerCutType = value
                }
            }
            get() = if (isLtr) edgeChildParentContactEndTopChildCornerCutType else edgeChildParentContactStartTopChildCornerCutType

        /**
         * Right Bottom edge view's contact [CornerCutType] to override parent respective ([CornerCutLinearLayout]) child corner cut type.
         * Does not depend on this view layout direction.
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_right_bottom_contact_corner_cut_type]
         */
        var edgeChildParentContactRightBottomChildCornerCutType: CornerCutType?
            set(value) {
                when (isLtr) {
                    true -> edgeChildParentContactEndBottomChildCornerCutType = value
                    false -> edgeChildParentContactStartBottomChildCornerCutType = value
                }
            }
            get() = if (isLtr) edgeChildParentContactEndBottomChildCornerCutType else edgeChildParentContactStartBottomChildCornerCutType

        /**
         * Left Bottom edge view's contact [CornerCutType] to override parent respective ([CornerCutLinearLayout]) child corner cut type.
         * Does not depend on this view layout direction.
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_left_bottom_contact_corner_cut_type]
         */
        var edgeChildParentContactLeftBottomChildCornerCutType: CornerCutType?
            set(value) {
                when (isLtr) {
                    true -> edgeChildParentContactStartBottomChildCornerCutType = value
                    false -> edgeChildParentContactEndBottomChildCornerCutType = value
                }
            }
            get() = if (isLtr) edgeChildParentContactStartBottomChildCornerCutType else edgeChildParentContactEndBottomChildCornerCutType

        /**
         * Start Top edge view's contact [CornerCutType] to override parent respective ([CornerCutLinearLayout]) child corner cut type.
         * Depends on this view layout direction.
         * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_start_top_contact_corner_cut_type]
         */
        var edgeChildParentContactStartTopChildCornerCutType: CornerCutType? by NullableDelegate(
            null
        )

        /**
         * End Top edge view's contact [CornerCutType] to override parent respective ([CornerCutLinearLayout]) child corner cut type.
         * Depends on this view layout direction.
         * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_end_top_contact_corner_cut_type]
         */
        var edgeChildParentContactEndTopChildCornerCutType: CornerCutType? by NullableDelegate(
            null
        )

        /**
         * End Bottom edge view's contact [CornerCutType] to override parent respective ([CornerCutLinearLayout]) child corner cut type.
         * Depends on this view layout direction.
         * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_end_bottom_contact_corner_cut_type]
         */
        var edgeChildParentContactEndBottomChildCornerCutType: CornerCutType? by NullableDelegate(
            null
        )

        /**
         * Start Bottom edge view's contact [CornerCutType] to override parent respective ([CornerCutLinearLayout]) child corner cut type.
         * Depends on this view layout direction.
         * @see [LinearLayout.LAYOUT_DIRECTION_LTR] & [LinearLayout.LAYOUT_DIRECTION_RTL].
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_start_bottom_contact_corner_cut_type]
         */
        var edgeChildParentContactStartBottomChildCornerCutType: CornerCutType? by NullableDelegate(
            null
        )
        //endregion

        //region Override Cut Types
        /**
         * Start Top [CornerCutType] to override parent respective ([CornerCutLinearLayout]) child corner cut type.
         * @see [childStartSideCutTypes]
         * @see [childEndSideCutTypes]
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_start_top_corner_cut_type]
         */
        var startTopCornerCutType: CornerCutType? by NullableDelegate(null)

        /**
         * End Top [CornerCutType] to override parent respective ([CornerCutLinearLayout]) child corner cut type.
         * @see [childStartSideCutTypes]
         * @see [childEndSideCutTypes]
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_end_top_corner_cut_type]
         */
        var endTopCornerCutType: CornerCutType? by NullableDelegate(null)

        /**
         * End Bottom [CornerCutType] to override parent respective ([CornerCutLinearLayout]) child corner cut type.
         * @see [childStartSideCutTypes]
         * @see [childEndSideCutTypes]
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_end_bottom_corner_cut_type]
         */
        var endBottomCornerCutType: CornerCutType? by NullableDelegate(null)

        /**
         * Start Bottom [CornerCutType] to override parent respective ([CornerCutLinearLayout]) child corner cut type.
         * @see [childStartSideCutTypes]
         * @see [childEndSideCutTypes]
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_start_bottom_corner_cut_type]
         */
        var startBottomCornerCutType: CornerCutType? by NullableDelegate(null)

        /**
         * Left Top [CornerCutType] to override parent respective ([CornerCutLinearLayout]) child corner cut type.
         * @see [childStartSideCutTypes]
         * @see [childEndSideCutTypes]
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_left_top_corner_cut_type]
         */
        var leftTopCornerCutType: CornerCutType?
            set(value) = when (isLtr) {
                true -> startTopCornerCutType = value
                false -> endTopCornerCutType = value
            }
            get() = when (isLtr) {
                true -> startTopCornerCutType
                false -> endTopCornerCutType
            }

        /**
         * Right Top [CornerCutType] to override parent respective ([CornerCutLinearLayout]) child corner cut type.
         * @see [childStartSideCutTypes]
         * @see [childEndSideCutTypes]
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_right_top_corner_cut_type]
         */
        var rightTopCornerCutType: CornerCutType?
            set(value) = when (isLtr) {
                true -> endTopCornerCutType = value
                false -> startTopCornerCutType = value
            }
            get() = when (isLtr) {
                true -> endTopCornerCutType
                false -> startTopCornerCutType
            }

        /**
         * Right Bottom [CornerCutType] to override parent respective ([CornerCutLinearLayout]) child corner cut type.
         * @see [childStartSideCutTypes]
         * @see [childEndSideCutTypes]
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_right_bottom_corner_cut_type]
         */
        var rightBottomCornerCutType: CornerCutType?
            set(value) = when (isLtr) {
                true -> endBottomCornerCutType = value
                false -> startBottomCornerCutType = value
            }
            get() = when (isLtr) {
                true -> endBottomCornerCutType
                false -> startBottomCornerCutType
            }

        /**
         * Left Bottom [CornerCutType] to override parent respective ([CornerCutLinearLayout]) child corner cut type.
         * @see [childStartSideCutTypes]
         * @see [childEndSideCutTypes]
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_left_bottom_corner_cut_type]
         */
        var leftBottomCornerCutType: CornerCutType?
            set(value) = when (isLtr) {
                true -> startBottomCornerCutType = value
                false -> endBottomCornerCutType = value
            }
            get() = when (isLtr) {
                true -> startBottomCornerCutType
                false -> endBottomCornerCutType
            }

        /**
         * Convenience function for setting [CornerCutType] to each override child corner of [CornerCutLinearLayout] parent.
         * @see [CornerCutType]
         *
         * Attributes:
         * [R.styleable.CornerCutLinearLayout_Layout_layout_ccll_corner_cut_type]
         */
        fun setCornerCutType(type: CornerCutType?) {
            startTopCornerCutType = type
            endTopCornerCutType = type
            endBottomCornerCutType = type
            startBottomCornerCutType = type
        }
        //endregion

        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs) {
            // Pull the layout param values from the layout XML during
            // inflation.  This is not needed if you don't care about
            // changing the layout behavior in XML.
            c ?: return
            if (attrs != null) {
                val typedArray =
                    c.obtainStyledAttributes(attrs, R.styleable.CornerCutLinearLayout_Layout)
                try {
                    //region Override Edge Child Corner Cut Types
                    couldEdgeChildOverrideParentCornerCutTypeIfEdgeAligned = typedArray.get(
                        R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_could_override_parent_corner_cut_type_if_edge_aligned,
                        couldEdgeChildOverrideParentCornerCutTypeIfEdgeAligned
                    )

                    val edgeChildParentContactChildCornerCutType =
                        CornerCutType.values().getOrNull(
                            typedArray.get(
                                attr = R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_contact_corner_cut_type,
                                default = -1
                            )
                        )
                    val edgeChildParentContactStartTopChildCornerCutType =
                        CornerCutType.values().getOrNull(
                            typedArray.get(
                                attr = R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_start_top_contact_corner_cut_type,
                                default = -1
                            )
                        )
                    val edgeChildParentContactEndTopChildCornerCutType =
                        CornerCutType.values().getOrNull(
                            typedArray.get(
                                attr = R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_end_top_contact_corner_cut_type,
                                default = -1
                            )
                        )
                    val edgeChildParentContactEndBottomChildCornerCutType =
                        CornerCutType.values().getOrNull(
                            typedArray.get(
                                attr = R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_end_bottom_contact_corner_cut_type,
                                default = -1
                            )
                        )
                    val edgeChildParentContactStartBottomChildCornerCutType =
                        CornerCutType.values().getOrNull(
                            typedArray.get(
                                attr = R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_start_bottom_contact_corner_cut_type,
                                default = -1
                            )
                        )

                    val edgeChildParentContactLeftTopChildCornerCutType = CornerCutType.values()
                        .getOrNull(
                            typedArray.get(
                                R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_left_top_contact_corner_cut_type,
                                -1
                            )
                        )

                    val edgeChildParentContactRightTopChildCornerCutType = CornerCutType.values()
                        .getOrNull(
                            typedArray.get(
                                R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_right_top_contact_corner_cut_type,
                                -1
                            )
                        )

                    val edgeChildParentContactRightBottomChildCornerCutType = CornerCutType.values()
                        .getOrNull(
                            typedArray.get(
                                R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_right_bottom_contact_corner_cut_type,
                                -1
                            )
                        )

                    val edgeChildParentContactLeftBottomChildCornerCutType = CornerCutType.values()
                        .getOrNull(
                            typedArray.get(
                                R.styleable.CornerCutLinearLayout_Layout_layout_ccll_edge_child_parent_left_bottom_contact_corner_cut_type,
                                -1
                            )
                        )
                    //endregion

                    //region Override Corner Cut Types
                    val cornerCutType = CornerCutType.values().getOrNull(
                        typedArray.get(
                            attr = R.styleable.CornerCutLinearLayout_Layout_layout_ccll_corner_cut_type,
                            default = -1
                        )
                    )

                    val startTopCornerCutType = CornerCutType.values().getOrNull(
                        typedArray.get(
                            attr = R.styleable.CornerCutLinearLayout_Layout_layout_ccll_start_top_corner_cut_type,
                            default = -1
                        )
                    )
                    val endTopCornerCutType = CornerCutType.values().getOrNull(
                        typedArray.get(
                            attr = R.styleable.CornerCutLinearLayout_Layout_layout_ccll_end_top_corner_cut_type,
                            default = -1
                        )
                    )
                    val startBottomCornerCutType = CornerCutType.values().getOrNull(
                        typedArray.get(
                            attr = R.styleable.CornerCutLinearLayout_Layout_layout_ccll_start_bottom_corner_cut_type,
                            default = -1
                        )
                    )
                    val endBottomCornerCutType = CornerCutType.values().getOrNull(
                        typedArray.get(
                            attr = R.styleable.CornerCutLinearLayout_Layout_layout_ccll_end_bottom_corner_cut_type,
                            default = -1
                        )
                    )

                    val leftTopCornerCutType = CornerCutType.values().getOrNull(
                        typedArray.get(
                            R.styleable.CornerCutLinearLayout_Layout_layout_ccll_left_top_corner_cut_type,
                            -1
                        )
                    )
                    val rightTopCornerCutType = CornerCutType.values().getOrNull(
                        typedArray.get(
                            R.styleable.CornerCutLinearLayout_Layout_layout_ccll_right_top_corner_cut_type,
                            -1
                        )
                    )
                    val rightBottomCornerCutType = CornerCutType.values().getOrNull(
                        typedArray.get(
                            R.styleable.CornerCutLinearLayout_Layout_layout_ccll_right_bottom_corner_cut_type,
                            -1
                        )
                    )
                    val leftBottomCornerCutType = CornerCutType.values().getOrNull(
                        typedArray.get(
                            R.styleable.CornerCutLinearLayout_Layout_layout_ccll_left_bottom_corner_cut_type,
                            -1
                        )
                    )

                    doOnLayout {
                        this.edgeChildParentContactLeftTopChildCornerCutType =
                            edgeChildParentContactLeftTopChildCornerCutType ?: when {
                                isLtr -> edgeChildParentContactStartTopChildCornerCutType
                                    ?: edgeChildParentContactChildCornerCutType
                                else -> edgeChildParentContactEndTopChildCornerCutType
                                    ?: edgeChildParentContactChildCornerCutType
                            }
                        this.edgeChildParentContactRightTopChildCornerCutType =
                            edgeChildParentContactRightTopChildCornerCutType ?: when {
                                isLtr -> edgeChildParentContactEndTopChildCornerCutType
                                    ?: edgeChildParentContactChildCornerCutType
                                else -> edgeChildParentContactStartTopChildCornerCutType
                                    ?: edgeChildParentContactChildCornerCutType
                            }
                        this.edgeChildParentContactRightBottomChildCornerCutType =
                            edgeChildParentContactRightBottomChildCornerCutType ?: when {
                                isLtr -> edgeChildParentContactEndBottomChildCornerCutType
                                    ?: edgeChildParentContactChildCornerCutType
                                else -> edgeChildParentContactStartBottomChildCornerCutType
                                    ?: edgeChildParentContactChildCornerCutType
                            }
                        this.edgeChildParentContactLeftBottomChildCornerCutType =
                            edgeChildParentContactLeftBottomChildCornerCutType ?: when {
                                isLtr -> edgeChildParentContactStartBottomChildCornerCutType
                                    ?: edgeChildParentContactChildCornerCutType
                                else -> edgeChildParentContactEndBottomChildCornerCutType
                                    ?: edgeChildParentContactChildCornerCutType
                            }

                        this.leftTopCornerCutType = leftTopCornerCutType ?: when {
                            isLtr -> startTopCornerCutType ?: cornerCutType
                            else -> endTopCornerCutType ?: cornerCutType
                        }
                        this.rightTopCornerCutType = rightTopCornerCutType ?: when {
                            isLtr -> endTopCornerCutType ?: cornerCutType
                            else -> startTopCornerCutType ?: cornerCutType
                        }
                        this.rightBottomCornerCutType = rightBottomCornerCutType ?: when {
                            isLtr -> endBottomCornerCutType ?: cornerCutType
                            else -> startBottomCornerCutType ?: cornerCutType
                        }
                        this.leftBottomCornerCutType = leftBottomCornerCutType ?: when {
                            isLtr -> startBottomCornerCutType ?: cornerCutType
                            else -> endBottomCornerCutType ?: cornerCutType
                        }
                    }
                    //endregion
                } finally {
                    typedArray.recycle()
                }
            }
        }

        constructor(width: Int, height: Int) : super(width, height)
        constructor(width: Int, height: Int, weight: Float) : super(width, height, weight)
        constructor(p: ViewGroup.LayoutParams?) : super(p)
        constructor(source: MarginLayoutParams?) : super(source)
        constructor(source: LinearLayout.LayoutParams?) : super(source)
    }
    //endregion

    //region Save/Restore State & Constants
    override fun onRestoreInstanceState(state: Parcelable?) {
        (state as? Bundle)?.let {
            super.onRestoreInstanceState(it.getParcelable(EXTRA_KEY_SUPER_STATE))
            state.getIntArray(EXTRA_KEY_USER_DEFINED_PADDING)!!.copyInto(userDefinedPadding)
            state.getFloatArray(EXTRA_KEY_CORNER_CUT_DIMENSIONS)!!.copyInto(cornerCutDimensions)
            startTopCornerCutType =
                state.getSerializable(EXTRA_KEY_START_TOP_CORNER_CUT_TYPE) as CornerCutType
            endTopCornerCutType =
                state.getSerializable(EXTRA_KEY_END_TOP_CORNER_CUT_TYPE) as CornerCutType
            endBottomCornerCutType =
                state.getSerializable(EXTRA_KEY_END_BOTTOM_CORNER_CUT_TYPE) as CornerCutType
            startBottomCornerCutType =
                state.getSerializable(EXTRA_KEY_START_BOTTOM_CORNER_CUT_TYPE) as CornerCutType
            cornerCutFlag = state.getInt(EXTRA_KEY_CORNER_CUT_FLAG)
            rectangleTypeCornerCutRoundCornerRadiusDepth =
                state.getFloat(EXTRA_KEY_RECTANGLE_TYPE_CORNER_CUT_ROUND_CORNER_RADIUS_DEPTH)
            rectangleTypeCornerCutRoundCornerRadiusLength =
                state.getFloat(EXTRA_KEY_RECTANGLE_TYPE_CORNER_CUT_ROUND_CORNER_RADIUS_LENGTH)
            shouldUseMaxAllowedCornerCutSize =
                state.getBoolean(EXTRA_KEY_SHOULD_USER_MAX_ALLOWED_CORNER_CUT_SIZE)
            shouldUseMaxAllowedCornerCutDepthOrLengthToBeEqual =
                state.getBoolean(
                    EXTRA_KEY_SHOULD_USER_MAX_ALLOWED_CORNER_CUT_DEPTH_OR_LENGTH_TO_BE_EQUAL
                )

            childSideCutFlag = state.getInt(EXTRA_KEY_CHILD_SIDE_CUT_FLAG)
            childStartSideCornerCutType =
                state.getSerializable(EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_TYPE) as CornerCutType
            childEndSideCornerCutType =
                state.getSerializable(EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_TYPE) as CornerCutType
            childStartSideCornerCutDepth =
                state.getFloat(EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_DEPTH)
            childStartSideCornerCutLength =
                state.getFloat(EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_LENGTH)
            childEndSideCornerCutDepth =
                state.getFloat(EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_DEPTH)
            childEndSideCornerCutLength =
                state.getFloat(EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_LENGTH)
            childRectangleTypeCornerCutRoundCornerRadiusDepth =
                state.getFloat(
                    EXTRA_KEY_CHILD_RECTANGLE_TYPE_CORNER_CUT_ROUND_CORNER_RADIUS_DEPTH
                )
            childRectangleTypeCornerCutRoundCornerRadiusLength =
                state.getFloat(
                    EXTRA_KEY_CHILD_RECTANGLE_TYPE_CORNER_CUT_ROUND_CORNER_RADIUS_LENGTH
                )

            childStartSideCornerCutDepthOffset =
                state.getFloat(EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_DEPTH_OFFSET)
            childStartSideCornerCutLengthOffset =
                state.getFloat(EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_LENGTH_OFFSET)
            childStartSideCornerCutRotationDegree =
                state.getFloat(EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_ROTATION_DEGREE)
            childEndSideCornerCutDepthOffset =
                state.getFloat(EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_DEPTH_OFFSET)
            childEndSideCornerCutLengthOffset =
                state.getFloat(EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_LENGTH_OFFSET)
            childEndSideCornerCutRotationDegree =
                state.getFloat(EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_ROTATION_DEGREE)
            isChildCornerCutEndRotationMirroredFromStartRotation =
                state.getBoolean(
                    EXTRA_KEY_IS_CHILD_CORNER_CUT_END_ROTATION_MIRRORED_FROM_START_ROTATION
                )

            customShadowRadius = state.getFloat(EXTRA_KEY_CUSTOM_SHADOW_RADIUS)
            customShadowOffsetDx = state.getFloat(EXTRA_KEY_CUSTOM_SHADOW_OFFSET_DX)
            customShadowOffsetDy = state.getFloat(EXTRA_KEY_CUSTOM_SHADOW_OFFSET_DY)
            customDividerColor = state.getInt(EXTRA_KEY_CUSTOM_SHADOW_COLOR)
            isCustomShadowAutoPaddingEnabled =
                state.getBoolean(EXTRA_KEY_IS_CUSTOM_SHADOW_AUTO_PADDING_ENABLED)
            couldDrawCustomShadowOverUserDefinedPadding =
                state.getBoolean(EXTRA_KEY_COULD_DRAW_CUSTOM_SHADOW_OVER_USER_DEFINED_PADDING)

            customDividerHeight = state.getFloat(EXTRA_KEY_CUSTOM_DIVIDER_HEIGHT)
            customDividerPaddingStart = state.getFloat(EXTRA_KEY_CUSTOM_DIVIDER_PADDING_START)
            customDividerPaddingEnd = state.getFloat(EXTRA_KEY_CUSTOM_DIVIDER_PADDING_END)
            customDividerColor = state.getInt(EXTRA_KEY_CUSTOM_DIVIDER_COLOR)
            customDividerLineCap =
                state.getSerializable(EXTRA_KEY_CUSTOM_DIVIDER_LINE_CAP) as Paint.Cap
            customDividerShowFlag = state.getInt(EXTRA_KEY_CUSTOM_DIVIDER_SHOW_FLAG)
            customDividerDashWidth = state.getFloat(EXTRA_KEY_CUSTOM_DIVIDER_DASH_WIDTH)
            customDividerDashGap = state.getFloat(EXTRA_KEY_CUSTOM_DIVIDER_DASH_GAP)
            customCustomDividerGravity =
                state.getSerializable(EXTRA_KEY_CUSTOM_DIVIDER_GRAVITY) as CustomDividerGravity
            return
        }

        // Stops a bug with the wrong state being passed to the super
        super.onRestoreInstanceState(state)
    }

    override fun onSaveInstanceState(): Parcelable? {
        val bundle = Bundle()
        bundle.putParcelable(EXTRA_KEY_SUPER_STATE, super.onSaveInstanceState())
        bundle.putIntArray(EXTRA_KEY_USER_DEFINED_PADDING, userDefinedPadding)
        bundle.putFloatArray(EXTRA_KEY_CORNER_CUT_DIMENSIONS, cornerCutDimensions)
        bundle.putSerializable(EXTRA_KEY_START_TOP_CORNER_CUT_TYPE, startTopCornerCutType)
        bundle.putSerializable(EXTRA_KEY_END_TOP_CORNER_CUT_TYPE, endTopCornerCutType)
        bundle.putSerializable(EXTRA_KEY_END_BOTTOM_CORNER_CUT_TYPE, endBottomCornerCutType)
        bundle.putSerializable(EXTRA_KEY_START_BOTTOM_CORNER_CUT_TYPE, startBottomCornerCutType)
        bundle.putInt(EXTRA_KEY_CORNER_CUT_FLAG, cornerCutFlag)
        bundle.putFloat(
            EXTRA_KEY_RECTANGLE_TYPE_CORNER_CUT_ROUND_CORNER_RADIUS_DEPTH,
            rectangleTypeCornerCutRoundCornerRadiusDepth
        )
        bundle.putFloat(
            EXTRA_KEY_RECTANGLE_TYPE_CORNER_CUT_ROUND_CORNER_RADIUS_LENGTH,
            rectangleTypeCornerCutRoundCornerRadiusLength
        )
        bundle.putBoolean(
            EXTRA_KEY_SHOULD_USER_MAX_ALLOWED_CORNER_CUT_SIZE,
            shouldUseMaxAllowedCornerCutSize
        )
        bundle.putBoolean(
            EXTRA_KEY_SHOULD_USER_MAX_ALLOWED_CORNER_CUT_DEPTH_OR_LENGTH_TO_BE_EQUAL,
            shouldUseMaxAllowedCornerCutDepthOrLengthToBeEqual
        )

        bundle.putInt(EXTRA_KEY_CHILD_SIDE_CUT_FLAG, childSideCutFlag)
        bundle.putSerializable(
            EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_TYPE,
            childStartSideCornerCutType
        )
        bundle.putSerializable(
            EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_TYPE,
            childEndSideCornerCutType
        )
        bundle.putFloat(
            EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_DEPTH,
            childStartSideCornerCutDepth
        )
        bundle.putFloat(
            EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_LENGTH,
            childStartSideCornerCutLength
        )
        bundle.putFloat(EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_DEPTH, childEndSideCornerCutDepth)
        bundle.putFloat(EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_LENGTH, childEndSideCornerCutLength)
        bundle.putFloat(
            EXTRA_KEY_CHILD_RECTANGLE_TYPE_CORNER_CUT_ROUND_CORNER_RADIUS_DEPTH,
            childRectangleTypeCornerCutRoundCornerRadiusDepth
        )
        bundle.putFloat(
            EXTRA_KEY_CHILD_RECTANGLE_TYPE_CORNER_CUT_ROUND_CORNER_RADIUS_LENGTH,
            childRectangleTypeCornerCutRoundCornerRadiusLength
        )
        bundle.putFloat(
            EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_DEPTH_OFFSET,
            childStartSideCornerCutDepthOffset
        )
        bundle.putFloat(
            EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_LENGTH_OFFSET,
            childStartSideCornerCutLengthOffset
        )
        bundle.putFloat(
            EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_ROTATION_DEGREE,
            childStartSideCornerCutRotationDegree
        )
        bundle.putFloat(
            EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_DEPTH_OFFSET,
            childEndSideCornerCutDepthOffset
        )
        bundle.putFloat(
            EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_LENGTH_OFFSET,
            childEndSideCornerCutLengthOffset
        )
        bundle.putFloat(
            EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_ROTATION_DEGREE,
            childEndSideCornerCutRotationDegree
        )
        bundle.putBoolean(
            EXTRA_KEY_IS_CHILD_CORNER_CUT_END_ROTATION_MIRRORED_FROM_START_ROTATION,
            isChildCornerCutEndRotationMirroredFromStartRotation
        )

        bundle.putFloat(EXTRA_KEY_CUSTOM_SHADOW_RADIUS, customShadowRadius)
        bundle.putFloat(EXTRA_KEY_CUSTOM_SHADOW_OFFSET_DX, customShadowOffsetDx)
        bundle.putFloat(EXTRA_KEY_CUSTOM_SHADOW_OFFSET_DY, customShadowOffsetDy)
        bundle.putInt(EXTRA_KEY_CUSTOM_SHADOW_COLOR, customDividerColor)
        bundle.putBoolean(
            EXTRA_KEY_IS_CUSTOM_SHADOW_AUTO_PADDING_ENABLED,
            isCustomShadowAutoPaddingEnabled
        )
        bundle.putBoolean(
            EXTRA_KEY_COULD_DRAW_CUSTOM_SHADOW_OVER_USER_DEFINED_PADDING,
            couldDrawCustomShadowOverUserDefinedPadding
        )

        bundle.putFloat(EXTRA_KEY_CUSTOM_DIVIDER_HEIGHT, customDividerHeight)
        bundle.putFloat(EXTRA_KEY_CUSTOM_DIVIDER_PADDING_START, customDividerPaddingStart)
        bundle.putFloat(EXTRA_KEY_CUSTOM_DIVIDER_PADDING_END, customDividerPaddingEnd)
        bundle.putInt(EXTRA_KEY_CUSTOM_DIVIDER_COLOR, customDividerColor)
        bundle.putSerializable(EXTRA_KEY_CUSTOM_DIVIDER_LINE_CAP, customDividerLineCap)
        bundle.putInt(EXTRA_KEY_CUSTOM_DIVIDER_SHOW_FLAG, customDividerShowFlag)
        bundle.putFloat(EXTRA_KEY_CUSTOM_DIVIDER_DASH_WIDTH, customDividerDashWidth)
        bundle.putFloat(EXTRA_KEY_CUSTOM_DIVIDER_DASH_GAP, customDividerDashGap)
        bundle.putSerializable(EXTRA_KEY_CUSTOM_DIVIDER_GRAVITY, customCustomDividerGravity)
        return bundle
    }

    companion object {
        private val DEFAULT_CORNER_CUT_TYPE = CornerCutType.OVAL
        private val DEFAULT_CHILD_CORNER_CUT_TYPE = CornerCutType.OVAL
        private const val DEFAULT_CORNER_CUT_SIZE = 0.0F
        private const val DEFAULT_RECTANGLE_TYPE_CORNER_CUT_RADIUS = 0.0F
        private const val DEFAULT_CHILD_CORNER_CUT_ROTATION_DEGREE = 0.0F

        private const val UNDEFINED = Int.MIN_VALUE

        private const val EXTRA_KEY_SUPER_STATE = "EXTRA_KEY_SUPER_STATE"
        private const val EXTRA_KEY_USER_DEFINED_PADDING = "EXTRA_KEY_USER_DEFINED_PADDING"
        private const val EXTRA_KEY_CORNER_CUT_DIMENSIONS = "EXTRA_KEY_CORNER_CUT_DIMENSIONS"
        private const val EXTRA_KEY_START_TOP_CORNER_CUT_TYPE =
            "EXTRA_KEY_START_TOP_CORNER_CUT_TYPE"
        private const val EXTRA_KEY_END_TOP_CORNER_CUT_TYPE =
            "EXTRA_KEY_END_TOP_CORNER_CUT_TYPE"
        private const val EXTRA_KEY_END_BOTTOM_CORNER_CUT_TYPE =
            "EXTRA_KEY_END_BOTTOM_CORNER_CUT_TYPE"
        private const val EXTRA_KEY_START_BOTTOM_CORNER_CUT_TYPE =
            "EXTRA_KEY_START_BOTTOM_CORNER_CUT_TYPE"
        private const val EXTRA_KEY_CORNER_CUT_FLAG = "EXTRA_KEY_CORNER_CUT_FLAG"
        private const val EXTRA_KEY_RECTANGLE_TYPE_CORNER_CUT_ROUND_CORNER_RADIUS_DEPTH =
            "EXTRA_KEY_RECTANGLE_TYPE_CORNER_CUT_ROUND_CORNER_RADIUS_DEPTH"
        private const val EXTRA_KEY_RECTANGLE_TYPE_CORNER_CUT_ROUND_CORNER_RADIUS_LENGTH =
            "EXTRA_KEY_RECTANGLE_TYPE_CORNER_CUT_ROUND_CORNER_RADIUS_LENGTH"
        private const val EXTRA_KEY_SHOULD_USER_MAX_ALLOWED_CORNER_CUT_SIZE =
            "EXTRA_KEY_SHOULD_USER_MAX_ALLOWED_CORNER_CUT_SIZE"
        private const val EXTRA_KEY_SHOULD_USER_MAX_ALLOWED_CORNER_CUT_DEPTH_OR_LENGTH_TO_BE_EQUAL =
            "EXTRA_KEY_SHOULD_USER_MAX_ALLOWED_CORNER_CUT_DEPTH_OR_LENGTH_TO_BE_EQUAL"

        private const val EXTRA_KEY_CHILD_SIDE_CUT_FLAG = "EXTRA_KEY_CHILD_SIDE_CUT_FLAG"
        private const val EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_TYPE =
            "EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_TYPE"
        private const val EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_TYPE =
            "EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_TYPE"
        private const val EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_DEPTH =
            "EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_DEPTH"
        private const val EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_LENGTH =
            "EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_DEPTH"
        private const val EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_DEPTH =
            "EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_DEPTH"
        private const val EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_LENGTH =
            "EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_DEPTH"
        private const val EXTRA_KEY_CHILD_RECTANGLE_TYPE_CORNER_CUT_ROUND_CORNER_RADIUS_DEPTH =
            "EXTRA_KEY_CHILD_RECTANGLE_TYPE_CORNER_CUT_ROUND_CORNER_RADIUS_DEPTH"
        private const val EXTRA_KEY_CHILD_RECTANGLE_TYPE_CORNER_CUT_ROUND_CORNER_RADIUS_LENGTH =
            "EXTRA_KEY_CHILD_RECTANGLE_TYPE_CORNER_CUT_ROUND_CORNER_RADIUS_LENGTH"
        private const val EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_DEPTH_OFFSET =
            "EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_DEPTH_OFFSET"
        private const val EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_LENGTH_OFFSET =
            "EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_LENGTH_OFFSET"
        private const val EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_ROTATION_DEGREE =
            "EXTRA_KEY_CHILD_START_SIDE_CORNER_CUT_ROTATION_DEGREE"
        private const val EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_DEPTH_OFFSET =
            "EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_DEPTH_OFFSET"
        private const val EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_LENGTH_OFFSET =
            "EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_LENGTH_OFFSET"
        private const val EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_ROTATION_DEGREE =
            "EXTRA_KEY_CHILD_END_SIDE_CORNER_CUT_ROTATION_DEGREE"
        private const val EXTRA_KEY_IS_CHILD_CORNER_CUT_END_ROTATION_MIRRORED_FROM_START_ROTATION =
            "EXTRA_KEY_IS_CHILD_CORNER_CUT_END_ROTATION_MIRRORED_FROM_START_ROTATION"

        private const val EXTRA_KEY_CUSTOM_SHADOW_RADIUS = "EXTRA_KEY_CUSTOM_SHADOW_RADIUS"
        private const val EXTRA_KEY_CUSTOM_SHADOW_OFFSET_DX =
            "EXTRA_KEY_CUSTOM_SHADOW_OFFSET_DX"
        private const val EXTRA_KEY_CUSTOM_SHADOW_OFFSET_DY =
            "EXTRA_KEY_CUSTOM_SHADOW_OFFSET_DY"
        private const val EXTRA_KEY_CUSTOM_SHADOW_COLOR = "EXTRA_KEY_CUSTOM_SHADOW_COLOR"
        private const val EXTRA_KEY_IS_CUSTOM_SHADOW_AUTO_PADDING_ENABLED =
            "EXTRA_KEY_IS_CUSTOM_SHADOW_AUTO_PADDING_ENABLED"
        private const val EXTRA_KEY_COULD_DRAW_CUSTOM_SHADOW_OVER_USER_DEFINED_PADDING =
            "EXTRA_KEY_COULD_DRAW_CUSTOM_SHADOW_OVER_USER_DEFINED_PADDING"

        private const val EXTRA_KEY_CUSTOM_DIVIDER_HEIGHT = "EXTRA_KEY_CUSTOM_DIVIDER_HEIGHT"
        private const val EXTRA_KEY_CUSTOM_DIVIDER_PADDING_START =
            "EXTRA_KEY_CUSTOM_DIVIDER_PADDING_START"
        private const val EXTRA_KEY_CUSTOM_DIVIDER_PADDING_END =
            "EXTRA_KEY_CUSTOM_DIVIDER_PADDING_END"
        private const val EXTRA_KEY_CUSTOM_DIVIDER_COLOR = "EXTRA_KEY_CUSTOM_DIVIDER_COLOR"
        private const val EXTRA_KEY_CUSTOM_DIVIDER_LINE_CAP =
            "EXTRA_KEY_CUSTOM_DIVIDER_LINE_CAP"
        private const val EXTRA_KEY_CUSTOM_DIVIDER_SHOW_FLAG =
            "EXTRA_KEY_CUSTOM_DIVIDER_SHOW_FLAG"
        private const val EXTRA_KEY_CUSTOM_DIVIDER_DASH_WIDTH =
            "EXTRA_KEY_CUSTOM_DIVIDER_DASH_WIDTH"
        private const val EXTRA_KEY_CUSTOM_DIVIDER_DASH_GAP =
            "EXTRA_KEY_CUSTOM_DIVIDER_DASH_GAP"
        private const val EXTRA_KEY_CUSTOM_DIVIDER_GRAVITY = "EXTRA_KEY_CUSTOM_DIVIDER_GRAVITY"
    }
    //endregion

    //region Cutout Provider Interfaces
    /**
     * Provider that allow custom corner cut.
     */
    interface CornerCutProvider {
        /**
         * @param view - [CornerCutLinearLayout] owner of this provider.
         * @param cutout - path that holds custom (user-defined) cutout data.
         * Note that path will be closed automatically afterwards.
         * @param cutEdge - one of [CornerCutFlag]
         * @param rectF - rectangle with respective corner cut bounds.
         * This bounds (along with modifications) will be passed to [getPathTransformationMatrix] function.
         * Note that it is your responsibility to keep path data within that bound.
         * In other words, path could exceeds specified bounds and in this case rather serves as a hint of preferred bounds.
         *
         * @return true - if path cutout data should be accepted.
         * Otherwise [cutEdge] will be handled by default corner cut logic.
         */
        fun getCornerCut(
            view: CornerCutLinearLayout,
            cutout: Path,
            cutEdge: Int,
            rectF: RectF
        ): Boolean

        /**
         * This method allows custom cutout path transformation (i.e. rotation, skew, warp, etc.)
         *
         * @param view - [CornerCutLinearLayout] owner of this provider.
         * @param cutout - path that holds custom (user-defined) cutout data.
         * Note that path will be closed automatically afterwards.
         * @param cutEdge - one of [CornerCutFlag]
         * @param rectF - rectangle with respective corner cut bounds.
         * May be previously modified in [getCornerCut] function.
         * Note that it is your responsibility to keep path data within that bound.
         * In other words, path could exceeds specified bounds and in this case rather serves as a hint of preferred bounds.
         *
         * @return [Matrix] - custom cutout transformation matrix.
         * Return null is case you want to skip any [cutout] path transformations.
         */
        fun getPathTransformationMatrix(
            view: CornerCutLinearLayout,
            cutout: Path,
            cutEdge: Int,
            rectF: RectF
        ): Matrix? {
            return null
        }
    }

    /**
     * Provider that allow custom child corner cut.
     */
    interface ChildCornerCutProvider {
        /**
         * @param view - [CornerCutLinearLayout] owner of this provider.
         * @param cutout - path that holds custom (user-defined) child cutout data.
         * Note that path will be closed automatically afterwards.
         * @param cutSide - one of [ChildSideCutFlag]
         * @param rectF - rectangle with respective child corner cut bounds.
         * Note that it is your responsibility to keep path data within that bound.
         * In other words, path could exceeds specified bounds and in this case rather serves as a hint of preferred bounds.
         * This bounds (along with modifications) will be passed to [getPathTransformationMatrix] function.
         * @param relativeCutTopChild - top child in cut contact relative to cut of this [CornerCutLinearLayout] orientation.
         * For instance, for [LinearLayout.HORIZONTAL] & RTL it would be right-side child between cutout.
         * Could be null in case of first edge child.
         * @param relativeCutBottomChild - bottom child in cut contact relative to cut of this [CornerCutLinearLayout] orientation.
         * For instance, for [LinearLayout.HORIZONTAL] & RTL it would be left-side child between cutout.
         * Could be null in case of last edge child.
         *
         * @return true - if path cutout data should be accepted.
         * Otherwise [cutSide] will be handled by default corner cut logic.
         */
        fun getCornerCut(
            view: CornerCutLinearLayout,
            cutout: Path,
            cutSide: Int,
            rectF: RectF,
            relativeCutTopChild: View?,
            relativeCutBottomChild: View?
        ): Boolean

        /**
         * This method allows custom child cutout path transformation (i.e. rotation, skew, warp, etc.)
         *
         * @param view - [CornerCutLinearLayout] owner of this provider.
         * @param cutout - path that holds custom (user-defined) child cutout data.
         * Note that path will be closed automatically afterwards.
         * @param cutSide - one of [ChildSideCutFlag]
         * @param rectF - rectangle with respective child corner cut bounds.
         * May be previously modified in [getCornerCut] function.
         * Note that it is your responsibility to keep path data within that bound.
         * In other words, path could exceeds specified bounds and in this case rather serves as a hint of preferred bounds.
         * @param relativeCutTopChild - top child in cut contact relative to cut of this [CornerCutLinearLayout] orientation.
         * For instance, for [LinearLayout.HORIZONTAL] & RTL it would be right-side child between cutout.
         * Could be null in case of first edge child.
         * @param relativeCutBottomChild - bottom child in cut contact relative to cut of this [CornerCutLinearLayout] orientation.
         * For instance, for [LinearLayout.HORIZONTAL] & RTL it would be left-side child between cutout.
         * Could be null in case of last edge child.
         *
         * @return [Matrix] - custom child cutout transformation matrix.
         * Return null is case you want to skip any [cutout] path transformations.
         */
        fun getPathTransformationMatrix(
            view: CornerCutLinearLayout,
            cutout: Path,
            cutSide: Int,
            rectF: RectF,
            relativeCutTopChild: View?,
            relativeCutBottomChild: View?
        ): Matrix? {
            return null
        }
    }

    /**
     * Provider that allow custom cutout.
     */
    interface CustomCutoutProvider {
        /**
         * @param view - [CornerCutLinearLayout] owner of this provider.
         * @param cutout - path that holds custom (user-defined) cutout data.
         * Note that path will be closed automatically afterwards.
         * @param rectF - padded bounds of this [CornerCutLinearLayout].
         *
         * @return true - if path cutout data should be accepted.
         */
        fun getCutout(view: CornerCutLinearLayout, cutout: Path, rectF: RectF)

        /**
         * This method allows custom cutout path transformation (i.e. rotation, skew, warp, etc.)
         *
         * @param view - [CornerCutLinearLayout] owner of this provider.
         * @param cutout - path that holds custom (user-defined) cutout data.
         * Note that path will be closed automatically afterwards.
         * @param rectF - padded bounds of this [CornerCutLinearLayout].
         *
         * @return [Matrix] - custom cutout transformation matrix.
         * Return null is case you want to skip any [cutout] path transformations.
         */
        fun getPathTransformationMatrix(
            view: CornerCutLinearLayout,
            cutout: Path,
            rectF: RectF
        ): Matrix? {
            return null
        }
    }
    //endregion

    /**
     * Provider that allow custom divider.
     */
    interface CustomDividerProvider {
        /**
         * @param view - [CornerCutLinearLayout] owner of this provider.
         * @param dividerPath - path that holds custom (user-defined) cutout data.
         * Note that path will NOT be closed automatically afterwards.
         * See also [customDividerProviderPath].
         * @param dividerPaint - holds divider paint settings.
         * See also [customDividerProviderPaint].
         * @param showDividerFlag - specification of divider.
         * @see [CustomDividerShowFlag].
         * @param dividerTypeIndex - index of this divider in relation to its type ([CustomDividerShowFlag]).
         * So, when [showDividerFlag] != [CustomDividerShowFlag.MIDDLE], then [dividerTypeIndex] always = 0.
         * For instance, in [CornerCutLinearLayout.VERTICAL] with [showDividerFlag] = [CustomDividerShowFlag.MIDDLE] with 3 child views
         * [dividerTypeIndex] would be equal to:
         * - 0 between 1st and 2nd child
         * - 1 between 2nd and 3rd child.
         * @param rectF - bounds (line) of padded divider from start to end relative to this [CornerCutLinearLayout] orientation.
         * Note that in this case this bounds are rather guide of calculated divider position.
         *
         * @return true - if divider handler by user.
         * Otherwise default custom divider may be drawn if eligible (divider height > 0, etc.)
         */
        fun getDivider(
            view: CornerCutLinearLayout,
            dividerPath: Path,
            dividerPaint: Paint,
            showDividerFlag: Int,
            dividerTypeIndex: Int,
            rectF: RectF
        ): Boolean
    }

    /**
     * Corner Cut flag for each of this [CornerCutLinearLayout] corner.
     * Having specified flag in [cornerCutFlag] will enable further corner cut logic, otherwise all
     * respective corner cut properties will be ignored.
     * Use extension methods for easy flag combination (e.g. [combineFlags] or [Int.addFlag], etc.)
     */
    object CornerCutFlag {
        const val START_TOP = 1
        const val END_TOP = 2
        const val START_BOTTOM = 4
        const val END_BOTTOM = 8
    }

    /**
     *
     * Flag that defines which child sides would be cut out.
     *
     * Start side for [LinearLayout.VERTICAL] orientation in LTR means view left cutouts
     * and for [LinearLayout.HORIZONTAL] orientation in LTR means view bottom cutouts
     *
     * End side for [LinearLayout.VERTICAL] orientation in LTR means view right cutouts
     * and for [LinearLayout.HORIZONTAL] orientation in LTR means view top cutouts
     *
     * Examples.
     *
     * [LinearLayout.VERTICAL], LRT:            | [LinearLayout.VERTICAL], RTL:
     *                                          |
     *        |    view n    |                  |         |    view n    |
     *        |______________|                  |         |______________|
     *        |              |                  |         |              |
     *  start |   view n+1   | end              |    end  |   view n+1   | start
     *        |______________|                  |         |______________|
     *        |              |                  |         |              |
     *        |   view n+2   |                  |         |   view n+2   |
     *                                          |
     * [LinearLayout.HORIZONTAL], LRT:          | [LinearLayout.HORIZONTAL], RTL:
     *                                          |
     *               end                        |               start
     *  ------------------------------          | ---------------------------------
     *          |           |                   |           |           |
     *  view n  | view n+1  | view n+2          | view n+2  | view n+1  | view n
     *          |           |                   |           |           |
     *  ------------------------------          | ---------------------------------
     *             start                        |                end
     *                                          |
     *                                          |
     */
    object ChildSideCutFlag {
        const val START = 1
        const val END = 2
    }

    /**
     * Corner cut type.
     *
     * Note there are exist priority for resolving attributes conflicts in XML.
     * Priority follows the next pattern (higher number - higher priority):
     * 1 Global
     * [R.styleable.CornerCutLinearLayout_ccll_corner_cut_type]
     *
     * 2 Global Side Relative
     * [R.styleable.CornerCutLinearLayout_ccll_start_corners_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_end_corners_cut_type]
     *
     * 3 Global Side Absolute
     * [R.styleable.CornerCutLinearLayout_ccll_left_corners_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_right_corners_cut_type]
     *
     * 4 Global Top/Bottom Side
     * [R.styleable.CornerCutLinearLayout_ccll_top_corners_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_bottom_corners_cut_type]
     *
     * 5 Side Relative
     * [R.styleable.CornerCutLinearLayout_ccll_start_top_corner_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_end_top_corner_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_start_bottom_corner_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_end_bottom_corner_cut_type]
     *
     * 6 Side Absolute
     * [R.styleable.CornerCutLinearLayout_ccll_left_top_corner_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_right_top_corner_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_left_bottom_corner_cut_type]
     * [R.styleable.CornerCutLinearLayout_ccll_right_bottom_corner_cut_type]
     *
     * For example (LTR):
     * app:ccll_right_bottom_corner_cut_type="BEVEL"
     * app:ccll_start_bottom_corner_cut_type="RECTANGLE_INVERSE"
     * app:ccll_bottom_side_corner_cut_type="OVAL"
     * app:ccll_left_side_corner_cut_type="RECTANGLE"
     * app:ccll_end_side_corner_cut_type="RECTANGLE_INVERSE"
     * app:ccll_corner_cut_type="OVAL_INVERSE"
     *
     * Resulting corner type would be:
     * [leftTopCornerCutType] = [CornerCutType.RECTANGLE]
     * [rightTopCornerCutType] = [CornerCutType.RECTANGLE_INVERSE]
     * [rightBottomCornerCutType] = [CornerCutType.BEVEL]
     * [leftBottomCornerCutType] = [CornerCutType.RECTANGLE_INVERSE]
     */
    enum class CornerCutType {
        /**
         * Makes view with rounded corners.
         */
        OVAL,

        /**
         * Makes view with inverse rounded corners.
         */
        OVAL_INVERSE,

        /**
         * Makes view corner beveled.
         */
        BEVEL,

        /**
         * Does nothing if [rectangleTypeCornerCutRoundCornerRadiusLength]
         * and [rectangleTypeCornerCutRoundCornerRadiusDepth]
         * or [childRectangleTypeCornerCutRoundCornerRadiusDepth]
         * and [childRectangleTypeCornerCutRoundCornerRadiusLength] <= 0.0F
         */
        RECTANGLE,

        /**
         * Makes view rectangle cut defined by respective depth & length.
         */
        RECTANGLE_INVERSE
    }


    /**
     * Gravity for custom divider.
     * @see [customCustomDividerGravity]
     */
    enum class CustomDividerGravity {
        CENTER,
        START,
        END
    }

    /**
     * Custom divider show flags.
     * @see [customDividerShowFlag]
     */
    object CustomDividerShowFlag {
        const val NONE = 0
        const val BEGINNING = 1
        const val MIDDLE = 2
        const val END = 4
        const val CONTAINER_BEGINNING = 8
        const val CONTAINER_END = 16
    }

    /**
     * Delegate that force to rebuild cutout path and redraw view.
     */
    private inner class Delegate<T : Any>(
        initialValue: T,
        beforeSetPredicate: T.() -> T? = { this },
    ) : NonNullDelegate<T>(
        initialValue,
        beforeSetPredicate = { _, _, newValue -> beforeSetPredicate(newValue) },
        afterSetPredicate = { _, _, _ -> invalidateCornerCutPath() }
    )

    private inner class NullableDelegate<T>(
        initialValue: T,
        beforeSetPredicate: T.() -> T = { this },
    ) : io.devlight.xtreeivi.cornercutlinearlayout.util.delegate.NullableDelegate<T>(
        initialValue,
        beforeSetPredicate = { _, _, newValue -> beforeSetPredicate(newValue) },
        afterSetPredicate = { _, _, _ -> invalidateCornerCutPath() }
    )
}