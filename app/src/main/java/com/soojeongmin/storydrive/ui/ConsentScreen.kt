package com.soojeongmin.storydrive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.soojeongmin.storydrive.R

@Composable
fun ConsentScreen(onAgree: () -> Unit, onDecline: () -> Unit) {
    BackHandler(onBack = onDecline)
    val t = LocalTokens.current
    Column(Modifier.fillMaxSize().background(t.bg)) {
        PageHeader(stringResource(R.string.app_name), leadingIcon = R.drawable.ic_book)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.margin)) {
            Column(Modifier.widthIn(max = 1000.dp)) {
                VGap(Space.xxl)
                Text(stringResource(R.string.consent_title), style = Type.headline, color = t.text)
                VGap(Space.s)
                Muted(stringResource(R.string.consent_body))
                VGap(Space.xl)
                ListContainer {
                    ConsentItem(R.string.consent_items_label, R.string.consent_items)
                    Divider()
                    ConsentItem(R.string.consent_purpose_label, R.string.consent_purpose)
                    Divider()
                    ConsentItem(R.string.consent_storage_label, R.string.consent_storage)
                }
                VGap(Space.l)
                Muted(stringResource(R.string.consent_refuse), Type.bodyS)
                VGap(Space.xxl)
            }
        }
        Row(
            Modifier.padding(horizontal = Space.margin, vertical = Space.l).widthIn(max = 1000.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            PButton(stringResource(R.string.consent_decline), onClick = onDecline, modifier = Modifier.weight(1f))
            PButton(stringResource(R.string.consent_agree), onClick = onAgree, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ConsentItem(label: Int, body: Int) {
    val t = LocalTokens.current
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.ml, vertical = Space.l)) {
        Text(stringResource(label), style = Type.label, color = t.text3)
        VGap(Space.xs)
        Text(stringResource(body), style = Type.bodyL, color = t.text)
    }
}
