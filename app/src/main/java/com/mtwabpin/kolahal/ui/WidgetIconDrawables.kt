/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.widgets.WidgetIcons

/**
 * The drawable of the widget icon [name] ([WidgetIcons]), or null when [name] is no icon name: then
 * the text is drawn as it is (an emoji or a letter). WidgetIconsTest checks that every name has one.
 */
internal fun widgetIconDrawable(name: String): Int? = when (name) {
    "sun" -> R.drawable.wi_sun
    "moon" -> R.drawable.wi_moon
    "cloud" -> R.drawable.wi_cloud
    "rain" -> R.drawable.wi_rain
    "snow" -> R.drawable.wi_snow
    "storm" -> R.drawable.wi_storm
    "wind" -> R.drawable.wi_wind
    "fog" -> R.drawable.wi_fog
    "temp" -> R.drawable.wi_temp
    "drop" -> R.drawable.wi_drop
    "lock" -> R.drawable.wi_lock
    "unlock" -> R.drawable.wi_unlock
    "bell" -> R.drawable.wi_bell
    "alarm" -> R.drawable.wi_alarm
    "timer" -> R.drawable.wi_timer
    "clock" -> R.drawable.wi_clock
    "calendar" -> R.drawable.wi_calendar
    "home" -> R.drawable.wi_home
    "work" -> R.drawable.wi_work
    "wifi" -> R.drawable.wi_wifi
    "battery" -> R.drawable.wi_battery
    "bolt" -> R.drawable.wi_bolt
    "torch" -> R.drawable.wi_torch
    "phone" -> R.drawable.wi_phone
    "mail" -> R.drawable.wi_mail
    "chat" -> R.drawable.wi_chat
    "quote" -> R.drawable.wi_quote
    "music" -> R.drawable.wi_music
    "play" -> R.drawable.wi_play
    "pause" -> R.drawable.wi_pause
    "heart" -> R.drawable.wi_heart
    "star" -> R.drawable.wi_star
    "check" -> R.drawable.wi_check
    "flag" -> R.drawable.wi_flag
    "fire" -> R.drawable.wi_fire
    "leaf" -> R.drawable.wi_leaf
    "cart" -> R.drawable.wi_cart
    "money" -> R.drawable.wi_money
    "chart" -> R.drawable.wi_chart
    "up" -> R.drawable.wi_up
    "down" -> R.drawable.wi_down
    "car" -> R.drawable.wi_car
    "bus" -> R.drawable.wi_bus
    "train" -> R.drawable.wi_train
    "plane" -> R.drawable.wi_plane
    "walk" -> R.drawable.wi_walk
    "pin" -> R.drawable.wi_pin
    "globe" -> R.drawable.wi_globe
    "news" -> R.drawable.wi_news
    "book" -> R.drawable.wi_book
    "code" -> R.drawable.wi_code
    "bug" -> R.drawable.wi_bug
    "person" -> R.drawable.wi_person
    "people" -> R.drawable.wi_people
    "trophy" -> R.drawable.wi_trophy
    "ball" -> R.drawable.wi_ball
    "cricket" -> R.drawable.wi_cricket
    "parcel" -> R.drawable.wi_parcel
    "gift" -> R.drawable.wi_gift
    "coffee" -> R.drawable.wi_coffee
    "dnd" -> R.drawable.wi_dnd
    "feed" -> R.drawable.wi_feed
    "link" -> R.drawable.wi_link
    else -> null
}

/**
 * A widget's [icon]: the drawable of an icon name in [colour], [size] across, or else the text (an
 * emoji or a letter) in [style]. An icon says nothing to a screen reader: the label beside it, or
 * the card's description, does.
 */
@Composable
internal fun WidgetIconText(
    icon: String,
    style: TextStyle,
    colour: Color = LocalContentColor.current,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    val drawable = widgetIconDrawable(icon)
    if (drawable != null) Icon(
        painterResource(drawable),
        contentDescription = null,
        tint = colour,
        modifier = modifier.size(size)
    )
    else Text(
        icon,
        style = style,
        color = colour,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
    )
}
