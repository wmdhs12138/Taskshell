package com.wmdhs.taskshell.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wmdhs.taskshell.mcp.McpServer

@Composable
fun OutputTextBlock(
    title: String,
    text: String,
    onCopy: () -> Unit,
    textColor: Color
) {
    val scrollState = rememberScrollState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onCopy, modifier = Modifier.height(30.dp)) {
                Text("Copy", style = MaterialTheme.typography.labelSmall)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                .verticalScrollbar(scrollState)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            SelectionContainer {
                Text(
                    text = text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(end = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = textColor
                )
            }
        }
    }
}

fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    widthDp: Float = 2.5f,
    minThumbHeightPx: Float = 24f
): Modifier = drawWithContent {
    drawContent()
    val maxScroll = scrollState.maxValue
    if (maxScroll <= 0) return@drawWithContent

    val viewportHeight = size.height
    val totalContentHeight = viewportHeight + maxScroll
    val thumbHeight = (viewportHeight * viewportHeight / totalContentHeight).coerceAtLeast(minThumbHeightPx)
    val thumbOffsetY = (scrollState.value.toFloat() / maxScroll.toFloat()) * (viewportHeight - thumbHeight)
    val barWidth = widthDp.dp.toPx()

    drawRoundRect(
        color = Color.Gray.copy(alpha = 0.22f),
        topLeft = Offset(size.width - barWidth, 0f),
        size = Size(barWidth, viewportHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth, barWidth)
    )
    drawRoundRect(
        color = Color.Gray.copy(alpha = 0.75f),
        topLeft = Offset(size.width - barWidth, thumbOffsetY),
        size = Size(barWidth, thumbHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth, barWidth)
    )
}
