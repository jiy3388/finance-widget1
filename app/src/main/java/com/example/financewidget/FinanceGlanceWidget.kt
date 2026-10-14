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
    val VIEW_MODE = stringPreferencesKey("view_mode") // "SUMMARY", "FX", "KOSPI", "NASDAQ"
    val PAYLOAD_JSON = stringPreferencesKey("payload_json")
    val PARAM_TARGET_VIEW = ActionParameters.Key<String>("target_view")
}

class FinanceGlanceWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // OLED 배터리 절약 다크 테마 색상
    private val bgWidget = ColorProvider(Color(0xFF121418))
    private val bgCard = ColorProvider(Color(0xFF1C1F26))
    private val bgBadge = ColorProvider(Color(0xFF1E2D45))
    private val bgEco = ColorProvider(Color(0xFF143021))
    private val textPrimary = ColorProvider(Color(0xFFF1F3F5))
    private val textSecondary = ColorProvider(Color(0xFF8B95A1))
    private val colorUp = ColorProvider(Color(0xFFF04452))     // 상승 (빨강)
    private val colorDown = ColorProvider(Color(0xFF3182F6))   // 하락 (파랑)
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
                .padding(14.dp)
        ) {
            HeaderBar(viewMode, payload.updatedTime)
            Spacer(GlanceModifier.height(10.dp))

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
                        .cornerRadius(12.dp)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .clickable(
                            actionRunCallback<SwitchViewAction>(
                                actionParametersOf(WidgetKeys.PARAM_TARGET_VIEW to "SUMMARY")
                            )
                        )
                ) {
                    Text(
                        text = "← 요약",
                        style = TextStyle(color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(GlanceModifier.width(8.dp))
            }

            val title = when (viewMode) {
                "FX" -> "주요 10개국 환율"
                "KOSPI" -> "코스피 10대 기업"
                "NASDAQ" -> "나스닥 10대 기업"
                else -> "글로벌 마켓 위젯"
            }

            Text(
                text = title,
                style = TextStyle(color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )

            if (viewMode == "SUMMARY") {
                Spacer(GlanceModifier.width(6.dp))
                Box(
                    modifier = GlanceModifier
                        .background(bgEco)
                        .cornerRadius(8.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "절전모드",
                        style = TextStyle(color = colorEco, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(GlanceModifier.defaultWeight())

            Text(
                text = updatedTime,
                style = TextStyle(color = textSecondary, fontSize = 11.sp)
            )
            Spacer(GlanceModifier.width(6.dp))

            // 수동 새로고침 버튼 (🔄)
            Box(
                modifier = GlanceModifier
                    .background(bgCard)
                    .cornerRadius(14.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(actionRunCallback<ManualRefreshAction>())
            ) {
                Text(text = "🔄", style = TextStyle(fontSize = 12.sp))
            }
        }
    }

    @Composable
    private fun SummaryGridView(summary: Map<String, MarketItem>) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // 1행: 달러 환율(터치 시 10개국) | 국제 금
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                MetricCard(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    title = "🇺🇸 달러 환율",
                    badge = "10개국 ▸",
                    item = summary["usd"],
                    targetView = "FX"
                )
                Spacer(GlanceModifier.width(8.dp))
                MetricCard(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    title = "🥇 국제 금 (oz)",
                    badge = null,
                    item = summary["gold"],
                    targetView = null
                )
            }
            Spacer(GlanceModifier.height(8.dp))

            // 2행: WTI 유가 | S&P 500
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                MetricCard(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    title = "🛢️ WTI 유가",
                    badge = null,
                    item = summary["oil"],
                    targetView = null
                )
                Spacer(GlanceModifier.width(8.dp))
                MetricCard(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    title = "📈 S&P 500",
                    badge = null,
                    item = summary["sp500"],
                    targetView = null
                )
            }
            Spacer(GlanceModifier.height(8.dp))

            // 3행: 코스피(터치 시 10대기업) | 나스닥(터치 시 10대기업)
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                MetricCard(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    title = "🇰🇷 코스피",
                    badge = "10대기업 ▸",
                    item = summary["kospi"],
                    targetView = "KOSPI"
                )
                Spacer(GlanceModifier.width(8.dp))
                MetricCard(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    title = "🇺🇸 나스닥",
                    badge = "10대기업 ▸",
                    item = summary["nasdaq"],
                    targetView = "NASDAQ"
                )
            }
        }
    }

    @Composable
    private fun MetricCard(
        modifier: GlanceModifier,
        title: String,
        badge: String?,
        item: MarketItem?,
        targetView: String?
    ) {
        val cardModifier = modifier
            .background(bgCard)
            .cornerRadius(16.dp)
            .padding(10.dp)
            .let { mod ->
                if (targetView != null) {
                    mod.clickable(
                        actionRunCallback<SwitchViewAction>(
                            actionParametersOf(WidgetKeys.PARAM_TARGET_VIEW to targetView)
                        )
                    )
                } else mod
            }

        Column(modifier = cardModifier, verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = TextStyle(color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                )
                Spacer(GlanceModifier.defaultWeight())
                if (badge != null) {
                    Box(
                        modifier = GlanceModifier
                            .background(bgBadge)
                            .cornerRadius(6.dp)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            style = TextStyle(color = colorDown, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = item?.priceText ?: "--",
                style = TextStyle(color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = item?.changeText ?: "",
                style = TextStyle(
                    color = if (item?.isUp != false) colorUp else colorDown,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }

    @Composable
    private fun DetailListView(items: List<MarketItem>) {
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            itemsIndexed(items) { index, item ->
                Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(bgCard)
                            .cornerRadius(12.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = TextStyle(color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(GlanceModifier.width(10.dp))
                        Column {
                            Text(
                                text = item.name,
                                style = TextStyle(color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = item.code,
                                style = TextStyle(color = textSecondary, fontSize = 10.sp)
                            )
                        }
                        Spacer(GlanceModifier.defaultWeight())
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = item.priceText,
                                style = TextStyle(color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = item.changeText,
                                style = TextStyle(
                                    color = if (item.isUp) colorUp else colorDown,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// 화면 전환 액션 (네트워크 재호출 없이 로컬 캐시로 즉시 전환 -> 배터리 소모 없음)
class SwitchViewAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val targetView = parameters[WidgetKeys.PARAM_TARGET_VIEW] ?: "SUMMARY"
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[WidgetKeys.VIEW_MODE] = targetView
            }
        }
        FinanceGlanceWidget().update(context, glanceId)
    }
}

// 수동 새로고침 액션 (🔄 버튼 터치 시 즉시 실시간 API 호출)
class ManualRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val freshPayload = FinanceRepository.fetchAllMarketData()
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[WidgetKeys.PAYLOAD_JSON] = freshPayload.toJsonString()
            }
        }
        FinanceGlanceWidget().update(context, glanceId)
    }
}