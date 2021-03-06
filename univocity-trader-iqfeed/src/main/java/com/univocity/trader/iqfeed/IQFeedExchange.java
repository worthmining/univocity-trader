package com.univocity.trader.iqfeed;

import com.univocity.trader.*;
import com.univocity.trader.candles.*;
import com.univocity.trader.indicators.base.*;
import com.univocity.trader.iqfeed.api.*;
import com.univocity.trader.iqfeed.api.constant.*;
import com.univocity.trader.iqfeed.api.domain.candles.*;
import com.univocity.trader.iqfeed.api.domain.request.*;
import io.netty.channel.*;
import io.netty.channel.nio.*;
import org.asynchttpclient.*;
import org.slf4j.*;

import java.io.*;
import java.math.*;
import java.net.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import java.util.concurrent.*;

class IQFeedExchange implements Exchange<IQFeedCandle, Account> {

	private static final Logger logger = LoggerFactory.getLogger(IQFeedExchange.class);

	private IQFeedApiWebSocketClient socketClient;
	private org.asynchttpclient.ws.WebSocket socketClientCloseable;
	private final Map<String, SymbolInformation> symbolInformation = new ConcurrentHashMap<>();

	private boolean iqPortal = false;
	private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup(2);
	// TODO: ask about maxFrameSize
	private final AsyncHttpClient asyncHttpClient = HttpUtils.newAsyncHttpClient(eventLoopGroup, 655356);




	@Override
	public IQFeedClientAccount connectToAccount(Account account) {
		if (!iqPortal) {
			try {
				Runtime.getRuntime().exec(account.iqPortalPath(), null, new File(account.iqPortalPath()));
				iqPortal = true;
			} catch (Exception e) {
				logger.info(e.getMessage());
			}
		}
		return new IQFeedClientAccount();
	}

	// TODO: implement
	@Override
	public Map<String, SymbolInformation> getSymbolInformation() {
		return new HashMap<>();
	}

	;

	@Override
	public IQFeedCandle getLatestTick(String symbol, TimeInterval interval) {
		// TODO: implement
		return null;
	}

	@Override
	public Map<String, Double> getLatestPrices() {
		return new HashMap<>();
	}

	@Override
	public double getLatestPrice(String assetSymbol, String fundSymbol) {
		return new Double(0);
	}

	@Override
	public List<IQFeedCandle> getLatestTicks(String symbol, TimeInterval interval) {
		// TODO: implement
		ChronoUnit timeUnit = null;
		switch (TimeInterval.getUnitStr(interval.unit)) {
			case "d":
				timeUnit = ChronoUnit.DAYS;
				break;
			case "h":
				timeUnit = ChronoUnit.HOURS;
				break;
			case "m":
				timeUnit = ChronoUnit.MINUTES;
				break;
			case "s":
				timeUnit = ChronoUnit.SECONDS;
				break;
			case "ms":
				timeUnit = ChronoUnit.MILLIS;
				break;
		}
		StringBuilder requestIDBuilder = new StringBuilder("IQFeedLatestTicksRequest_" + Instant.now().toString());
		requestIDBuilder.append("_symbol:" + symbol + "_interval:" + interval.toString());
		IQFeedHistoricalRequest request = new IQFeedHistoricalRequestBuilder()
				.setRequestID(requestIDBuilder.toString())
				.setSymbol(symbol)
				.setIntervalType(interval)
				.setBeginDateTime(Instant.now().minus(100L, timeUnit).toEpochMilli())
				.setEndDateTime(Instant.now().toEpochMilli())
				.build();

		List<IQFeedCandle> candles = socketClient().getHistoricalCandlestickBars(request);
		return candles;
	}

	//TODO: implement
	@Override
	public List<IQFeedCandle> getHistoricalTicks(String symbol, TimeInterval interval, long startTime, long endTime) {
		StringBuilder requestIDBuilder = new StringBuilder("IQFeedHistoricalRequest_" + Instant.now().toString());
		requestIDBuilder.append("_symbol:" + symbol + "_interval:" + interval.toString() + "_start:" + startTime + "_end:" + endTime);
		IQFeedHistoricalRequest request = new IQFeedHistoricalRequestBuilder()
				.setRequestID(requestIDBuilder.toString())
				.setSymbol(symbol)
				.setIntervalType(interval)
				.setBeginDateTime(startTime)
				.setEndDateTime(endTime)
				.build();
		return socketClient.getHistoricalCandlestickBars(request);
	}
	// TODO: add callback for connection login via IQFeed

	@Override
	public Candle generateCandle(IQFeedCandle c) {
		return new Candle(
				c.getOpenTime(),
				c.getCloseTime(),
				c.getOpen(),
				c.getHigh(),
				c.getLow(),
				c.getClose(),
				c.getVolume()
		);
	}

	public PreciseCandle generatePreciseCandle(IQFeedCandle c) {
		return new PreciseCandle(
				c.getOpenTime(),
				c.getCloseTime(),
				new BigDecimal(c.getOpen()),
				new BigDecimal(c.getHigh()),
				new BigDecimal(c.getLow()),
				new BigDecimal(c.getClose()),
				new BigDecimal(c.getVolume()));
	}

//    @Override
//    public List<Candlestick> getHistoricalTicks(String symbol, TimeInterval interval, long startTime, long endTime){
//        return socketClient().getC
//    }

	@Override
	public TimeInterval handlePollingException(String symbol, Exception e) {
		String message = "execute polling for " + symbol;
		if (e.getCause() instanceof TimeoutException) {
			logger.error("Timeout trying to " + message, e);
		} else if (e.getCause() instanceof UnknownHostException) {
			logger.error("Unable to " + message + ". Binance is offline.", e);
			return TimeInterval.minutes(1);
		} else {
			logger.error("Error trying to " + message, e);
		}
		return null;
	}

	// TODO: implement this...
	@Override
	public void openLiveStream(String symbols, TimeInterval tickInterval, TickConsumer<IQFeedCandle> consumer) {
	}

	@Override
	public void closeLiveStream() {
		if (socketClientCloseable != null) {
			socketClientCloseable.sendCloseFrame();
			socketClientCloseable = null;
		}
	}


	private IQFeedApiWebSocketClient socketClient() {
		if (socketClient == null) {
			IQFeedApiClientFactory factory = IQFeedApiClientFactory.newInstance(asyncHttpClient);
			socketClient = factory.newWebSocketClient();
		}
		return socketClient;
	}

}

