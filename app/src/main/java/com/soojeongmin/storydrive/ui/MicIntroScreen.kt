package com.soojeongmin.storydrive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.soojeongmin.storydrive.R

@Composable
fun MicIntroScreen(onAllow: () -> Unit, onLater: () -> Unit) {
    BackHandler(onBack = onLater)
    val t = LocalTokens.current
    Column(Modifier.fillMaxSize().background(t.bg)) {
        PageHeader(stringResource(R.string.app_name), leadingIcon = R.drawable.ic_book)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.margin)) {
            Column(Modifier.widthIn(max = 1000.dp)) {
                VGap(Space.xxl)
                Box(Modifier.size(96.dp).clip(CircleShape).background(t.surfaceHigh), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_mic), contentDescription = null, tint = t.text, modifier = Modifier.size(56.dp))
                }
                VGap(Space.xl)
                Text(stringResource(R.string.mic_title), style = Type.headline, color = t.text)
                VGap(Space.s)
                Muted(stringResource(R.string.mic_body))
                VGap(Space.xl)
                ListContainer {
                    MicReason(R.string.mic_use_kids)
                    Divider()
                    MicReason(R.string.mic_use_driver)
                    Divider()
                    MicReason(R.string.mic_use_privacy)
                }
                VGap(Space.xxl)
            }
        }
        Row(
            Modifier.padding(horizontal = Space.margin, vertical = Space.l).widthIn(max = 1000.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            PButton(stringResource(R.string.mic_later), onClick = onLater, modifier = Modifier.weight(1f))
            PButton(stringResource(R.string.mic_allow), onClick = onAllow, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MicReason(text: Int) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.ml, vertical = Space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = t.active, modifier = Modifier.size(32.dp))
        Text(stringResource(text), style = Type.bodyL, color = t.text)
    }
}
