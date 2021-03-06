package com.jforex.programming.instrument;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Currency;
import java.util.Set;

import com.dukascopy.api.IBar;
import com.dukascopy.api.ICurrency;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.OfferSide;
import com.jforex.programming.currency.CurrencyCode;
import com.jforex.programming.currency.CurrencyFactory;
import com.jforex.programming.currency.CurrencyUtil;
import com.jforex.programming.math.CalculationUtil;
import com.jforex.programming.quote.BarParams;
import com.jforex.programming.quote.BarQuoteProvider;
import com.jforex.programming.quote.TickQuoteProvider;

public final class InstrumentUtil {

    private final Instrument instrument;
    private final TickQuoteProvider tickQuoteProvider;
    private final BarQuoteProvider barQuoteProvider;
    private final CalculationUtil calculationUtil;
    private final ICurrency baseCurrency;
    private final ICurrency quoteCurrency;
    private final Currency baseJavaCurrency;
    private final Currency quoteJavaCurrency;
    private final int numberOfDigits;
    private final String toStringNoSeparator;
    private final String toString;
    private final Set<ICurrency> currencies;

    private static final String pairsSeparator = Instrument.getPairsSeparator();

    public InstrumentUtil(final Instrument instrument,
                          final TickQuoteProvider tickQuoteProvider,
                          final BarQuoteProvider barQuoteProvider,
                          final CalculationUtil calculationUtil) {
        this.instrument = instrument;
        this.tickQuoteProvider = tickQuoteProvider;
        this.barQuoteProvider = barQuoteProvider;
        this.calculationUtil = calculationUtil;

        baseCurrency = instrument.getPrimaryJFCurrency();
        quoteCurrency = instrument.getSecondaryJFCurrency();
        baseJavaCurrency = baseJavaCurrency(instrument);
        quoteJavaCurrency = quoteJavaCurrency(instrument);
        numberOfDigits = numberOfDigits(instrument);
        toStringNoSeparator = toStringNoSeparator(instrument);
        toString = nameFromCurrencies(baseCurrency, quoteCurrency);
        currencies = CurrencyFactory.fromInstrument(instrument);
    }

    public final ITick tickQuote() {
        return tickQuoteProvider.tick(instrument);
    }

    public final double askQuote() {
        return tickQuoteProvider.ask(instrument);
    }

    public final double bidQuote() {
        return tickQuoteProvider.bid(instrument);
    }

    public final IBar barQuote(final BarParams barParams) {
        checkNotNull(barParams);

        return barQuoteProvider.bar(barParams);
    }

    public final double spread() {
        return CalculationUtil.pipDistance(instrument,
                                           askQuote(),
                                           bidQuote());
    }

    public final Currency baseJavaCurrency() {
        return baseJavaCurrency;
    }

    public final Currency quoteJavaCurrency() {
        return quoteJavaCurrency;
    }

    public final int numberOfDigits() {
        return numberOfDigits;
    }

    public final String toStringNoSeparator() {
        return toStringNoSeparator;
    }

    public final String toString() {
        return toString;
    }

    public final Set<ICurrency> currencies() {
        return currencies;
    }

    public double scalePips(final double pips) {
        return CalculationUtil.scalePipsToInstrument(pips, instrument);
    }

    public double addPipsToPrice(final double price,
                                 final double pipsToAdd) {
        return CalculationUtil.addPipsToPrice(instrument,
                                              price,
                                              pipsToAdd);
    }

    public double pipDistanceOfPrices(final double priceA,
                                      final double priceB) {
        return CalculationUtil.pipDistance(instrument,
                                           priceA,
                                           priceB);
    }

    public boolean isPricePipDivisible(final double price) {
        return CalculationUtil.isPricePipDivisible(instrument, price);
    }

    public double convertAmount(final double amount,
                                final Instrument targetInstrument,
                                final OfferSide offerSide) {
        checkNotNull(targetInstrument);
        checkNotNull(offerSide);

        return calculationUtil.convertAmount(amount,
                                             baseCurrency,
                                             targetInstrument.getPrimaryJFCurrency(),
                                             offerSide);
    }

    public double pipValueInCurrency(final double amount,
                                     final ICurrency targetCurrency,
                                     final OfferSide offerSide) {
        checkNotNull(targetCurrency);
        checkNotNull(offerSide);

        return calculationUtil.pipValueInCurrency(amount,
                                                  instrument,
                                                  targetCurrency,
                                                  offerSide);
    }

    public final boolean containsCurrency(final ICurrency currency) {
        checkNotNull(currency);

        return CurrencyUtil.isInInstrument(currency, instrument);
    }

    public final boolean containsCurrencyCode(final CurrencyCode currencyCode) {
        checkNotNull(currencyCode);

        return CurrencyUtil.isInInstrument(currencyCode.toString(), instrument);
    }

    public static final int numberOfDigits(final Instrument instrument) {
        checkNotNull(instrument);

        return instrument.getPipScale() + 1;
    }

    public static final String toStringNoSeparator(final Instrument instrument) {
        checkNotNull(instrument);

        return instrument
            .getPrimaryJFCurrency()
            .toString()
            .concat(instrument.getSecondaryJFCurrency().toString());
    }

    public static final Currency baseJavaCurrency(final Instrument instrument) {
        checkNotNull(instrument);

        return instrument
            .getPrimaryJFCurrency()
            .getJavaCurrency();
    }

    public static final Currency quoteJavaCurrency(final Instrument instrument) {
        checkNotNull(instrument);

        return instrument
            .getSecondaryJFCurrency()
            .getJavaCurrency();
    }

    public static final String baseCurrencyName(final Instrument instrument) {
        checkNotNull(instrument);

        return instrument
            .getPrimaryJFCurrency()
            .getCurrencyCode();
    }

    public static final String quoteCurrencyName(final Instrument instrument) {
        checkNotNull(instrument);

        return instrument
            .getSecondaryJFCurrency()
            .getCurrencyCode();
    }

    public static final String nameFromCurrencies(final ICurrency baseCurrency,
                                                  final ICurrency quoteCurrency) {
        checkNotNull(baseCurrency);
        checkNotNull(quoteCurrency);

        return baseCurrency
            .toString()
            .concat(pairsSeparator)
            .concat(quoteCurrency.toString());
    }
}
