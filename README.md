# CornerCutLinearLayout

`CornerCutLinearLayout` extends [`LinearLayout`]
 (https://developer.android.com/reference/android/widget/LinearLayout). It allows cutting parent corners with different shapes and build proper shadow to complex shapes.\
It also allows cutting each child's corners.\
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

## Basics

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
    app:ccll_child_corner_cut_type="oval"
    app:ccll_child_end_side_corner_cut_type="oval_inverse"
    app:ccll_child_end_side_corner_cut_rotation_degree="60"
    app:ccll_child_start_side_corner_cut_rotation_degree="45">
    ...
</io.devlight.xtreeivi.cornercutlinearlayout.CornerCutLinearLayout>
```

<img src="/assets/images/child_property_rotation.png" width="150" height="150">

## Advanced

## License
Please see [LICENSE](/LICENSE.txt)