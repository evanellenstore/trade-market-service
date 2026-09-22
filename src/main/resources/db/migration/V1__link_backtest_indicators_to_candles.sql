-- Run the verification query before enforcing NOT NULL.
-- Any returned rows need to be investigated because they have no matching candle.
SELECT i.id, i.symbol_token, i.timeframe, i.candle_time
FROM market_indicators_backtest i
LEFT JOIN market_candles_backtest c
  ON c.symbol_token = i.symbol_token
 AND c.timeframe = i.timeframe
 AND c.candle_time = i.candle_time
WHERE i.candle_id IS NULL
  AND c.id IS NULL;

ALTER TABLE market_indicators_backtest
    ADD COLUMN IF NOT EXISTS candle_id BIGINT NULL;

UPDATE market_indicators_backtest i
JOIN market_candles_backtest c
  ON c.symbol_token = i.symbol_token
 AND c.timeframe = i.timeframe
 AND c.candle_time = i.candle_time
SET i.candle_id = c.id
WHERE i.candle_id IS NULL;

ALTER TABLE market_indicators_backtest
    ADD CONSTRAINT uk_backtest_indicator_candle_id UNIQUE (candle_id);

ALTER TABLE market_indicators_backtest
    ADD CONSTRAINT fk_backtest_indicator_candle
    FOREIGN KEY (candle_id) REFERENCES market_candles_backtest(id);