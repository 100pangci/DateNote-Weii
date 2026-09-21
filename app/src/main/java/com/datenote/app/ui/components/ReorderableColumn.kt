package com.datenote.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.datenote.app.ui.theme.AppSpacing
import kotlin.math.abs
import kotlin.math.roundToInt

/** Raw row ids for [ReorderableColumn]. */
typealias RowIds = List<Long>

/**
 * Identifies a row for [ReorderableColumn].
 *
 * The id must stay stable while the list is edited, because every edit recreates
 * the item object. Using object identity would restart the drag gesture and stop
 * the drag after a single swap.
 */
interface ReorderableItem {
    val stableId: Long
}

/**
 * A vertical reorder surface for editable lists.
 *
 * Only the handle supplied to [itemContent] starts a drag, and the drag starts as
 * soon as the finger moves past a small threshold, so the neighbouring text field
 * can still be tapped. Rows keep their own slot positions derived from measured
 * heights, so a live reorder never disturbs measurement: the dragged row follows
 * the finger, the remaining rows spring into the new slots, and the freed slot is
 * shown as a dashed placeholder.
 */
@Composable
@JvmName("ReorderableIdColumn")
fun ReorderableColumn(
    items: RowIds,
    modifier: Modifier = Modifier,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragStart: () -> Unit = {},
    itemContent: @Composable (id: Long, dragHandleModifier: Modifier) -> Unit,
) {
    ReorderableIdColumn(
        items = items,
        modifier = modifier,
        onMove = onMove,
        onDragStart = onDragStart,
        itemId = { it },
        itemContent = itemContent,
    )
}

/** [ReorderableColumn] for items that expose their own [ReorderableItem.stableId]. */
@Composable
fun <T : ReorderableItem> ReorderableColumn(
    items: List<T>,
    modifier: Modifier = Modifier,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragStart: () -> Unit = {},
    itemContent: @Composable (item: T, dragHandleModifier: Modifier) -> Unit,
) {
    ReorderableIdColumn(
        items = items,
        modifier = modifier,
        onMove = onMove,
        onDragStart = onDragStart,
        itemId = { it.stableId },
        itemContent = itemContent,
    )
}

@Composable
private fun <T> ReorderableIdColumn(
    items: List<T>,
    modifier: Modifier,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragStart: () -> Unit,
    itemId: (T) -> Long,
    itemContent: @Composable (item: T, dragHandleModifier: Modifier) -> Unit,
) {
    val localDensity = LocalDensity.current
    val spacingPx = with(localDensity) { AppSpacing.Tight.roundToPx() }
    // Measured content height of every row, kept per stable id.
    val itemHeightMap = remember { mutableStateMapOf<Long, Int>() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    // Frozen top of the dragged row when the gesture started.
    var draggingStartTopPx by remember { mutableFloatStateOf(0f) }
    // Finger movement in the current gesture; the absolute position is start + this.
    var draggingDeltaPx by remember { mutableFloatStateOf(0f) }

    fun resetDrag() {
        draggingId = null
        draggingStartTopPx = 0f
        draggingDeltaPx = 0f
    }

    /** Slot positions of the current order, independent of the rendered container order. */
    fun layoutMapOf(currentItems: List<T>): Map<Long, ReorderableItemLayout> {
        var top = 0f
        return buildMap {
            currentItems.forEach { item ->
                val id = itemId(item)
                val contentHeight = itemHeightMap[id] ?: 0
                put(
                    id,
                    ReorderableItemLayout(
                        top = top,
                        contentHeight = contentHeight,
                        slotHeight = if (contentHeight == 0) 0 else contentHeight + spacingPx,
                    ),
                )
                top += contentHeight + spacingPx
            }
        }
    }

    /**
     * Where the dragged row should be inserted.
     *
     * The dragged row is removed mentally, the remaining rows close the gap, and
     * the dragged row's top is compared against their vertical centers. Using the
     * top means a tall row does not have to travel far to swap.
     */
    fun findInsertionIndex(currentItems: List<T>, draggedId: Long, draggedTopPx: Float): Int {
        var insertionIndex = 0
        var currentTopPx = 0f
        currentItems.forEach { item ->
            val id = itemId(item)
            if (id != draggedId) {
                val height = itemHeightMap[id] ?: 0
                if (currentTopPx + height / 2f < draggedTopPx) insertionIndex++
                currentTopPx += height + spacingPx
            }
        }
        return insertionIndex
    }

    fun updateDragTarget() {
        val currentItems = items
        val draggedId = draggingId
        if (draggedId != null) {
            val currentIndex = currentItems.indexOfFirst { itemId(it) == draggedId }
            if (currentIndex >= 0) {
                val draggedTopPx = draggingStartTopPx + draggingDeltaPx
                val targetIndex = findInsertionIndex(currentItems, draggedId, draggedTopPx)
                    .coerceIn(0, currentItems.lastIndex)
                if (targetIndex != currentIndex) onMove(currentIndex, targetIndex)
            }
        }
    }

    val latestUpdateDragTarget by rememberUpdatedState(newValue = { updateDragTarget() })
    val placeholderColor = MaterialTheme.colorScheme.primary
    val dragStartCallback by rememberUpdatedState(newValue = onDragStart)

    val currentItems = items
    val layoutMap = layoutMapOf(currentItems)

    // Rows measure themselves freely, then place themselves with their own offsets.
    // The reserved height comes from those measurements, so this layout must never
    // pass a height derived from [itemHeightMap] down as a constraint: on the first
    // pass the map is still empty and a fixed zero height would collapse every row.
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            currentItems.forEach { item ->
                val stableId = itemId(item)
                key(stableId) {
                    val layout = layoutMap[stableId]
                    if (layout != null) {
                        val isDragging = draggingId == stableId
                        val isTargetMeasured = itemHeightMap.containsKey(stableId)
                        val itemOffset by layoutDrivenOffset(
                            isDragging = isDragging,
                            dragOffset = IntOffset(
                                x = 0,
                                y = (draggingStartTopPx + draggingDeltaPx).roundToInt(),
                            ),
                            targetOffset = IntOffset(x = 0, y = layout.top.roundToInt()),
                            isTargetMeasured = isTargetMeasured,
                            animateTarget = draggingId != null,
                        )
                        // The freed slot keeps sliding to its latest position, even while
                        // other rows spring away, so the dashed frame does not chase them.
                        val placeholderOffset by layoutDrivenOffset(
                            isDragging = false,
                            dragOffset = IntOffset.Zero,
                            targetOffset = IntOffset(x = 0, y = layout.top.roundToInt()),
                            isTargetMeasured = isTargetMeasured,
                            animateTarget = false,
                        )
                        val placeholderAlpha by animateFloatAsState(
                            targetValue = if (isDragging) 1f else 0f,
                            animationSpec = tween(durationMillis = 120),
                            label = "reorderPlaceholderAlpha",
                        )
                        val itemScale by animateFloatAsState(
                            targetValue = if (isDragging) 1.01f else 1f,
                            label = "reorderItemScale",
                        )
                        val itemAlpha by animateFloatAsState(
                            targetValue = if (isDragging) 0.96f else 1f,
                            label = "reorderItemAlpha",
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(with(localDensity) { layout.slotHeight.toDp() })
                                .offset { placeholderOffset }
                                .alpha(placeholderAlpha)
                                .drawBehind {
                                    val cornerRadius = 12.dp.toPx()
                                    drawRoundRect(
                                        color = placeholderColor.copy(alpha = 0.5f),
                                        cornerRadius = CornerRadius(x = cornerRadius, y = cornerRadius),
                                        style = Stroke(
                                            width = 1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(
                                                intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                                                phase = 0f,
                                            ),
                                        ),
                                    )
                                },
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { itemOffset }
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    scaleX = itemScale
                                    scaleY = itemScale
                                    alpha = itemAlpha
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { size ->
                                        if (itemHeightMap[stableId] != size.height) {
                                            itemHeightMap[stableId] = size.height
                                        }
                                    },
                            ) {
                                itemContent(
                                    item,
                                    Modifier.pointerInput(stableId) {
                                        reorderDragGesture(
                                            onDragStart = {
                                                val startLayout = layoutMap[stableId]
                                                if (startLayout != null) {
                                                    draggingId = stableId
                                                    draggingStartTopPx = startLayout.top
                                                    draggingDeltaPx = 0f
                                                    dragStartCallback()
                                                }
                                            },
                                            onDragEnd = { resetDrag() },
                                            onDragCancel = { resetDrag() },
                                        ) { change, dragAmount ->
                                            if (draggingId == stableId) {
                                                change.consume()
                                                draggingDeltaPx += dragAmount.y
                                                latestUpdateDragTarget()
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val layoutWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val rowConstraints = Constraints(
            minWidth = layoutWidth,
            maxWidth = layoutWidth,
            minHeight = 0,
            maxHeight = Constraints.Infinity,
        )
        val placeables = measurables.map { measurable -> measurable.measure(rowConstraints) }
        // Children always come in (placeholder, row) pairs, one pair per item.
        val rowHeights = placeables.filterIndexed { index, _ -> index % 2 == 1 }.map { it.height }
        val totalHeight = rowHeights.sum() + spacingPx * (rowHeights.size - 1).coerceAtLeast(0)
        layout(layoutWidth, totalHeight) {
            placeables.forEach { placeable -> placeable.placeRelative(0, 0) }
        }
    }
}

/**
 * Immediate drag gesture.
 *
 * Unlike `detectDragGestures` this does not consume the first down event, so a
 * plain tap on the neighbouring text field is not swallowed; the drag is claimed
 * as soon as the finger moves past the directional threshold.
 */
private suspend fun PointerInputScope.reorderDragGesture(
    onDragStart: (startPosition: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    val touchSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var accumulated = Offset.Zero
        var dragging = false
        var cancelled = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (dragging) onDragEnd() else onDragCancel()
                break
            }
            val delta = change.position - change.previousPosition
            if (!dragging && !cancelled) {
                accumulated += delta
                val passed = abs(accumulated.y) > touchSlop && abs(accumulated.y) > abs(accumulated.x)
                if (passed) {
                    dragging = true
                    change.consume()
                    onDragStart(down.position)
                } else if (abs(accumulated.x) > touchSlop) {
                    // Horizontal movement belongs to something else; give the gesture back.
                    cancelled = true
                }
            }
            if (dragging) {
                onDrag(change, Offset(x = 0f, y = delta.y))
            }
        }
    }
}

/**
 * Layout driven offset.
 *
 * The first measurement and the drag start position with [snap] so content never
 * jumps; a live reorder springs into the new slot instead.
 */
@Composable
private fun layoutDrivenOffset(
    isDragging: Boolean,
    dragOffset: IntOffset,
    targetOffset: IntOffset,
    isTargetMeasured: Boolean,
    animateTarget: Boolean,
): State<IntOffset> = animateIntOffsetAsState(
    targetValue = if (isDragging) dragOffset else targetOffset,
    animationSpec = if (isDragging || !isTargetMeasured || !animateTarget) {
        snap()
    } else {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )
    },
    label = "reorderItemOffset",
)

private data class ReorderableItemLayout(
    val top: Float,
    /** Height of the row content itself. */
    val contentHeight: Int,
    /** Content plus the gap to the next row, used by the placeholder frame. */
    val slotHeight: Int,
) {
    val bottomPx: Float
        get() = top + contentHeight
}
