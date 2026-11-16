package ru.franh.modemmon.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.franh.modemmon.ui.theme.Muted

/**
 * Общая обёртка экрана: прокрутка, отступы и подсказка, если нет сессии ubus.
 * Позволяет не дублировать «Нужен вход» в каждом экране управления.
 */
@Composable
fun ScreenShell(
    connected: Boolean,
    hint: String = "Откройте «Опции» → «Управление (ubus)» и войдите с паролем роутера.",
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        if (!connected) {
            CardBox {
                SectionLabel("Нужен вход")
                Text(hint, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        } else {
            content()
        }
        Spacer(Modifier.height(12.dp))
    }
}
