package com.mtwabpin.kolahal.ui.assistant

import android.content.Context
import androidx.a2ui.compose.runtime.A2uiComponentReference
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiComponentState
import androidx.a2ui.compose.runtime.observeA2uiComponentState
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.model.protocol.A2uiExecutionContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults
import androidx.compose.material3.a2ui.catalog.materialA2uiBasicCatalogV1
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/*
 * The catalog an answer is drawn with: Google's Material 3 basic catalog, with Kolahal's look where its
 * builder lets a component be replaced (materialA2uiBasicCatalogV1's parameters). Replaced here: Text
 * (the app's type sizes, simple Markdown), Card (the settings rows' container color and 24 dp corners,
 * a card in a card a step lighter), Divider (the theme's outline), Icon (the theme's primary color) and
 * List (laid out whole, not lazily: a lazy list in the page's scrolling thread had no height limit and
 * crashed). Row, Column, Button, the inputs, Tabs and Modal are Material's own, with the theme's colors.
 * Nothing reaches out: pictures, video and sound show as a line of text, and openUrl does nothing.
 */

/** How many cards hold the one being drawn: a card in a card is a step lighter. */
private val LocalCardDepth = compositionLocalOf { 0 }

/** The catalog for answers. [context] is the application's, for the catalog's own functions (formatting). */
internal fun answerCatalog(context: Context): A2uiCatalog {
    // openUrl is the one function that reaches outside: it does nothing here.
    val functions = object : A2uiBasicCatalogV1.Functions by A2uiBasicCatalogV1.Functions(context) {
        override fun openUrl(url: String, executionContext: A2uiExecutionContext) {}
    }
    return materialA2uiBasicCatalogV1(
        image = MaterialA2uiBasicCatalogV1Defaults.image { url, description, _, modifier, _ ->
            Note("[picture: ${description ?: url}]", modifier)
        },
        video = MaterialA2uiBasicCatalogV1Defaults.video { url, modifier, _ -> Note("[video: $url]", modifier) },
        audioPlayer = MaterialA2uiBasicCatalogV1Defaults.audioPlayer { url, description, modifier, _ ->
            Note("[sound: ${description ?: url}]", modifier)
        },
        functions = functions,
        text = AnswerTextComponent,
        card = AnswerCard,
        divider = AnswerDivider,
        icon = AnswerIcon,
        list = AnswerList,
    )
}

@Composable
private fun Note(text: String, modifier: Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Text in the app's sizes: one title (h1, h2), headings (h3 to h5), body and captions. */
private object AnswerTextComponent : A2uiBasicCatalogV1.Text {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        text: String,
        variant: A2uiBasicCatalogV1.Text.Variant,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val type = MaterialTheme.typography
        val colors = MaterialTheme.colorScheme
        val (style: TextStyle, heading) = when (variant) {
            A2uiBasicCatalogV1.Text.Variant.H1 -> type.headlineSmall to true
            A2uiBasicCatalogV1.Text.Variant.H2 -> type.titleLarge to true
            A2uiBasicCatalogV1.Text.Variant.H3 -> type.titleLarge to false
            A2uiBasicCatalogV1.Text.Variant.H4 -> type.titleSmall.copy(color = colors.primary) to true
            A2uiBasicCatalogV1.Text.Variant.H5 -> type.labelLarge to true
            A2uiBasicCatalogV1.Text.Variant.Caption -> type.labelMedium.copy(color = colors.onSurfaceVariant) to false
            A2uiBasicCatalogV1.Text.Variant.Body -> type.bodyMedium to false
        }
        val shown = remember(text, colors.primary) { answerText(text, block = false, code = colors.primary) }
        Text(
            shown,
            modifier.then(if (heading) Modifier.semantics { heading() } else Modifier),
            style = style,
        )
    }
}

/** A card as the settings rows are: the raised container color, 24 dp corners, no border. */
private object AnswerCard : A2uiBasicCatalogV1.Card {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        childId: String,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val depth = LocalCardDepth.current
        val colors = MaterialTheme.colorScheme
        Surface(
            modifier = modifier,
            color = if (depth == 0) colors.surfaceContainerHigh else colors.surfaceContainerHighest,
            shape = RoundedCornerShape(if (depth == 0) 24.dp else 16.dp),
        ) {
            CompositionLocalProvider(LocalCardDepth provides depth + 1) {
                Child(observeA2uiComponentState(childId), Modifier.padding(16.dp))
            }
        }
    }
}

/** A thin line in the theme's outline color. */
private object AnswerDivider : A2uiBasicCatalogV1.Divider {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        axis: A2uiBasicCatalogV1.Divider.Axis,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val color = MaterialTheme.colorScheme.outlineVariant
        when (axis) {
            A2uiBasicCatalogV1.Divider.Axis.Horizontal -> HorizontalDivider(modifier.padding(vertical = 4.dp), color = color)
            A2uiBasicCatalogV1.Divider.Axis.Vertical -> VerticalDivider(modifier.padding(horizontal = 4.dp), color = color)
        }
    }
}

/** Material's icon, in the theme's primary color and 20 dp, the size of an icon beside a line of text. */
private object AnswerIcon : A2uiBasicCatalogV1.Icon {
    private val base = MaterialA2uiBasicCatalogV1Defaults.icon

    @Composable
    override fun A2uiComponentScope.TypedContent(
        source: A2uiBasicCatalogV1.Icon.Source,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val scope = this
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
            with(base) { scope.TypedContent(source, accessibility, modifier.size(20.dp)) }
        }
    }
}

/**
 * A list laid out whole: a column, or a row that scrolls sideways. Never lazy, so a list inside the
 * thread, or inside another list, always has a height.
 */
private object AnswerList : A2uiBasicCatalogV1.List {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        children: List<A2uiComponentReference>,
        direction: A2uiBasicCatalogV1.List.Direction,
        align: A2uiBasicCatalogV1.List.Align,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val stretch = align == A2uiBasicCatalogV1.List.Align.Stretch
        if (direction == A2uiBasicCatalogV1.List.Direction.Vertical) {
            Column(
                modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = when (align) {
                    A2uiBasicCatalogV1.List.Align.Center -> Alignment.CenterHorizontally
                    A2uiBasicCatalogV1.List.Align.End -> Alignment.End
                    else -> Alignment.Start
                },
            ) {
                children.forEach { ref ->
                    key(ref.id, ref.baseDataPath) {
                        Child(observeA2uiComponentState(ref), if (stretch) Modifier.fillMaxWidth() else Modifier)
                    }
                }
            }
        } else {
            Row(
                modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = when (align) {
                    A2uiBasicCatalogV1.List.Align.Center -> Alignment.CenterVertically
                    A2uiBasicCatalogV1.List.Align.End -> Alignment.Bottom
                    else -> Alignment.Top
                },
            ) {
                children.forEach { ref ->
                    key(ref.id, ref.baseDataPath) { Child(observeA2uiComponentState(ref), Modifier) }
                }
            }
        }
    }
}

/** A child component: drawn, or a small error in its place; nothing while it loads. */
@Composable
private fun Child(state: A2uiComponentState, modifier: Modifier) {
    when (state) {
        is A2uiComponentState.Success -> A2uiComponent(component = state.component, modifier = modifier)
        is A2uiComponentState.Error -> Surface(
            modifier,
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small,
        ) { Text(state.exception.message.orEmpty(), Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
        is A2uiComponentState.Loading -> Box(modifier)
    }
}
