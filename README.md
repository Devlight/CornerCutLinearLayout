# CornerCutLinearLayout

`CornerCutLinearLayout` extends [`LinearLayout`]
 (https://developer.android.com/reference/android/widget/LinearLayout). It allows cutting parent corners with different shapes.\
It also allows cutting each child's corners.\
Additionally, using available properties and custom providers, those cuts may be turned into cutouts of different shapes, sizes, etc.\
Widget's sole purpose is to use with children with no transformations (like rotation, scale, matrix transformations).

Amongst additional features:
- child layout parameters that allow overriding default parent parameters.
- custom shadow
- custom dividers & providers
- custom cutouts & providers

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

![CornerCutLinearLayout](/assets/images/first_usage.jpg){:height="100" width="100"}


## Advanced

## License
Please see [LICENSE](/LICENSE.txt)