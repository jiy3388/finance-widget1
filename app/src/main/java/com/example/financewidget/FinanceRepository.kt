package com.example.financewidget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class MarketItem(
    val code: String,
    val name: String,
    val priceText: String,
    val changeText: String,
    val isUp: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("code", code)
        put("name", name)
        put("priceText", priceText)
        put("changeText", changeText)
        put("isUp", isUp)
    }

    companion object {
        fun fromJson(obj: JSONObject) = MarketItem(
            code = obj.getString("code"),
            name = obj.getString("name"),
            priceText = obj.getString("priceText"),
            changeText = obj.getString("changeText"),
            isUp = obj.getBoolean("isUp")
        )
    }
}

data class WidgetPayload(
    val updatedTime: String,
    val summary: Map<String, MarketItem>,
    val fxList: List<MarketItem>,
    val kospiList: List<MarketItem>,
    val nasdaqList: List<MarketItem>
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("updatedTime", updatedTime)

        val sumObj = JSONObject()
        summary.forEach { (k, v) -> sumObj.put(k, v.toJson()) }
        root.put("summary", sumObj)

        root.put("fxList", JSONArray(fxList.map { it.toJson() }))
        root.put("kospiList", JSONArray(kospiList.map { it.toJson() }))
        root.put("nasdaqList", JSONArray(nasdaqList.map { it.toJson() }))
        return root.toString()
    }

    companion object {
        fun fromJsonString(jsonStr: String?): WidgetPayload {
            if (jsonStr.isNullOrBlank()) return defaultFallback()
            return try {
                val root = JSONObject(jsonStr)
                val sumObj = root.getJSONObject("summary")
                val summaryMap = mutableMapOf<String, MarketItem>()
                sumObj.keys().forEach { key ->
                    summaryMap[key] = MarketItem.fromJson(sumObj.getJSONObject(key))
                }
                WidgetPayload(
                    updatedTime = root.optString("updatedTime", "--:--"),
                    summary = summaryMap,
                    fxList = parseList(root.optJSONArray("fxList")),
                    kospiList = parseList(root.optJSONArray("kospiList")),
                    nasdaqList = parseList(root.optJSONArray("nasdaqList"))
                )
            } catch (e: Exception) {
                defaultFallback()
            }
        }

        private fun parseList(arr: JSONArray?): List<MarketItem> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).map { MarketItem.fromJson(arr.getJSONObject(it)) }
        }

        // 최초 위젯 배치 직후(네트워크 완료 전 1초 동안) 보여줄 최신 기준가
        fun defaultFallback(): WidgetPayload {
            return WidgetPayload(
                updatedTime = "연결중",
                summary = mapOf(
                    "usd" to MarketItem("USD", "달러 환율", "1,385.00원", "▲ 2.50 (+0.18%)", true),
                    "gold" to MarketItem("GC=F", "국제 금", "$2,750.00", "▲ 15.20 (+0.56%)", true),
                    "oil" to MarketItem("CL=F", "WTI 유가", "$70.50", "▼ 0.40 (-0.56%)", false),
                    "sp500" to MarketItem("^GSPC", "S&P 500", "6,120.00", "▲ 18.40 (+0.30%)", true),
                    "kospi" to MarketItem("KOSPI", "코스피", "3,850.00", "▲ 25.10 (+0.66%)", true),
                    "nasdaq" to MarketItem("^IXIC", "나스닥", "19,850.00", "▲ 65.20 (+0.33%)", true)
                ),
                fxList = listOf(
                    MarketItem("USD", "미국 달러", "1,385.00원", "기준 통화 (1 USD)", true),
                    MarketItem("JPY", "일본 엔 (100엔)", "920.50원", "100 JPY 기준", false),
                    MarketItem("EUR", "유럽연합 유로", "1,505.20원", "1 EUR 기준", true),
                    MarketItem("CNY", "중국 위안", "194.10원", "1 CNY 기준", true),
                    MarketItem("GBP", "영국 파운드", "1,795.00원", "1 GBP 기준", true),
                    MarketItem("AUD", "호주 달러", "922.40원", "1 AUD 기준", false),
                    MarketItem("CAD", "캐나다 달러", "1,002.10원", "1 CAD 기준", true),
                    MarketItem("THB", "태국 바트", "41.20원", "1 THB 기준", false),
                    MarketItem("VND", "베트남 동 (100동)", "5.52원", "100 VND 기준", true),
                    MarketItem("CHF", "스위스 프랑", "1,590.00원", "1 CHF 기준", true)
                ),
                kospiList = listOf(
                    MarketItem("005930", "삼성전자", "239,500원", "▲ 3,500 (+1.48%)", true),
                    MarketItem("000660", "SK하이닉스", "1,420,000원", "▲ 25,000 (+1.79%)", true),
                    MarketItem("005380", "현대차", "319,500원", "▲ 4,000 (+1.27%)", true),
                    MarketItem("035720", "카카오", "31,950원", "▼ 350 (-1.08%)", false),
                    MarketItem("001740", "SK네트웍스", "4,650원", "▲ 45 (+0.98%)", true),
                    MarketItem("373220", "LG에너지솔루션", "425,000원", "▼ 3,000 (-0.70%)", false),
                    MarketItem("207940", "삼성바이오로직스", "1,085,000원", "▲ 12,000 (+1.12%)", true),
                    MarketItem("000270", "기아", "128,500원", "▲ 1,500 (+1.18%)", true),
                    MarketItem("068270", "셀트리온", "195,000원", "▲ 2,000 (+1.04%)", true),
                    MarketItem("035420", "NAVER", "192,000원", "▲ 2,500 (+1.32%)", true)
                ),
                nasdaqList = listOf(
                    MarketItem("AAPL", "애플 (Apple)", "$245.20", "▲ 2.10 (+0.86%)", true),
                    MarketItem("MSFT", "마이크로소프트", "$448.50", "▲ 3.20 (+0.72%)", true),
                    MarketItem("NVDA", "엔비디아 (NVIDIA)", "$168.40", "▲ 4.10 (+2.49%)", true),
                    MarketItem("AMZN", "아마존 (Amazon)", "$212.30", "▲ 1.50 (+0.71%)", true),
                    MarketItem("GOOGL", "알파벳 (구글)", "$184.60", "▼ 0.80 (-0.43%)", false),
                    MarketItem("META", "메타 (Meta)", "$615.00", "▲ 5.40 (+0.89%)", true),
                    MarketItem("TSLA", "테슬라 (Tesla)", "$258.00", "▼ 3.20 (-1.22%)", false),
                    MarketItem("AVGO", "브로드컴", "$195.40", "▲ 2.80 (+1.45%)", true),
                    MarketItem("NFLX", "넷플릭스", "$785.00", "▲ 7.10 (+0.91%)", true),
                    MarketItem("COST", "코스트코", "$915.00", "▲ 3.50 (+0.38%)", true)
                )
            )
        }
    }
}

object FinanceRepository {

    // 코스피 10대 기업 (네이버 금융 실시간 연동)
    private val kospiTickers = listOf(
        "005930" to "삼성전자",
        "000660" to "SK하이닉스",
        "005380" to "현대차",
        "035720" to "카카오",
        "001740" to "SK네트웍스",
        "373220" to "LG에너지솔루션",
        "207940" to "삼성바이오로직스",
        "000270" to "기아",
        "068270" to "셀트리온",
        "035420" to "NAVER"
    )

    // 나스닥 10대 기업
    private val nasdaqTickers = listOf(
        "AAPL" to "애플 (Apple)",
        "MSFT" to "마이크로소프트",
        "NVDA" to "엔비디아 (NVIDIA)",
        "AMZN" to "아마존 (Amazon)",
        "GOOGL" to "알파벳 (구글)",
        "META" to "메타 (Meta)",
        "TSLA" to "테슬라 (Tesla)",
        "AVGO" to "브로드컴",
        "NFLX" to "넷플릭스",
        "COST" to "코스트코"
    )

    suspend fun fetchAllMarketData(): WidgetPayload = withContext(Dispatchers.IO) {
        val fallback = WidgetPayload.defaultFallback()

        // 1. 무료 공개 환율 API (10개국 환율)
        val fxJob = async { fetchExchangeRates(fallback) }

        // 2. 코스피 지수 & 10대 기업 (네이버 모바일 금융 실시간 API 직통 호출)
        val kospiIdxJob = async { fetchNaverKospiIndex() ?: fallback.summary["kospi"]!! }
        val kospiStocksJob = async {
            kospiTickers.mapIndexed { idx, (code, name) ->
                async { fetchNaverStock(code, name) ?: fallback.kospiList[idx] }
            }.awaitAll()
        }

        // 3. 해외 지수/원자재 & 나스닥 10대 기업 (Yahoo Finance query2 API)
        val goldJob = async { fetchYahooQuote("GC=F", "국제 금", prefix = "$", decimals = 2) ?: fallback.summary["gold"]!! }
        val oilJob = async { fetchYahooQuote("CL=F", "WTI 유가", prefix = "$", decimals = 2) ?: fallback.summary["oil"]!! }
        val sp500Job = async { fetchYahooQuote("^GSPC", "S&P 500", decimals = 2) ?: fallback.summary["sp500"]!! }
        val nasdaqIdxJob = async { fetchYahooQuote("^IXIC", "나스닥", decimals = 2) ?: fallback.summary["nasdaq"]!! }

        val nasdaqStocksJob = async {
            nasdaqTickers.mapIndexed { idx, (ticker, name) ->
                async {
                    fetchYahooQuote(ticker, name, prefix = "$", decimals = 2)
                        ?: fallback.nasdaqList[idx]
                }
            }.awaitAll()
        }

        val (usdSummary, fxTop10) = fxJob.await()
        val nowTime = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date())

        WidgetPayload(
            updatedTime = nowTime,
            summary = mapOf(
                "usd" to usdSummary,
                "gold" to goldJob.await(),
                "oil" to oilJob.await(),
                "sp500" to sp500Job.await(),
                "kospi" to kospiIdxJob.await(),
                "nasdaq" to nasdaqIdxJob.await()
            ),
            fxList = fxTop10,
            kospiList = kospiStocksJob.await(),
            nasdaqList = nasdaqStocksJob.await()
        )
    }

    // 네이버 모바일 금융 API: 코스피 지수 실시간 조회
    private fun fetchNaverKospiIndex(): MarketItem? {
        return try {
            val res = httpGet("https://m.stock.naver.com/api/index/KOSPI/basic")
            val obj = JSONObject(res)
            val closePrice = obj.getString("closePrice")
            val diffRaw = obj.getString("compareToPreviousClosePrice").replace(",", "").toDouble()
            val ratioRaw = obj.getString("fluctuationsRatio").toDouble()
            val dirName = obj.optJSONObject("compareToPreviousPrice")?.optString("name", "RISING") ?: "RISING"
            val isDown = dirName == "FALLING" || dirName == "LOWER_LIMIT" || ratioRaw < 0
            val isUp = !isDown

            val arrow = if (isUp) "▲" else "▼"
            val sign = if (isUp) "+" else "-"
            val changeStr = "$arrow ${String.format(Locale.KOREA, "%,.2f", abs(diffRaw))} ($sign${String.format(Locale.KOREA, "%.2f", abs(ratioRaw))}%)"

            MarketItem("KOSPI", "코스피", closePrice, changeStr, isUp)
        } catch (e: Exception) {
            null
        }
    }

    // 네이버 모바일 금융 API: 국내 개별 주식 실시간 조회
    private fun fetchNaverStock(code: String, displayName: String): MarketItem? {
        return try {
            val res = httpGet("https://m.stock.naver.com/api/stock/$code/basic")
            val obj = JSONObject(res)
            val closePrice = obj.getString("closePrice")
            val diffRaw = obj.getString("compareToPreviousClosePrice").replace(",", "").toDouble()
            val ratioRaw = obj.getString("fluctuationsRatio").toDouble()
            val dirName = obj.optJSONObject("compareToPreviousPrice")?.optString("name", "RISING") ?: "RISING"
            val isDown = dirName == "FALLING" || dirName == "LOWER_LIMIT" || ratioRaw < 0
            val isUp = !isDown

            val arrow = if (isUp) "▲" else "▼"
            val sign = if (isUp) "+" else "-"
            val changeStr = "$arrow ${String.format(Locale.KOREA, "%,.0f", abs(diffRaw))} ($sign${String.format(Locale.KOREA, "%.2f", abs(ratioRaw))}%)"

            MarketItem(
                code = code,
                name = displayName,
                priceText = "${closePrice}원",
                changeText = changeStr,
                isUp = isUp
            )
        } catch (e: Exception) {
            null
        }
    }

    // 환율 10개국 실시간 조회
    private fun fetchExchangeRates(fallback: WidgetPayload): Pair<MarketItem, List<MarketItem>> {
        return try {
            val json = httpGet("https://open.er-api.com/v6/latest/USD")
            val rates = JSONObject(json).getJSONObject("rates")
            val krw = rates.getDouble("KRW")

            val usdYahoo = fetchYahooQuote("KRW=X", "달러 환율", suffix = "원", decimals = 2)
            val usdItem = usdYahoo ?: MarketItem(
                code = "USD",
                name = "달러 환율",
                priceText = String.format(Locale.KOREA, "%,.2f원", krw),
                changeText = "실시간 고시환율",
                isUp = true
            )

            val countries = listOf(
                Triple("USD", "미국 달러", 1),
                Triple("JPY", "일본 엔 (100엔)", 100),
                Triple("EUR", "유럽연합 유로", 1),
                Triple("CNY", "중국 위안", 1),
                Triple("GBP", "영국 파운드", 1),
                Triple("AUD", "호주 달러", 1),
                Triple("CAD", "캐나다 달러", 1),
                Triple("THB", "태국 바트", 1),
                Triple("VND", "베트남 동 (100동)", 100),
                Triple("CHF", "스위스 프랑", 1)
            )

            val list = countries.map { (code, name, unit) ->
                val rateInUsd = rates.optDouble(code, 1.0)
                val krwPerUnit = if (code == "USD") krw else (krw / rateInUsd) * unit
                MarketItem(
                    code = code,
                    name = name,
                    priceText = String.format(Locale.KOREA, "%,.2f원", krwPerUnit),
                    changeText = "${unit} ${code} 기준",
                    isUp = true
                )
            }
            usdItem to list
        } catch (e: Exception) {
            fallback.summary["usd"]!! to fallback.fxList
        }
    }

    // 해외 지수/원자재/나스닥 종목 조회
    private fun fetchYahooQuote(
        ticker: String,
        displayName: String,
        prefix: String = "",
        suffix: String = "",
        decimals: Int = 2
    ): MarketItem? {
        return try {
            val encoded = java.net.URLEncoder.encode(ticker, "UTF-8")
            val url = "https://query2.finance.yahoo.com/v8/finance/chart/$encoded?interval=1d&range=2d"
            val res = httpGet(url)
            val meta = JSONObject(res)
                .getJSONObject("chart")
                .getJSONArray("result")
                .getJSONObject(0)
                .getJSONObject("meta")

            val price = meta.getDouble("regularMarketPrice")
            val prevClose = meta.optDouble("chartPreviousClose", price)
            val diff = price - prevClose
            val pct = if (prevClose != 0.0) (diff / prevClose) * 100.0 else 0.0
            val isUp = diff >= 0

            val fmt = "%,.${decimals}f"
            val priceStr = "$prefix${String.format(Locale.KOREA, fmt, price)}$suffix"
            val arrow = if (isUp) "▲" else "▼"
            val sign = if (isUp) "+" else "-"
            val changeStr = "$arrow ${String.format(Locale.KOREA, fmt, abs(diff))} ($sign${String.format(Locale.KOREA, "%.2f", abs(pct))}%)"

            MarketItem(
                code = ticker,
                name = displayName,
                priceText = priceStr,
                changeText = changeStr,
                isUp = isUp
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; SM-S928N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
            )
            setRequestProperty("Accept", "application/json, text/plain, */*")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}