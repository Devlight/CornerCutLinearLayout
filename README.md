# CornerCutLinearLayout

`CornerCutLinearLayout` extends [`LinearLayout`](https://developer.android.com/reference/android/widget/LinearLayout). It allows cutting parent corners with different shapes and build proper shadow to complex shapes.\
It also allows cutting each child's corners.

<p align="center"><img src="/assets/images/logo.png" width="200" height="auto"></p>

Additionally, using available properties and custom providers, those cuts may be turned into cutouts of different shapes, sizes, etc.\
Widget's sole purpose is to use with children with no transformations (like rotation, scale, matrix transformations).

Amongst additional features:
- RTL support
- child layout parameters that allow overriding default parent parameters
- custom shadow
- custom dividers & providers
- custom cutouts & providers
- custom view visible area provider

# Installation

**Step 1.**  Add the JitPack repository to your project's `build.gradle` file:

```groovy
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

or

```groovy
subprojects {
    repositories {
        ...
        maven {
            ...
            url = "https://jitpack.io"
        }
    }
}
```

**Step 2.**  Add the following dependency to your target module's `build.gradle` file:

```groovy
dependencies {
    implementation 'com.github.XtreeIvI:CornerCutLinearLayout:1.0.0'
}
```

# Usage

For simple quick usage that covers most user cases, see **Basics** section below.\
For more complex usage section **Advanced** might be useful.

# Basics

#### Declaration in XML

All widget attributes start with `ccll_` prefix. Children's layout attributes starts with `layout_ccll_` prefix, respectively. There are plenty of attributes. In order to facilitate their usage, they separated into few categories with start prefix:
- `ccll_` or `ccll_corner_cut` - global widget attributes
- `ccll_child_` - global child cut attributes
- `ccll_custom_shadow` - custom shadow attributes
- `ccll_custom_divider` - custom divider attributes

```xml
<io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
    android:id="@+id/ccll_kotlin_synthetic_view"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="#FFFFFF"
    android:orientation="vertical"

    app:ccll_corner_cut_flag="start_top|end_bottom"
    app:ccll_corner_cut_size="24dp"
    app:ccll_corner_cut_type="oval"

    app:ccll_child_corner_cut_type="oval_inverse"

    app:ccll_custom_shadow_color="#FEB545"
    app:ccll_custom_shadow_radius="16dp">

    <View
        android:layout_width="match_parent"
        android:layout_height="50dp" />

    <View
        android:layout_width="match_parent"
        android:layout_height="50dp" />
</io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout>
```

#### Declaration in Code (Kotlin)

All XML attributes correspond to `CornerCutLinearLayout`'s property or function.

```kotlin
with(ccll_kotlin_synthetic_view) {
    val density = resources.displayMetrics.density
    cornerCutFlag = combineFlags(CornerCutFlag.START_TOP, CornerCutFlag.END_BOTTOM)
    setCornerCutSize(density * 24)
    setCornerCutType(CornerCutType.OVAL)
    setChildCornerCutType(CornerCutType.OVAL_INVERSE)
    customShadowColor = Color.parseColor("#FEB545")
    customShadowRadius = density * 16
}
```
Visual result would be follow:

<img src="/assets/images/first_usage.jpg" width="150" height="150">

### Corner Cut Anatomy

By default `CornerCutType.OVAL` is used for both parent corner and child cuts. Corner cut are bounded to its personal dimensions - **depth** and **length**.

**Depth** - relative to orientation width of the cutout bounds.\
**Length** - relative to orientation height of the cutout bounds.

Each of 4 parent corner cuts dimensions could be specified individually.\
Children's corner cuts could have separate dimensions for `ChildSideCutFlag.START` and `ChildSideCutFlag.END` sides. Children's corner cuts could also be rotated. The rotation angle could optionally be mirrored.

<img src="/assets/images/anatomy.png" width="420" height="150">

As you may notice, parent corner cuts are purely bounded to corners, but child corner cut bounds are "mirrored". Indeed, each child corner cut forms a mirrored path. This strategy was chosen in order for children could separately override the contact part of the corner cut.  

Also, note that **depth** and **length** depends on widget's layout direction (`LinearLayout.LAYOUT_DIRECTION_LTR` or `LinearLayout.LAYOUT_DIRECTION_RTL`) and orientation (`LinearLayout.VERTICAL` or `LinearLayout.HORIZONTAL`).

### Corner Cut Types

There are 5 default corner cut types for parent and children corners\*.

1) Oval.

<img src="/assets/images/oval.jpg" width="150" height="150"><img src="/assets/images/child_oval.jpg" width="300" height="150">

2) Oval Inverse.

<img src="/assets/images/oval_inverse.jpg" width="150" height="150"><img src="/assets/images/child_oval_inverse.jpg" width="300" height="150">

3) Rectangle.\*\*

<img src="/assets/images/rectangle.jpg" width="300" height="150"><img src="/assets/images/child_rectangle.jpg" width="300" height="150"><img src="/assets/images/child_rectangle_corners.jpg" width="300" height="150">

4) Rectangle Inverse.\*\*

<img src="/assets/images/rectangle_inverse.jpg" width="300" height="150"><img src="/assets/images/child_rectangle_inverse.jpg" width="300" height="150"><img src="/assets/images/child_rectangle_inverse_corners.jpg" width="300" height="150">

5) Bevel.

<img src="/assets/images/bevel.jpg" width="150" height="150"><img src="/assets/images/child_bevel.jpg" width="300" height="150">

>\* - Each child corner type, in fact, is mirrored and combined into a path from the respective corner cut types of contact children.\
\*\* - Rectangle types support an internal corner radius. There are respective attributes and view properties for both parent and child cuts.

### Layout Parameters

Each child can override parent defined properties and attributes related to the corner cuts.

In the examples below, parent `CornerCutLinearLayout` has `ccll_child_corner_cut_type` = `oval_inverse` and the middle children override each corner (different types) 

```xml
<io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
    ...
    app:ccll_child_corner_cut_type="oval_inverse">
    ...
    <View
        ...
        app:layout_ccll_start_top_corner_cut_type="oval"
        app:layout_ccll_end_top_corner_cut_type="bevel"
        app:layout_ccll_end_bottom_corner_cut_type="rectangle"
        app:layout_ccll_start_bottom_corner_cut_type="rectangle_inverse"/>
    ...
</io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout>
```

<img src="/assets/images/child_override_corner_cut_type.jpg" width="300" height="150">

#### Edge Child
There also special layout params for the first and last child. For example in a vertical orientation, when the top and bottom children are not aligned to parent top and bottom respectively they can override **contact cut\*** with the parent.

```xml
<io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
    ...
    android:gravity="center"
    app:ccll_child_corner_cut_type="oval_inverse">
    <View
        ...
        app:layout_ccll_edge_child_parent_contact_corner_cut_type="oval"/>
        ...
    <View
        ...
        app:layout_ccll_edge_child_parent_contact_corner_cut_type="rectangle_inverse"/>
</io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout>
```

<img src="/assets/images/edge_child_override_contact.jpg" width="300" height="150">


In case edge child is aligned to the respective side, they could optionally override parent corner cut type\*\*.

```xml
<io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
    ...
    app:ccll_corner_cut_type="bevel">
    <View
        ...
        app:layout_ccll_start_top_corner_cut_type="oval"
        app:layout_ccll_end_top_corner_cut_type="rectangle"
        app:layout_ccll_edge_child_could_override_parent_corner_cut_type_if_edge_aligned="true"/>
        ...
    <View
        ...
        app:layout_ccll_start_bottom_corner_cut_type="rectangle_inverse"
        app:layout_ccll_edge_child_could_override_parent_corner_cut_type_if_edge_aligned="true"/>
</io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout>
```

<img src="/assets/images/edge_child_aligned_override_parent_corners.jpg" width="150" height="150">


>\* - By default specified `ccll_child_corner_cut_type` is used for child-parent contact if not overridden by edge child.\
\*\* - Note that only type of parent corner type is overridden, while parent properties (depth, length, etc.) are stay preserved.

### Extra Child Corner Cut Properties

There are next extra child corner cut properties:
- Depth & Length Offset
- Corner Cut Rotation

#### Depth & Length Offset

Each side of child corner cuts could have different depth and length offset (see anatomy above).

**Example 1 - Depth Offset**

```xml
<io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
    ...
    app:ccll_child_corner_cut_depth_offset="@dimen/offset_24"
    app:ccll_child_corner_cut_type="oval_inverse">
    ...
    <View
        ...
        app:layout_ccll_end_bottom_corner_cut_type="oval"
        app:layout_ccll_end_top_corner_cut_type="rectangle_inverse"
        app:layout_ccll_start_bottom_corner_cut_type="bevel"
        app:layout_ccll_start_top_corner_cut_type="rectangle" />
    ...
</io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout>
```
<img src="/assets/images/child_property_offset.png" width="150" height="150">

**Example 2 - Depth & Length Offset**
```xml
<io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
    ...
    app:ccll_child_corner_cut_type="bevel"
    app:ccll_child_end_side_corner_cut_depth_offset="@dimen/depth_offset"
    app:ccll_child_start_side_corner_cut_length_offset="@dimen/length_offset"
    app:ccll_corner_cut_type="bevel">
    <View
        ...
        android:layout_marginTop="@dimen/offset_8"
        android:layout_marginBottom="@dimen/offset_8"/>
    <View
        ...
        android:layout_marginTop="@dimen/offset_8"
        android:layout_marginBottom="@dimen/offset_8"/>

    <View
        ...
        android:layout_marginTop="@dimen/offset_8"
        android:layout_marginBottom="@dimen/offset_8"/>
</io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout>
```

<img src="/assets/images/child_property_offset_depth_and_length.png" width="150" height="auto">

#### Rotation
Each side could have its corner cuts rotated be specified degree.
Corresponding attributes are:
- `ccll_child_start_side_corner_cut_rotation_degree`
- `ccll_child_end_side_corner_cut_rotation_degree`

Also, it might be necessary to keep the same mirrored corner cut angle for the both sides. For such purposes attribute `ccll_is_child_corner_cut_end_rotation_mirrored_from_start_rotation` might be helpful.

Each attribute has its corresponding `CornerCutLinearLayout`'s property and/or convenience function.

```xml
<io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
    ...
    app:ccll_child_corner_cut_type="oval"
    app:ccll_child_end_side_corner_cut_type="oval_inverse"
    app:ccll_child_end_side_corner_cut_rotation_degree="60"
    app:ccll_child_start_side_corner_cut_rotation_degree="45">
    ...
</io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout>
```

<img src="/assets/images/child_property_rotation.png" width="150" height="150">

### Shadow 
One of the main problem of [Android's shadow](https://developer.android.com/training/material/shadows-clipping) is that path must be [convex](https://developer.android.com/reference/android/graphics/Path#isConvex()). 

>\* - A path is convex if it has a single contour, and only ever curves in a single direction.

This widget allows bypass this limitation by automatically building complex shadow (event with cutouts). Of course, shadow is custom and has its pros and cons.

**Pros**:
- Shadow has custom properties, such as offset & color (ARGB).
- Supports complex non convex path.

**Cons**: 
- Shadow is artificial compared to native elevation shadow's nature. Thus, you cannot rely on global source an light position and elevation parameter. 
- Shadow uses view's area (padding), which you should keep in mind during layout process or dynamic change of shadow radius.
- Shadow does NOT depend on view's or children's background and their transparencies, thus cannot be a composite shadow with the overlays of different levels of transparency (opacity). 

By default shadow are build upon parent padded area combined with all cutouts data. It means that shadow does NOT depend on view's background, child presence or child's background. But this behavior could be changed by `CustomViewAreaProvider` (see **Advanced** section). 

**Shadow Padding**.\
You could also enable custom shadow auto padding (`ccll_is_custom_shadow_auto_padding_enabled`), allow or prevent custom shadow over user defined padding. (`ccll_could_draw_custom_shadow_over_user_defined_padding`). Last attribute works only in conjunction with enabled first attribute.

Examples:

<img src="/assets/images/shadow_no_children.jpg" width="150" height="auto"><img src="/assets/images/shadow_children.jpg" width="150" height="auto"><img src="/assets/images/shadow_no_children_semi_tranparent_bg.jpg" width="150" height="auto"><img src="/assets/images/shadow_children_single_bg.jpg" width="150" height="auto"><img src="/assets/images/shadow_view_transformation.jpg" width="150" height="auto"><img src="/assets/images/shadow_offset.jpg" width="150" height="auto">

### Custom Divider
Custom Divider has similar anatomy and properties as default's LinearLayout divider.
Custom dividers does not change view's dimension unlike  default's LinearLayout divider did (last adds space at specified by flag position equal to divider's width or height). Custom diviers are drown over view and default dividers. You can combine default and custom divider.

Custom dividers have several advantages though:
- additional show flags
- line caps (ROUND, BUTT, SQUARE)
- seperate start an end padding
- dashed line divider (width and gap)
- gravity of dashed divider (`CustomDividerGravity`: `START`, `CENTER`, `END`)
- custom divider provider (see **Advanced** section)

**Show Flags** (`CustomDividerShowFlag`):
- `container_beginning` - at view beginning
- `beginning` - between contact of first view's margin and parent.
- `middle` - between children contact margins
- `end` - between contact of last view's margin and parent.
- `container_end` - at view end

>By default custom dividers are not taken into consideration when shadow are build. 

Custom Divider attributes:

```xml
<io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
    ...
    app:ccll_custom_divider_color="@color/divider"
    app:ccll_custom_divider_dash_gap="@dimen/divider_dash_gap"
    app:ccll_custom_divider_dash_width="@dimen/divider_dash_width"
    app:ccll_custom_divider_height="@dimen/divider_height"
    app:ccll_custom_divider_line_cap="butt"
    app:ccll_custom_divider_show_flag="middle|container_end"
    app:ccll_custom_divider_gravity="center"
    app:ccll_custom_divider_padding="@dimen/divider_padding"
    app:ccll_custom_divider_padding_start="@dimen/divider_padding_start"
    app:ccll_custom_divider_padding_end="@dimen/divider_padding_end">
    ...
</io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout>
```

Examples:

<img src="/assets/images/divider_example_1.jpg" width="300" height="auto"><img src="/assets/images/divider_example_2.jpg" width="300" height="auto"><img src="/assets/images/divider_example_3.jpg" width="300" height="auto">

# Advanced
Sometimes you might want to have even more complex visible area, divider, cutouts, etc.
For such purposes there are custom providers for aforementioned subjects. All of them could be specified programatically. For your convenience, there are also a Kotlin lambda-style functions for the most of the providers.

### Corner Cut Provider
`CornerCutProvider` allows to override each of 4 widget's corners.\
Let's look at an examples.

**Example 1**.

1. As usual define our view at xml (this scenario) or create & setup it programatically.
```xml
<io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
    ...
    android:id="@+id/ccll_corner_cut_provider"
    app:ccll_corner_cut_depth="@dimen/corner_cut_depth"
    app:ccll_corner_cut_length="@dimen/corner_cut_length"
    app:ccll_corner_cut_type="bevel"/>
```
2. Set a `CornerCutProvider`. 
```kotlin
ccll_corner_cut_provider.setCornerCutProvider { view, cutout, cutCorner, rectF ->
    when (cutCorner) {
        CornerCutFlag.START_TOP -> {
            rectF.inset(inset, inset) // inset - globally defined property
            cutout.moveTo(rectF.left, rectF.top)
            cutout.lineTo(rectF.right, rectF.top)
            cutout.lineTo(rectF.left, rectF.bottom)
            true // accept left top corner
        }
        
        CornerCutFlag.END_BOTTOM -> {
            // complex pacman path
            ... 
            true // accept right bottom corner
        }
        
        else -> false // skip the rest of the corners and treat them by default settings
    }
}
```

Here, we simply override left top and right bottom corner cuts with the custom ones. They are accepted by returning `true` as a last statement, otherwise respective corner would be handled by its default settings (if any).

When is necessary to compose `cutout` path with nested cutout pathes, use either `Path.addPath()` function (with different `fillType`) or `Path.op()` function with different `Path.Op` modes. 

The result would be as follow:

<img src="/assets/images/corner_cut_provider_example_1.jpg" width="300" height="auto">

**Example 2**.
In some scenario you might need transform your cutout path (scale, rotate, skew, etc). For this purposes there is also optional function `getTransformationMatrix()` of `CornerCutProvider` interface.\
In this example, there are 3 simple views with different background color below `CornerCutLinearLayout` with `CornerCutProvider`.

Also, as you can see from the image below, you could achieve animated effects by calling public function `invalidateCornerCutPath()` whenever it is necessary to update your view after changing your custom cutout relative values.

Aforementioned views become visible through cutout. Moreover shadow is also properly drawn around cutout. And this cutout exceeds its recommended by `rectF` bounds. For such purposes better use `CustomCutoutProvider`. 

```kotlin
ccll_corner_cut_example_2.setCornerCutProvider(
            { view, _, _, _ ->
                val matrix = Matrix()
                val pb = view.paddedBounds
                matrix.postRotate(currentRotationAngle, pb.centerX(), pb.centerY())
                matrix // returns matrix that will be applied to cutout path. Null by default
            },
            
            { view, cutout, cutCorner, rectF ->
                when (cutCorner) {
                    CornerCutLinearLayout.CornerCutFlag.START_TOP -> {
                        with(view.paddedBounds) {
                            cutout.addRect(
                                centerX() - rectF.width() / 2.0F,
                                centerY() - rectF.height() / 2.0F,
                                centerX() + rectF.width() / 2.0F,
                                centerY() + rectF.height() / 2.0F,
                                Path.Direction.CW
                            )
                        }
                        true
                    }

                    else -> false
                }
            }
        )
```

<img src="/assets/images/corner_cut_provider_example_2.gif" width="300" height="auto">

### Child Corner Cut Provider
`ChildCornerCutProvider` is almost the same as `CornerCutProvider`. Instead of `cutCorner` it has `cutEdge` and additionally possible contact children - `relativeCutTopChild` & `relativeCutBottomChild`.

**Example 1**.

```xml
<io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
    ...
    android:id="@+id/ccll_child_cut_provider_example_1"
    app:ccll_child_side_cut_flag="start"
    app:ccll_corner_cut_flag="none">
    ...
</io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout>
```

```kotlin
ccll_child_cut_provider_example_1.setChildCornerCutProvider { view, cutout, _, rectF, _, _ ->
    with(cutout) {
        moveTo(rectF.centerX(), rectF.top)
        arcTo(...)
        lineTo(rectF.centerX() + rectF.width(), rectF.bottom)
        arcTo(...)
        lineTo(rectF.centerX(), rectF.top)
        val halfChordWidth = rectF.height() / 2.0F
        addCircle(...)
        moveTo(rectF.centerX() + rectF.width(), rectF.top)
        lineTo(view.paddedBounds.right - rectF.width() / 2.0F, rectF.centerY())
        lineTo(rectF.centerX() + rectF.width(), rectF.bottom)
        lineTo(rectF.centerX() + rectF.width(), rectF.top)
    }
    true // accept custom cutout
}
```

The result would be as follow:

<img src="/assets/images/child_corner_cut_provider_example_1.jpg" width="300" height="auto">

> Note that in this example only left side corner cuts are build with custom cutout provider. This is because providers is only called for `cutSide`'s & `cutCorner`'s specified by `ccll_child_side_cut_flag` & `ccll_corner_cut_flag`, respectively.

**Example 2**.
```xml
<io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
    ...
    android:id="@+id/ccll_child_cut_provider_example_2"
    app:ccll_child_corner_cut_type="rectangle_inverse"
    app:ccll_corner_cut_flag="none">
    ...
</io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout>
```
```kotlin
ccll_showcase_custom_child_cut_provider_mixed.setChildCornerCutProvider(
    { _, _, cutSide, rectF, _, _ ->
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
        matrix // return transformation matrix that will be applied over previously defined cutouts
    },
    
    { view, cutout, cutSide, rectF, relativeCutTopChild, _ ->
        when (cutSide) {
            CornerCutLinearLayout.ChildSideCutFlag.START -> {
                if (view.indexOfChild(relativeCutTopChild ?: return@setChildCornerCutProvider false) != 1) return@setChildCornerCutProvider false
                // cutout star path
                true // accept path for only 2 (index 1) start side cutout
            }
            CornerCutLinearLayout.ChildSideCutFlag.END -> {
                if (view.indexOfChild(relativeCutTopChild ?: return@setChildCornerCutProvider false) != 0) return@setChildCornerCutProvider false
                // cutout star path
                true // accept path for only 1 (index 0) end side cutout
            }
            else -> false
        }
    }
)
```

The result would be as follow:

<img src="/assets/images/child_corner_cut_provider_example_2.jpg" width="300" height="auto">

### Custom Cutout Provider
This type of provider (`CustomCutoutProvider`) is similar to previous cut providers. The only difference is that you could add many cutout providers and the `rectF` parameter in a both interface's functions return view's padded bounds.

### Custom View Area Provider
`CustomViewAreaProvider` might be useful in case you need to show custom area of view. Posibilities are only limited by your imagination and Android hardware. May require little knowledge of path composition, path op modes, fill types, etc.

**Example 1**.
```xml
<io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
    ...
    android:id="@+id/ccll_custom_view_area_provider_example_1"
    app:ccll_child_side_cut_flag="none"
    app:ccll_corner_cut_flag="none"
    app:ccll_custom_shadow_color="@color/shadow_color"
    app:ccll_custom_shadow_radius="@dimen/shadow_radius">

    <TextView
        ...
        android:layout_marginEnd="@dimen/offset_48"
        android:ellipsize="end"
        android:padding="@dimen/offset_16"
        android:text="Lorem ipsum dolor sit amet..." />
</io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout>
```
```kotlin
ccll_custom_view_area_provider_example_1.setCustomViewAreaProvider { view, path, rectF ->
    // properties
    val offset = view[0].marginEnd
    val cornerRadius = rectF.height() / 4.0F
    val tailCircleRadius = cornerRadius / 2.0F
    val innerTailCircleRadius = tailCircleRadius / 2.0F
    val smallCornerRadius = cornerRadius / 4.0F
    
    // left part: round rect
    path.addRoundRect(...)
    
    // right part: tail
    path.moveTo(rectF.right - offset, rectF.top + cornerRadius)
    path.arcTo(...)
    path.lineTo(rectF.right - tailCircleRadius, rectF.centerY() - innerTailCircleRadius)
    path.lineTo(rectF.right - offset + innerTailCircleRadius, rectF.centerY() - innerTailCircleRadius)
    path.arcTo(...)
    path.lineTo(rectF.right - tailCircleRadius, rectF.centerY() + innerTailCircleRadius)
    path.arcTo(...)
    path.lineTo(rectF.right - offset, rectF.top + cornerRadius)
    path.addCircle(rectF.right - tailCircleRadius, rectF.centerY(), tailCircleRadius,  Path.Direction.CW)
    path.addCircle(rectF.right - tailCircleRadius, rectF.centerY(), innerTailCircleRadius, Path.Direction.CCW)
}
```

The result would be as follow:

<img src="/assets/images/view_area_provider_example_1.gif" width="500" height="auto">

**Example 2**.
```xml
<io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
    android:id="@+id/ccll_custom_view_area_provider_example_2"
    android:gravity="center_horizontal"
    android:orientation="horizontal"
    android:padding="@dimen/offset_16"
    app:ccll_child_side_cut_flag="none"
    app:ccll_corner_cut_flag="none"
    app:ccll_could_draw_custom_shadow_over_user_defined_padding="true"
    app:ccll_custom_shadow_color="@color/accent_secondary"
    app:ccll_custom_shadow_radius="@dimen/elevation_16"
    app:ccll_is_custom_shadow_auto_padding_enabled="false">

    <io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
        ...
        android:rotation="10"
        android:rotationX="35"
        android:translationX="24dp"
        app:ccll_child_side_cut_flag="none"
        app:ccll_corner_cut_type="oval_inverse"
        app:ccll_corner_cut_size="@dimen/corner_cut_size">
        ...
    </io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout>

    <io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
        ...
        app:ccll_corner_cut_size="@dimen/corner_cut_size_2"
        android:layout_marginStart="@dimen/offset_16"
        android:layout_marginEnd="@dimen/offset_48"/>

    <View
        ...
        android:background="#8010A7E8" />
</io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout>
```

In this example `CornerCutLinearLayout` has nested `CornerCutLinearLayout` (first two children) and simple view (3rd child).\
**First child** is transformed (constantly running animation): rotated around x, y & z sequentially.\
**Second child** is continuously animated (translation Y) as well. It also has its own `CustomCutoutProvider` in a form of a star.\
**Third child** is a regular view with semi-transparent background.

Also parent `CustomCutoutProvider` has `CustomCutoutProvider` set programatically (curved lines path). In this examples we want to build shadow upon only children visible area also modifying some of them (virtual corner cuts for 3rd child).

```kotlin
// 1. Add Custom Cutout Provider
val waveLineCutWidth = resources.getDimension(R.dimen.offset_12)
val waveLineHeight = resources.getDimension(R.dimen.offset_48)
val halfWaveLineHeight = waveLineHeight / 2.0F
val halfWaveLineCutWidth = waveLineCutWidth / 2.0F
ccll_custom_view_area_provider_example_2.addCustomCutoutProvider { _, cutout, rectF ->
    cutout.moveTo(rectF.left, rectF.centerY() - halfWaveLineCutWidth)
    cutout.lineTo(rectF.left + rectF.width() / 4.0F, rectF.centerY() - halfWaveLineCutWidth - halfWaveLineHeight)
    cutout.lineTo(rectF.right - rectF.width() / 4.0F, rectF.centerY() - halfWaveLineCutWidth + halfWaveLineHeight)
    cutout.lineTo(rectF.right, rectF.centerY() - halfWaveLineCutWidth)
    cutout.lineTo(rectF.right, rectF.centerY() + halfWaveLineCutWidth)
    cutout.lineTo(rectF.right - rectF.width() / 4.0F, rectF.centerY() + halfWaveLineCutWidth + halfWaveLineHeight)
    cutout.lineTo(rectF.left + rectF.width() / 4.0F, rectF.centerY() + halfWaveLineCutWidth - halfWaveLineHeight)
    cutout.lineTo(rectF.left, rectF.centerY() + halfWaveLineCutWidth)
    cutout.lineTo(rectF.left, rectF.centerY() - halfWaveLineCutWidth)
}

// 2. Set Custom View Area Provider
ccll_showcase_custom_view_area_provider_example_2.setCustomViewAreaProvider { view, path, _ ->
    view.forEach {
        tempPath.rewind()
        if (it is CornerCutLinearLayout) {
            tempPath.offset(-it.left.toFloat(), -it.top.toFloat())
            tempPath.addPath(it.viewAreaPath) // nested ccll visible area path
            tempPath.transform(it.matrix)
            tempPath.offset(it.left.toFloat(), it.top.toFloat())
        } else {
            tempRectF.set(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
            val childCornerRadius = min(tempRectF.width(), tempRectF.height()) / 6.0F
            tempPath.addRoundRect(tempRectF, childCornerRadius, childCornerRadius, Path.Direction.CW)
            tempPath.offset(-it.left.toFloat(), -it.top.toFloat())
            tempPath.transform(it.matrix)
            tempPath.offset(it.left.toFloat(), it.top.toFloat())
        }
        path.op(tempPath, Path.Op.UNION)
    }
}
```

Note that 3rd child is clipped virtually. So when its bound overlay another visible area bounds corners of 3rd child become visible. 

As you see custom shadow are build correctly upon custom visible view area (including children cutouts and transformations) & global custom cutouts.

The result would be as follow:

<img src="/assets/images/view_area_provider_example_2.gif" width="500" height="auto">

When you nest `CornerCutLinearLayout` in another `CornerCutLinearLayout` and work with `CustomViewAreaProvider` it might be necessary to get current visible view area path. The copy of it could be obtained via `CornerCutLinearLayout.viewAreaPath`\*. In similar manner widget's padded bounds could be obtained (`CornerCutLinearLayout.paddedBounds`).

>\* - custom shadow are build upon `viewAreaPath`.

### Custom Divider Provider
Sometimes you might want to have some not trivial different dividers at different positions mixed with default dividers. In a such scenario, `CustomDividerProvider` might come handy.

**Example 1**.
```xml
<io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
    android:id="@+id/ccll_custom_divider_provider_example_1"
    app:ccll_child_side_cut_flag="none"
    app:ccll_corner_cut_flag="all"
    app:ccll_corner_cut_type="oval"
    app:ccll_custom_divider_show_flag="container_beginning|middle|container_end"
    app:ccll_should_use_max_allowed_corner_cut_depth_or_length_to_be_equal="true"
    app:ccll_should_use_max_allowed_corner_cut_size="true">
    ...
</io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout>
```

Then we define globally divider paint:

```kotlin
ccll_custom_divider_provider_example_1.doOnNonNullSizeLayout {
    val pb = it.paddedBounds
    it.customDividerProviderPaint.shader = RadialGradient(
        pb.centerX(), pb.centerY(),
        hypot(pb.width() / 2.0F, pb.height() / 2.0F) * 0.8F,
        Color.BLACK, Color.WHITE,
        Shader.TileMode.CLAMP
    )
}
```

Lastly, we add `CustomDividerProvider`:

```kotlin
ccll_custom_divider_provider_example_1.setCustomDividerProvider { _, dividerPath, dividerPaint, showDividerFlag, dividerTypeIndex, rectF ->
    when (showDividerFlag) {
        CornerCutLinearLayout.CustomDividerShowFlag.CONTAINER_BEGINNING -> {
            dividerPaint.style = Paint.Style.STROKE
            dividerPaint.strokeWidth = triangleHeight
            dividerPaint.pathEffect = PathDashPathEffect(topDividerTrianglePath, triangleBaseWidth, 0.0F, PathDashPathEffect.Style.TRANSLATE)
            dividerPath.moveTo(rectF.left, rectF.top)
            dividerPath.lineTo(rectF.right, rectF.top)
        }

        CornerCutLinearLayout.CustomDividerShowFlag.MIDDLE -> {
            dividerPaint.style = Paint.Style.STROKE
            if (dividerTypeIndex == 0) {
                dividerPaint.strokeWidth = circleRadius
                dividerPaint.pathEffect = PathDashPathEffect(circleDotDividerPath, triangleBaseWidth, 0.0F, PathDashPathEffect.Style.TRANSLATE)
                dividerPath.moveTo(rectF.left, rectF.centerY())
                dividerPath.lineTo(rectF.right + triangleBaseWidth, rectF.centerY())
            } else {
                dividerPaint.strokeWidth = circleRadius
                dividerPaint.pathEffect = PathDashPathEffect(diamondDotDividerPath, triangleBaseWidth, 0.0F, PathDashPathEffect.Style.TRANSLATE)
                dividerPath.moveTo(rectF.left, rectF.centerY())
                dividerPath.lineTo(rectF.right + triangleBaseWidth, rectF.centerY())
            }
        }

        CornerCutLinearLayout.CustomDividerShowFlag.CONTAINER_END -> {
            dividerPaint.style = Paint.Style.STROKE
            dividerPaint.strokeWidth = triangleHeight
            dividerPaint.pathEffect = PathDashPathEffect(bottomDividerTrianglePath, triangleBaseWidth, 0.0F, PathDashPathEffect.Style.TRANSLATE)
            dividerPath.moveTo(rectF.left, rectF.top)
            dividerPath.lineTo(rectF.right, rectF.top)
        }
    }
    true // accept divider path
}
```

The result would be as follow:

<img src="/assets/images/divider_provider_example_1.jpg" width="300" height="auto">

**Example 2**.
This example shows the combination of custom and default's dividers. 

```xml
<io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout
    android:id="@+id/ccll_custom_divider_provider_example_2"
    app:ccll_child_corner_cut_type="oval_inverse"
    app:ccll_corner_cut_flag="all"
    app:ccll_custom_divider_color="@color/divider"
    app:ccll_custom_divider_dash_gap="@dimen/divider_dash_gap"
    app:ccll_custom_divider_dash_width="@dimen/divider_dash_width"
    app:ccll_custom_divider_height="@dimen/divider_height"
    app:ccll_custom_divider_line_cap="round"
    app:ccll_custom_divider_show_flag="middle"
    app:ccll_custom_shadow_color="@color/accent_secondary">
    ...
</io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout>
```
```kotlin
ccll_custom_divider_provider_example_2.setCustomDividerProvider { _, dividerPath, dividerPaint, showDividerFlag, dividerTypeIndex, rectF ->
    when (showDividerFlag) {
        CornerCutLinearLayout.CustomDividerShowFlag.MIDDLE -> {
            dividerPaint.style = Paint.Style.STROKE
            when (dividerTypeIndex) {
                0 -> {
                    dividerPaint.shader = RadialGradient(
                        rectF.centerX(), rectF.centerY(),
                        rectF.width() / 2.0F, Color.GREEN, Color.RED,
                        Shader.TileMode.MIRROR
                    )
                    dividerPaint.strokeWidth = circleRadius
                    dividerPaint.pathEffect = PathDashPathEffect(diamondDotDividerPath, triangleBaseWidth, 0.0F, PathDashPathEffect.Style.TRANSLATE)
                    dividerPath.moveTo(rectF.left, rectF.centerY())
                    dividerPath.lineTo(rectF.right + triangleBaseWidth, rectF.centerY())
                    return@setCustomDividerProvider true // accept divider and draw it
                }
                
                2 -> {
                    dividerPaint.shader = LinearGradient(
                        rectF.centerX(), rectF.centerY() - halfWaveHeight,
                        rectF.centerX(), rectF.centerY() + halfWaveHeight,
                        Color.BLUE, Color.YELLOW, Shader.TileMode.CLAMP
                    )
                    dividerPaint.strokeWidth = halfWaveHeight * 2.0F
                    dividerPaint.pathEffect = PathDashPathEffect( wavePath, halfWaveWidth * 2.0F, 0.0F, PathDashPathEffect.Style.TRANSLATE)
                    dividerPath.moveTo(rectF.left, rectF.centerY())
                    dividerPath.lineTo(rectF.right, rectF.centerY())
                    return@setCustomDividerProvider true // accept divider and draw it
                }
                
                else -> return@setCustomDividerProvider false // skip divider and draw default
            }
        }
    }
    false // skip divider and draw default
}
```

The result would be as follow:

<img src="/assets/images/divider_provider_example_2.jpg" width="300" height="auto">

## Sample App
In order to take closer look over examples provided and library in general, please run the sample app.

## License
Please see [LICENSE](/LICENSE.txt)