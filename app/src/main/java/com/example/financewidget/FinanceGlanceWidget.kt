package com.example.financewidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

object WidgetKeys {
    val VIEW_MODE = stringPreferencesKey("view_mode")
    val PAYLOAD_JSON = stringPreferencesKey("payload_json")
    val PARAM_TARGET_VIEW = ActionParameters.Key<String>("target_view")
}

class FinanceGlanceWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    private val bgWidget = ColorProvider(Color(0xFF121418))
    private val bgCard = ColorProvider(Color(0xFF1C1F26))
    private val bgBadge = ColorProvider(Color(0xFF1E2D45))
    private val bgEco = ColorProvider(Color(0xFF143021))
    private val textPrimary = ColorProvider(Color(0xFFF1F3F5))
    private val textSecondary = ColorProvider(Color(0xFF9AA4B2))
    private val colorUp = ColorProvider(Color(0xFFF04452))
    private val colorDown = ColorProvider(Color(0xFF3182F6))
    private val colorEco = ColorProvider(Color(0xFF4CD964))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val viewMode = prefs[WidgetKeys.VIEW_MODE] ?: "SUMMARY"
            val payload = WidgetPayload.fromJsonString(prefs[WidgetKeys.PAYLOAD_JSON])

            WidgetRoot(viewMode, payload)
        }
    }

    @Composable
    private fun WidgetRoot(viewMode: String, payload: WidgetPayload) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bgWidget)
                .cornerRadius(24.dp)
                .padding(12.dp)
        ) {
            HeaderBar(viewMode, payload.updatedTime)
            Spacer(GlanceModifier.height(8.dp))

            when (viewMode) {
                "FX" -> DetailListView(payload.fxList)
                "KOSPI" -> DetailListView(payload.kospiList)
                "NASDAQ" -> DetailListView(payload.nasdaqList)
                else -> SummaryGridView(payload.summary)
            }
        }
    }

    @Composable
    private fun HeaderBar(viewMode: String, updatedTime: String) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (viewMode != "SUMMARY") {
                Box(
                    modifier = GlanceModifier
                        .background(bgCard)
                        .cornerRadius(10.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable(
                            actionRunCallback<SwitchViewAction>(
                                actionParametersOf(WidgetKeys.PARAM_TARGET_VIEW to "SUMMARY")
                            )
                        )
                ) {
                    Text(text = "← 요약", style = TextStyle(color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
                Spacer(GlanceModifier.width(6.dp))
            }

            val title = when (viewMode) {
                "FX" -> "주요 10개국 환율"
                "KOSPI" -> "코스피 10대 기업"
                "NASDAQ" -> "나스닥 10대 기업"
                else -> "글로벌 마켓 위젯"
            }

            Text(text = title, style = TextStyle(color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold))

            if (viewMode == "SUMMARY") {
                Spacer(GlanceModifier.width(6.dp))
                Box(modifier = GlanceModifier.background(bgEco).cornerRadius(6.dp).padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text(text = "절전모드", style = TextStyle(color = colorEco, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                }
            }

            Spacer(GlanceModifier.defaultWeight())

            Text(text = updatedTime, style = TextStyle(color = textSecondary, fontSize = 11.sp))
            Spacer(GlanceModifier.width(6.dp))

            Box(
                modifier = GlanceModifier
                    .background(bgCard)
                    .cornerRadius(12.dp)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
                    .clickable(actionRunCallback<ManualRefreshAction>())
            ) {
                Text(text = "🔄", style = TextStyle(fontSize = 12.sp))
            }
        }
    }

    @Composable
    private fun SummaryGridView(summary: Map<String, MarketItem>) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                MetricCard(GlanceModifier.defaultWeight().fillMaxHeight(), "🇺🇸 달러 환율", "10개국 ▸", summary["usd"], "FX")
                Spacer(GlanceModifier.width(6.dp))
                MetricCard(GlanceModifier.defaultWeight().fillMaxHeight(), "🥇 국제 금 (oz)", null, summary["gold"], null)
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                MetricCard(GlanceModifier.defaultWeight().fillMaxHeight(), "🛢️ WTI 유가", null, summary["oil"], null)
                Spacer(GlanceModifier.width(6.dp))
                MetricCard(GlanceModifier.defaultWeight().fillMaxHeight(), "📈 S&P 500", null, summary["sp500"], null)
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                MetricCard(GlanceModifier.defaultWeight().fillMaxHeight(), "🇰🇷 코스피", "10대기업 ▸", summary["kospi"], "KOSPI")
                Spacer(GlanceModifier.width(6.dp))
                MetricCard(GlanceModifier.defaultWeight().fillMaxHeight(), "🇺🇸 나스닥", "10대기업 ▸", summary["nasdaq"], "NASDAQ")
            }
        }
    }

    @Composable
    private fun MetricCard(modifier: GlanceModifier, title: String, badge: String?, item: MarketItem?, targetView: String?) {
        val cardMod = modifier.background(bgCard).cornerRadius(14.dp).padding(10.dp).let { m ->
            if (targetView != null) m.clickable(actionRunCallback<SwitchViewAction>(actionParametersOf(WidgetKeys.PARAM_TARGET_VIEW to targetView))) else m
        }

        Column(modifier = cardMod, verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 🏷️ 위젯 크기를 줄여도 답답하지 않도록 최적화된 폰트 크기 (12.sp)
                Text(text = title, style = TextStyle(color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.defaultWeight())
                if (badge != null) {
                    Box(modifier = GlanceModifier.background(bgBadge).cornerRadius(5.dp).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text(text = badge, style = TextStyle(color = colorDown, fontSize = 9.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
            Spacer(GlanceModifier.height(4.dp))
            // 💰 핵심 숫자 크기 (17.sp로 조절하여 크기를 줄여도 여유있게 배치)
            Text(text = item?.priceText ?: "--", style = TextStyle(color = textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(2.dp))
            // 📈 등락률 크기 (11.sp)
            Text(text = item?.changeText ?: "", style = TextStyle(color = if (item?.isUp != false) colorUp else colorDown, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
    }

    @Composable
    private fun DetailListView(items: List<MarketItem>) {
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            itemsIndexed(items) { index, item ->
                Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 5.dp)) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().background(bgCard).cornerRadius(10.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "${index + 1}", style = TextStyle(color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        Spacer(GlanceModifier.width(8.dp))
                        Column {
                            Text(text = item.name, style = TextStyle(color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                            Text(text = item.code, style = TextStyle(color = textSecondary, fontSize = 10.sp))
                        }
                        Spacer(GlanceModifier.defaultWeight())
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = item.priceText, style = TextStyle(color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                            Text(text = item.changeText, style = TextStyle(color = if (item.isUp) colorUp else colorDown, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

class SwitchViewAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val target = parameters[WidgetKeys.PARAM_TARGET_VIEW] ?: "SUMMARY"
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
            p.toMutablePreferences().apply { this[WidgetKeys.VIEW_MODE] = target }
        }
        FinanceGlanceWidget().update(context, glanceId)
    }
}

class ManualRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val fresh = FinanceRepository.fetchAllMarketData()
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
            p.toMutablePreferences().apply { this[WidgetKeys.PAYLOAD_JSON] = fresh.toJsonString() }
        }
        FinanceGlanceWidget().update(context, glanceId)
    }
}
