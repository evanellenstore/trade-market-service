package com.trade.market.service;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.StochasticOscillatorDIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.adx.MinusDIIndicator;
import org.ta4j.core.indicators.adx.PlusDIIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.volume.ChaikinMoneyFlowIndicator;
import org.ta4j.core.indicators.volume.OnBalanceVolumeIndicator;
import org.ta4j.core.indicators.volume.VWAPIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import com.trade.market.dto.IndicatorResultDto;
import com.trade.market.indicator.BarSeriesManager;
import com.trade.market.util.TradeConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndicatorService {

    private static final int MFI_PERIOD = 14;
    private static final int ADX_PERIOD = 14;
    private static final int ATR_PERIOD = 14;
    private static final int STOCHASTIC_PERIOD = 14;
    private static final int CCI_PERIOD = 20;
    private static final int ROC_PERIOD = 12;
    private static final int CMF_PERIOD = 20;
    private static final int VWAP_PERIOD = 14;
    private static final int SUPERTREND_PERIOD = 10;
    private static final double SUPERTREND_MULTIPLIER = 3.0;

    private final BarSeriesManager barSeriesManager;

    public IndicatorResultDto calculateIndicators(String symbol, String timeframe, String symbolToken, LocalDateTime candleTime, List<Double> closePrices) {
        return calculateIndicatorsBySeriesKey(symbol, timeframe, symbol, symbolToken, candleTime, closePrices);
    }

    public IndicatorResultDto calculateIndicatorsBySeriesKey(String symbol, String timeframe, String seriesKey, String symbolToken, LocalDateTime candleTime, List<Double> closePrices) {
        if (closePrices == null || closePrices.isEmpty()) {
            return IndicatorResultDto.builder()
                    .symbol(symbol)
                    .symbolToken(symbolToken)
                    .timeframe(timeframe)
                    .candleTime(candleTime)
                    .build();
        }

        BarSeries series = barSeriesManager.getSeriesByKey(seriesKey, timeframe);
        if (series == null || series.getBarCount() == 0) {
            return fallbackResult(symbol, symbolToken, timeframe, candleTime, closePrices);
        }

        ClosePriceIndicator close = new ClosePriceIndicator(series);

        /*
         * EMA (Exponential Moving Average) is a trend indicator that averages price over a lookback
         * window while weighting recent candles more heavily than older ones, so it reacts faster to
         * new price action than a simple moving average.
         *
         * Price above EMA  -> uptrend bias
         * Price below EMA  -> downtrend bias
         * Shorter EMA crossing above/below a longer EMA is a common trend-change signal.
         *
         *   Price ──╮
         *           ╰──╮        ╭── EMA (lags behind, smoothed)
         *               ╰──╮  ╭─╯
         *                   ╰─╯
         *   fast reaction ◄──────────────► smooth/lagging (longer period)
         *
         *
         * EMA (Exponential Moving Average) — हाल के दिनों की कीमतों का औसत, जिसमें नई कीमतों को ज़्यादा वेटेज मिलता है।
         * उदाहरण: स्टॉक ₹100 पर है, 20-EMA ₹95 है। कीमत EMA से ऊपर है, यानी हल्का अपट्रेंड।
         * कहाँ इस्तेमाल करें: ट्रेंड की दिशा पहचानने के लिए, और एंट्री/एग्ज़िट लेवल तय करने के लिए (जैसे 50-EMA पर सपोर्ट)। स्विंग ट्रेडिंग और पोज़िशनल ट्रेडिंग में बहुत काम आता है।
         *
         */
        // Calculates the average of the last 10 candles, with more weight given to recent candles.
        EMAIndicator ema = new EMAIndicator(close, TradeConstant.EMA_TEN);

        // Calculates the average of the last 20 candles, with more weight given to recent candles.
        EMAIndicator ema20 = new EMAIndicator(close, TradeConstant.EMA_TWENTY);

        // Calculates the average of the last 50 candles, with more weight given to recent candles.
        EMAIndicator ema50 = new EMAIndicator(close, TradeConstant.EMA_FIFTY);

        // Calculates the average of the last 100 candles, with more weight given to recent candles.
        EMAIndicator ema100 = new EMAIndicator(close, TradeConstant.EMA_HUNDRED);

        // Calculates the average of the last 200 candles, with more weight given to recent candles.
        EMAIndicator ema200 = new EMAIndicator(close, TradeConstant.EMA_TWO_HUNDRED);

        /* RSI (Relative Strength Index) is a momentum indicator that measures the speed and magnitude of
         * recent price movements.
         *
         * It tells whether a stock is:
         * Overbought (price may fall)
         * Oversold (price may rise)
         * Neutral
         *
         * 0 ---------------------------100
         * Oversold      Neutral      Overbought
         * 30            50             70
         *
         *
         * RSI (Relative Strength Index) — 0 से 100 का स्केल, हाल के गेन बनाम लॉस को मापता है।
         * उदाहरण: RSI = 78 → स्टॉक बहुत ज़्यादा चढ़ चुका है, "overbought" हो सकता है, गिरावट की संभावना। RSI = 25 → "oversold", उछाल की संभावना।
         * कहाँ इस्तेमाल करें: ओवरबॉट/ओवरसोल्ड ज़ोन पहचानने के लिए, रिवर्सल ट्रेड्स में।
         */
        RSIIndicator rsi14 = new RSIIndicator(close, TradeConstant.RSI_PERIOD);

        /*
         * MACD is a trend-following momentum indicator that helps identify:
         * Trend direction
         * Trend strength
         * Momentum changes
         * Buy/Sell signals
         *
         *                     Positive
         *                 ▲
         *                 │
         *       2.5 ──────┤
         *       1.5 ──────┤
         *       0.0 ──────┼──────── Zero Line
         *      -1.5 ──────┤
         *      -2.5 ──────┤
         *                 ▼
         *             Negative
         *
         *
         * MACD — फास्ट EMA और स्लो EMA के बीच का फर्क। शॉर्ट-टर्म मोमेंटम लॉन्ग-टर्म ट्रेंड से आगे है या पीछे, यह दिखाता है।
         * उदाहरण: MACD लाइन सिग्नल लाइन के ऊपर क्रॉस करे → बुलिश मोमेंटम, अक्सर खरीदारी का ट्रिगर माना जाता है।
         * कहाँ इस्तेमाल करें: ट्रेंड रिवर्सल और मोमेंटम शिफ्ट पकड़ने के लिए, स्विंग ट्रेडिंग में लोकप्रिय।
         */
        MACDIndicator macd = new MACDIndicator(close, TradeConstant.SHORT_BAR_COUNT, TradeConstant.LONG_BAR_COUNT);
        EMAIndicator macdSignal = new EMAIndicator(macd, TradeConstant.SIGNAL_BAR_COUNT);
        DifferenceIndicator macdHist = new DifferenceIndicator(macd, macdSignal);

        /*
         * Bollinger Bands are a volatility indicator built from a moving average (the middle band)
         * plus/minus a multiple of the standard deviation of price (the upper/lower bands).
         *
         * Bands widen  -> volatility increasing
         * Bands narrow -> volatility contracting (often precedes a breakout, the "squeeze")
         * Price near upper band  -> relatively expensive vs recent range (possible overbought)
         * Price near lower band  -> relatively cheap vs recent range (possible oversold)
         *
         *   Upper Band  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─╮   ╭─ ─ ─ ─
         *                                   ╲ ╱
         *   Middle (SMA) ───────────────────╳──────────
         *                                   ╱ ╲
         *   Lower Band  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─╯   ╰─ ─ ─ ─
         *
         * Note: the middle band must be a moving average (SMA) of price, not the raw close price
         * itself — passing `close` directly (as the original code did) collapses middle/upper/lower
         * bands onto the price line and makes bbWidth/percentB meaningless.
         *
         *
         * Bollinger Bands — मूविंग एवरेज के ऊपर-नीचे वोलैटिलिटी बेस्ड बैंड्स।
         * उदाहरण: बैंड्स ₹95–₹105 हैं, ₹100 के औसत के आसपास। कीमत ₹104 (ऊपरी बैंड) छू रही है → महंगी हो सकती है। बैंड्स टाइट (₹98–₹102) → बड़े मूव से पहले की चुप्पी।
         * कहाँ इस्तेमाल करें: वोलैटिलिटी ब्रेकआउट्स और squeeze पैटर्न पहचानने के लिए।
         *
         * %B (Percent B) — कीमत बैंड्स के अंदर कहाँ है, 0 से 1 स्केल।
         * उदाहरण: %B = 0.9 → कीमत बैंड्स के टॉप के पास (ओवरबॉट के करीब)।
         */
        SMAIndicator sma20 = new SMAIndicator(close, TradeConstant.STANDARD_DEVIATION_BAR_COUNT);
        StandardDeviationIndicator sd20 = new StandardDeviationIndicator(close, TradeConstant.STANDARD_DEVIATION_BAR_COUNT);
        BollingerBandsMiddleIndicator bbMid = new BollingerBandsMiddleIndicator(sma20);
        BollingerBandsUpperIndicator bbUpper = new BollingerBandsUpperIndicator(bbMid, sd20, DecimalNum.valueOf(2));
        BollingerBandsLowerIndicator bbLower = new BollingerBandsLowerIndicator(bbMid, sd20, DecimalNum.valueOf(2));

        /*
         * VWAP (Volume Weighted Average Price) is the average price over the lookback window,
         * weighted by the volume traded at each price level rather than by time. It approximates
         * the "fair" average price paid by market participants during that window.
         *
         * Price above VWAP -> buyers paying a premium vs the session's average (bullish bias)
         * Price below VWAP -> sellers accepting a discount vs the session's average (bearish bias)
         * Widely used as an intraday benchmark/execution reference by institutional traders.
         *
         *
         * VWAP — वॉल्यूम-वेटेड औसत कीमत, दिन की "फेयर वैल्यू" बेंचमार्क।
         * उदाहरण: ज़्यादातर ट्रेडिंग ₹102 के आसपास हुई → VWAP ≈ ₹102। कीमत ₹105 पर है तो आज के औसत से प्रीमियम पर ट्रेड हो रहा है।
         * कहाँ इस्तेमाल करें: इंट्राडे ट्रेडिंग में एंट्री/एग्ज़िट बेंचमार्क के तौर पर, इंस्टीट्यूशनल ट्रेडर्स बहुत इस्तेमाल करते हैं।
         *
         */
        VWAPIndicator vwap = new VWAPIndicator(series, VWAP_PERIOD);

        /*
         * ADX (Average Directional Index) measures trend STRENGTH, not direction — it stays positive
         * whether the trend is up or down. +DI and -DI (Directional Indicators) measure upward and
         * downward price movement respectively, and their crossover gives trend DIRECTION.
         *
         * 0 ---------------------------100
         * No Trend   Weak/Developing   Strong Trend
         * < 20            20-25           > 25
         *
         * +DI crosses above -DI -> bullish directional signal
         * -DI crosses above +DI -> bearish directional signal
         * ADX rising while a DI leads -> that trend is strengthening
         *
         *
         * ADX (Average Directional Index) — ट्रेंड कितना मज़बूत है, दिशा नहीं बताता।
         * उदाहरण: ADX = 35 यानी ट्रेंड मज़बूत है (ऊपर हो या नीचे)। ADX = 12 यानी मार्केट साइडवेज़/बेकार है।
         * कहाँ इस्तेमाल करें: यह तय करने के लिए कि ट्रेंड-फॉलोइंग स्ट्रैटेजी (जैसे moving average crossover) इस्तेमाल करें या नहीं। ADX कम हो तो ट्रेंड स्ट्रैटेजी से बचें।
         *
         * +DI / -DI — ADX की ताकत किस दिशा में लग रही है, यह बताते हैं।
         * उदाहरण: +DI = 30, -DI = 15 → खरीदार हावी हैं। +DI का -DI के ऊपर क्रॉस करना अक्सर खरीदारी का सिग्नल माना जाता है।
         * कहाँ इस्तेमाल करें: ADX के साथ मिलाकर एंट्री टाइमिंग तय करने के लिए।
         *
         */
        ADXIndicator adx = new ADXIndicator(series, ADX_PERIOD);
        PlusDIIndicator plusDiIndicator = new PlusDIIndicator(series, ADX_PERIOD);
        MinusDIIndicator minusDiIndicator = new MinusDIIndicator(series, ADX_PERIOD);

        /*
         * ATR (Average True Range) is a volatility indicator — the average of the "true range"
         * (largest of: high-low, high-prevClose, low-prevClose) over the lookback window. It does
         * not indicate direction, only how much price is moving.
         *
         * High ATR -> large price swings (higher volatility, wider stop-loss/position sizing needed)
         * Low ATR  -> small price swings (lower volatility, tighter ranges)
         * Commonly used to size stop-losses and position sizes relative to current volatility.
         *
         *
         * ATR (Average True Range) — कीमत के स्विंग का औसत साइज़, दिशा नहीं बताता।
         * उदाहरण: ATR = ₹8 एक ₹500 वाले स्टॉक पर → रोज़ का सामान्य रेंज करीब ₹8 है।
         * कहाँ इस्तेमाल करें: स्टॉप-लॉस और पोज़िशन साइज़ तय करने के लिए (आमतौर पर एंट्री से 1.5–2× ATR दूर)।
         */
        ATRIndicator atrIndicator = new ATRIndicator(series, ATR_PERIOD);

        /*
         * Stochastic Oscillator compares the current close to the high/low range over the lookback
         * window, on a 0-100 scale. %K is the raw fast line; %D is a smoothed (SMA) version of %K
         * used as a signal line, similar in spirit to MACD's signal line.
         *
         * 0 ---------------------------100
         * Oversold      Neutral      Overbought
         * 20            50             80
         *
         * %K crosses above %D in oversold zone -> potential bullish reversal
         * %K crosses below %D in overbought zone -> potential bearish reversal
         *
         *
         * Stochastic Oscillator (%K/%D) — आज की क्लोज़िंग प्राइस को हाल की high-low रेंज से तुलना करता है, 0–100 स्केल।
         * उदाहरण: 14 दिन की रेंज ₹90–₹110 थी, क्लोज़ ₹108 पर हुई → %K करीब 90 → ओवरबॉट ज़ोन।
         * कहाँ इस्तेमाल करें: शॉर्ट-टर्म रिवर्सल ढूंढने के लिए, खासकर रेंज-बाउंड मार्केट में।
         *
         */
        StochasticOscillatorKIndicator stochK = new StochasticOscillatorKIndicator(series, STOCHASTIC_PERIOD);
        StochasticOscillatorDIndicator stochD = new StochasticOscillatorDIndicator(stochK);

        /*
         * CCI (Commodity Channel Index) measures how far the current typical price has deviated
         * from its statistical average, unbounded (not fixed to 0-100 like RSI/Stochastic).
         *
         *                     Overbought
         *       +100 ──────┤
         *          0 ──────┼──────── Mean / Zero Line
         *       -100 ──────┤
         *                     Oversold
         *
         * Above +100 -> price unusually high vs recent average (overbought / strong uptrend)
         * Below -100 -> price unusually low vs recent average (oversold / strong downtrend)
         *
         *
         * CCI (Commodity Channel Index) — कीमत अपने औसत से कितनी दूर भटक गई है, कोई फिक्स्ड लिमिट नहीं।
         * उदाहरण: CCI = +150 → कीमत औसत से बहुत ऊपर, तेज़ (शायद ओवरएक्सटेंडेड) मूव।
         * कहाँ इस्तेमाल करें: स्ट्रॉन्ग ट्रेंड्स और ब्रेकआउट्स की पुष्टि के लिए।
         *
         */
        CCIIndicator cci = new CCIIndicator(series, CCI_PERIOD);

        /*
         * ROC (Rate of Change) is a pure momentum indicator — the percentage change in price
         * compared to N periods ago. It oscillates around zero with no fixed upper/lower bound.
         *
         *                     Positive
         *                 ▲
         *                 │
         *                 │  (price higher than N periods ago -> rising momentum)
         *      0.0 ───────┼──────── Zero Line
         *                 │  (price lower than N periods ago -> falling momentum)
         *                 ▼
         *             Negative
         *
         * Crossing above zero -> upward momentum building
         * Crossing below zero -> downward momentum building
         *
         *
         * ROC (Rate of Change) — N दिन पहले की तुलना में सीधा प्रतिशत बदलाव।
         * उदाहरण: स्टॉक 12 दिन पहले ₹100 था, अब ₹112 है → ROC = +12%।
         * कहाँ इस्तेमाल करें: मोमेंटम की स्पीड सीधे नापने के लिए, सेक्टर/स्टॉक कंपेरिज़न में।
         *
         */
        ROCIndicator roc = new ROCIndicator(close, ROC_PERIOD);

        /*
         * OBV (On-Balance Volume) is a cumulative volume-flow indicator: it adds the bar's volume
         * to a running total when the close rises, and subtracts it when the close falls. It has
         * no fixed scale — only its trend/slope and divergence from price matter.
         *
         * OBV rising with price -> volume confirms the uptrend
         * OBV falling with price -> volume confirms the downtrend
         * OBV diverging from price (e.g. price rising, OBV falling) -> possible weakening trend
         *
         *
         * OBV (On-Balance Volume) — अप-डे पर वॉल्यूम जोड़ें, डाउन-डे पर घटाएं। सिर्फ ट्रेंड मायने रखता है।
         * उदाहरण: कीमत फ्लैट है पर OBV लगातार बढ़ रहा है → वॉल्यूम चुपचाप बढ़ रहा है, शायद ब्रेकआउट से पहले जमाखोरी (accumulation) हो रही है।
         * कहाँ इस्तेमाल करें: price-volume divergence पकड़ने के लिए — जब कीमत और वॉल्यूम अलग दिशा दिखाएं।
         *
         */
        OnBalanceVolumeIndicator obv = new OnBalanceVolumeIndicator(series);

        /*
         * CMF (Chaikin Money Flow) combines price and volume to estimate buying vs selling pressure
         * over the lookback window, expressed on a bounded scale roughly between -1 and +1.
         *
         *       +1 ──────┤  Strong buying pressure (accumulation)
         *        0 ──────┼──────── Neutral
         *       -1 ──────┤  Strong selling pressure (distribution)
         *
         * Positive and rising -> accumulation, buying pressure dominant
         * Negative and falling -> distribution, selling pressure dominant
         *
         *
         * CMF (Chaikin Money Flow) — खरीदारी बनाम बिकवाली का दबाव, करीब -1 से +1।
         * उदाहरण: CMF = +0.3 → लगातार खरीदारी का दबाव। CMF = -0.25 → लगातार बिकवाली।
         * कहाँ इस्तेमाल करें: ट्रेंड की मज़बूती को वॉल्यूम से कन्फर्म करने के लिए।
         */
        ChaikinMoneyFlowIndicator cmf = new ChaikinMoneyFlowIndicator(series, CMF_PERIOD);

        /*
         * Supertrend is a trend-following overlay built from ATR bands plotted above/below price,
         * which flip sides when price closes through the opposite band. It is not shipped as a
         * built-in TA4J indicator, so it's computed manually below (see calculateSupertrend).
         *
         * Price above Supertrend line -> uptrend, line acts as trailing support
         * Price below Supertrend line -> downtrend, line acts as trailing resistance
         * A flip (line switches sides) -> trend-change signal
         *
         *
         * Supertrend — एक लाइन जो अपट्रेंड में कीमत के नीचे और डाउनट्रेंड में ऊपर रहती है, ट्रेंड बदलने पर साइड बदल लेती है।
         * उदाहरण: कीमत ₹500 है, Supertrend लाइन ₹480 पर है → अपट्रेंड, ₹480 पर स्टॉप-लॉस रख सकते हैं।
         * कहाँ इस्तेमाल करें: ट्रेलिंग स्टॉप-लॉस सेट करने और इंट्राडे/स्विंग ट्रेड में ट्रेंड कन्फर्मेशन के लिए।
         */
        ATRIndicator supertrendAtr = new ATRIndicator(series, SUPERTREND_PERIOD);

        int idx = series.getEndIndex();

        // ========= TREND =========
        Num latestEma = ema.getValue(idx);
        Num latestEma20 = ema20.getValue(idx);
        Num latestEma50 = ema50.getValue(idx);
        Num latestEma100 = ema100.getValue(idx);
        Num latestEma200 = ema200.getValue(idx);
        Num latestAdx = adx.getValue(idx);
        Num latestPlusDi = plusDiIndicator.getValue(idx);
        Num latestMinusDi = minusDiIndicator.getValue(idx);
        double latestSupertrend = calculateSupertrend(series, supertrendAtr, idx, SUPERTREND_MULTIPLIER);

        // ========= MOMENTUM =========
        Num latestRsi14 = rsi14.getValue(idx);
        Num latestMacd = macd.getValue(idx);
        Num latestSignal = macdSignal.getValue(idx);
        Num latestHist = macdHist.getValue(idx);
        Num latestCci = cci.getValue(idx);
        Num latestRoc = roc.getValue(idx);
        Num latestStochK = stochK.getValue(idx);
        Num latestStochD = stochD.getValue(idx);

        // ========= VOLUME / VOLATILITY =========
        Num latestVwap = vwap.getValue(idx);
        Num latestBbUpper = bbUpper.getValue(idx);
        Num latestBbMid = bbMid.getValue(idx);
        Num latestBbLower = bbLower.getValue(idx);
        Num latestObv = obv.getValue(idx);
        Num latestCmf = cmf.getValue(idx);
        double latestAtr = atrIndicator.getValue(idx).doubleValue();
        double latestMfi = calculateMfi(series, idx, MFI_PERIOD);

        /*
         * Pivot / Support / Resistance are classic price-action reference levels derived from the
         * recent high/low range around the latest close (pivot). They are not TA4J indicators —
         * they're simple arithmetic levels traders watch for potential reversal/breakout points.
         *
         *   Resistance 2  ─────────────  (pivot + full range)   strong resistance
         *   Resistance 1  ─────────────  (pivot + half range)   minor resistance
         *   Pivot         ─────────────  (latest close)         reference point
         *   Support 1     ─────────────  (pivot - half range)   minor support
         *   Support 2     ─────────────  (pivot - full range)   strong support
         *
         * Price approaching a resistance level -> possible selling pressure / reversal down
         * Price approaching a support level     -> possible buying pressure / reversal up
         *
         * Pivot, Support 1/2, Resistance 1/2 — आज की क्लोज़िंग और हाल की high-low रेंज से निकाले गए लेवल।
         * उदाहरण: Pivot = ₹100, Resistance 1 = ₹105, Support 1 = ₹95। कीमत ₹105 के पास पहुंचे तो वहां रुकने (resistance) या तोड़ने (breakout) पर नज़र रखी जाती है।
         * कहाँ इस्तेमाल करें: इंट्राडे ट्रेडर्स रोज़ाना इन लेवल्स को टार्गेट और स्टॉप-लॉस तय करने के लिए इस्तेमाल करते हैं।
         */
        // ========= SUPPORT / RESISTANCE (derived from raw closePrices input) =========
        double pivot = closePrices.get(closePrices.size() - 1);
        double highest = closePrices.stream().mapToDouble(Double::doubleValue).max().orElse(pivot);
        double lowest = closePrices.stream().mapToDouble(Double::doubleValue).min().orElse(pivot);
        double highLowRange = highest - lowest;
        double support1 = pivot - (highLowRange / 2.0);
        double support2 = pivot - highLowRange;
        double resistance1 = pivot + (highLowRange / 2.0);
        double resistance2 = pivot + highLowRange;

        double latestClose = close.getValue(idx).doubleValue();
        double bbWidth = latestBbUpper.doubleValue() - latestBbLower.doubleValue();
        // %B = (price - lowerBand) / (upperBand - lowerBand). Must use the actual close price —
        // the original code used latestEma20 here, which is a different value entirely.
        double percentB = bbWidth != 0.0 ? (latestClose - latestBbLower.doubleValue()) / bbWidth : 0.0;

        return IndicatorResultDto.builder()
                .symbol(symbol)
                .symbolToken(symbolToken)
                .timeframe(timeframe)
                .candleTime(candleTime)
                // ========= TREND =========
                .ema(latestEma.doubleValue())
                .ema20(latestEma20.doubleValue())
                .ema50(latestEma50.doubleValue())
                .ema100(latestEma100.doubleValue())
                .ema200(latestEma200.doubleValue())
                .adx(latestAdx.doubleValue())
                .plusDi(latestPlusDi.doubleValue())
                .minusDi(latestMinusDi.doubleValue())
                .supertrend(latestSupertrend)
                // ========= MOMENTUM =========
                .rsi14(latestRsi14.doubleValue())
                .macd(latestMacd.doubleValue())
                .macdSignal(latestSignal.doubleValue())
                .macdHistogram(latestHist.doubleValue())
                .stochasticK(latestStochK.doubleValue())
                .stochasticD(latestStochD.doubleValue())
                .cci(latestCci.doubleValue())
                .roc(latestRoc.doubleValue())
                // ========= VOLUME =========
                .vwap(latestVwap.doubleValue())
                .obv(latestObv.doubleValue())
                .mfi(latestMfi)
                .cmf(latestCmf.doubleValue())
                // ========= VOLATILITY =========
                .atr(latestAtr)
                .bbUpper(latestBbUpper.doubleValue())
                .bbMiddle(latestBbMid.doubleValue())
                .bbLower(latestBbLower.doubleValue())
                .bbWidth(bbWidth)
                .percentB(percentB)
                // ========= SUPPORT/RESISTANCE =========
                .pivot(pivot)
                .support1(support1)
                .support2(support2)
                .resistance1(resistance1)
                .resistance2(resistance2)
                .build();
    }

    /**
     * Fallback path used when no TA4J BarSeries is available yet for the symbol/timeframe.
     * Keeps the previous behavior of returning neutral/last-value placeholders rather than failing.
     */
    private IndicatorResultDto fallbackResult(String symbol, String symbolToken, String timeframe,
            LocalDateTime candleTime, List<Double> closePrices) {
        double last = closePrices.get(closePrices.size() - 1);
        double pivot = last;
        double highLowRange = closePrices.stream().mapToDouble(Double::doubleValue).max().orElse(pivot)
                - closePrices.stream().mapToDouble(Double::doubleValue).min().orElse(pivot);
        double support1 = pivot - (highLowRange / 2.0);
        double support2 = pivot - highLowRange;
        double resistance1 = pivot + (highLowRange / 2.0);
        double resistance2 = pivot + highLowRange;

        return IndicatorResultDto.builder()
                .symbol(symbol)
                .symbolToken(symbolToken)
                .timeframe(timeframe)
                .candleTime(candleTime)
                // ========= TREND =========
                .ema(last)
                .ema20(last)
                .ema50(last)
                .ema100(last)
                .ema200(last)
                .adx(0.0)
                .plusDi(0.0)
                .minusDi(0.0)
                .supertrend(last)
                // ========= MOMENTUM =========
                .rsi14(50.0)
                .macd(0.0)
                .macdSignal(0.0)
                .macdHistogram(0.0)
                .stochasticK(0.0)
                .stochasticD(0.0)
                .cci(0.0)
                .roc(0.0)
                // ========= VOLUME =========
                .vwap(last)
                .obv(0.0)
                .mfi(50.0)
                .cmf(0.0)
                // ========= VOLATILITY =========
                .atr(0.0)
                .bbUpper(last)
                .bbMiddle(last)
                .bbLower(last)
                .bbWidth(0.0)
                .percentB(0.0)
                // ========= SUPPORT/RESISTANCE =========
                .pivot(pivot)
                .support1(support1)
                .support2(support2)
                .resistance1(resistance1)
                .resistance2(resistance2)
                .build();
    }

    /**
     * MFI (Money Flow Index) is a volume-weighted version of RSI — it measures buying/selling
     * pressure using both price and volume rather than price alone.
     *
     * <pre>
     * 0 ---------------------------100
     * Oversold      Neutral      Overbought
     * 20            50             80
     * </pre>
     *
     * Above 80 -> overbought, high volume-backed buying pressure (possible pullback)
     * Below 20 -> oversold, high volume-backed selling pressure (possible bounce)
     *
     * TA4J has no built-in indicator for this, so it's computed manually.
     * MFI = 100 - (100 / (1 + moneyFlowRatio)), using typical price ((H+L+C)/3) * volume,
     * summed separately for up periods (positive money flow) and down periods (negative money flow)
     * over the trailing `period` bars. No Wilder smoothing is applied — this is the plain-average form.
     *
     *
     * MFI (Money Flow Index) — RSI जैसा ही, पर वॉल्यूम-वेटेड। 0–100 स्केल।
     * उदाहरण: MFI = 85 → भारी खरीदारी, वॉल्यूम का मज़बूत सपोर्ट, ओवरबॉट।
     * कहाँ इस्तेमाल करें: "असली" ओवरबॉट/ओवरसोल्ड (हाई वॉल्यूम वाला) को "कमज़ोर" मूव से अलग पहचानने के लिए।
     */
    private double calculateMfi(BarSeries series, int idx, int period) {
        int start = Math.max(series.getBeginIndex(), idx - period);
        if (idx <= start) {
            return 50.0; // not enough data yet
        }

        double positiveFlow = 0.0;
        double negativeFlow = 0.0;
        double prevTypicalPrice = typicalPrice(series.getBar(start));

        for (int i = start + 1; i <= idx; i++) {
            Bar bar = series.getBar(i);
            double tp = typicalPrice(bar);
            double rawFlow = tp * bar.getVolume().doubleValue();

            if (tp > prevTypicalPrice) {
                positiveFlow += rawFlow;
            } else if (tp < prevTypicalPrice) {
                negativeFlow += rawFlow;
            }
            prevTypicalPrice = tp;
        }

        if (negativeFlow == 0.0) {
            return 100.0;
        }

        double moneyFlowRatio = positiveFlow / negativeFlow;
        return 100.0 - (100.0 / (1.0 + moneyFlowRatio));
    }

    private double typicalPrice(Bar bar) {
        return (bar.getHighPrice().doubleValue() + bar.getLowPrice().doubleValue() + bar.getClosePrice().doubleValue()) / 3.0;
    }

    /**
     * Supertrend (ATR bands with flip logic). TA4J has no built-in indicator for this, so it's
     * computed manually by walking the series from its beginning up to {@code idx} and tracking,
     * bar by bar, which band (upper/lower) the line is currently following.
     *
     * Algorithm (standard Supertrend, per-bar):
     * <pre>
     * mid          = (high + low) / 2
     * basicUpper   = mid + multiplier * ATR
     * basicLower   = mid - multiplier * ATR
     *
     * finalUpper   = (basicUpper < prevFinalUpper) OR (prevClose > prevFinalUpper)
     *                  ? basicUpper : prevFinalUpper
     * finalLower   = (basicLower > prevFinalLower) OR (prevClose < prevFinalLower)
     *                  ? basicLower : prevFinalLower
     *
     * if trend was following the upper band (downtrend):
     *     close <= finalUpper  -> stay on upper band (still downtrend)
     *     close >  finalUpper  -> flip to lower band (trend turns up)
     * if trend was following the lower band (uptrend):
     *     close >= finalLower  -> stay on lower band (still uptrend)
     *     close <  finalLower  -> flip to upper band (trend turns down)
     * </pre>
     *
     * Whichever band the line is following at {@code idx} is the returned Supertrend value.
     *
     * Note: this recomputes the whole history on every call (O(n) in series length), which is fine
     * for periodic indicator refreshes but would be wasteful if called per-tick on a very long
     * series — cache/carry the running state across calls if that becomes a bottleneck.
     *
     * @param series    the bar series (must have high/low/close populated)
     * @param atr       an ATRIndicator already built against {@code series} with the desired period
     * @param idx       the index to compute the Supertrend value for (usually series.getEndIndex())
     * @param multiplier the ATR multiplier for the bands (commonly 3.0)
     * @return the Supertrend line value at {@code idx}
     */
    private double calculateSupertrend(BarSeries series, ATRIndicator atr, int idx, double multiplier) {
        int begin = series.getBeginIndex();
        if (idx < begin) {
            return 0.0;
        }

        double prevFinalUpper = 0.0;
        double prevFinalLower = 0.0;
        double supertrend = 0.0;
        boolean followingUpperBand = true; // true = tracking upper band (downtrend), false = lower band (uptrend)
        boolean initialized = false;

        for (int i = begin; i <= idx; i++) {
            Bar bar = series.getBar(i);
            double high = bar.getHighPrice().doubleValue();
            double low = bar.getLowPrice().doubleValue();
            double closePrice = bar.getClosePrice().doubleValue();
            double atrValue = atr.getValue(i).doubleValue();

            double mid = (high + low) / 2.0;
            double basicUpper = mid + multiplier * atrValue;
            double basicLower = mid - multiplier * atrValue;

            double finalUpper;
            double finalLower;

            if (!initialized) {
                finalUpper = basicUpper;
                finalLower = basicLower;
            } else {
                double prevClose = series.getBar(i - 1).getClosePrice().doubleValue();
                finalUpper = (basicUpper < prevFinalUpper || prevClose > prevFinalUpper) ? basicUpper : prevFinalUpper;
                finalLower = (basicLower > prevFinalLower || prevClose < prevFinalLower) ? basicLower : prevFinalLower;
            }

            if (!initialized) {
                // Seed the very first bar: follow whichever band the close is inside of.
                if (closePrice <= finalUpper) {
                    supertrend = finalUpper;
                    followingUpperBand = true;
                } else {
                    supertrend = finalLower;
                    followingUpperBand = false;
                }
            } else if (followingUpperBand) {
                if (closePrice <= finalUpper) {
                    supertrend = finalUpper;
                    followingUpperBand = true;
                } else {
                    supertrend = finalLower;
                    followingUpperBand = false;
                }
            } else {
                if (closePrice >= finalLower) {
                    supertrend = finalLower;
                    followingUpperBand = false;
                } else {
                    supertrend = finalUpper;
                    followingUpperBand = true;
                }
            }

            prevFinalUpper = finalUpper;
            prevFinalLower = finalLower;
            initialized = true;
        }

        return supertrend;
    }
}