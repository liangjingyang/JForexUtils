package com.jforex.programming.instrument;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.collections4.keyvalue.MultiKey;

import com.dukascopy.api.ICurrency;
import com.dukascopy.api.Instrument;
import com.jforex.programming.math.MathUtil;
import com.jforex.programming.misc.StreamUtil;

public final class InstrumentFactory {

    private InstrumentFactory() {
    }

    private static final Map<MultiKey<Object>, Instrument> instrumentByCurrencies = new ConcurrentHashMap<>();

    public static final Optional<Instrument> maybeFromName(final String instrumentName) {
        checkNotNull(instrumentName);

        final String upperCaseName = instrumentName.toUpperCase();
        return Instrument.isInverted(upperCaseName)
                ? Optional.of(Instrument.fromInvertedString(upperCaseName))
                : Optional.ofNullable(Instrument.fromString(upperCaseName));
    }

    public static final Optional<Instrument> maybeFromCurrencies(final ICurrency firstCurrency,
                                                                 final ICurrency secondCurrency) {
        checkNotNull(firstCurrency);
        checkNotNull(secondCurrency);

        return firstCurrency.equals(secondCurrency)
                ? Optional.empty()
                : Optional.of(createMapEntry(firstCurrency, secondCurrency));
    }

    private static Instrument createMapEntry(final ICurrency firstCurrency,
                                             final ICurrency secondCurrency) {
        return instrumentByCurrencies
            .computeIfAbsent(new MultiKey<>(firstCurrency, secondCurrency),
                             k -> fromCurrencies(firstCurrency, secondCurrency));
    }

    private static Instrument fromCurrencies(final ICurrency firstCurrency,
                                             final ICurrency secondCurrency) {
        final String instrumentName = InstrumentUtil.nameFromCurrencies(firstCurrency, secondCurrency);
        return maybeFromName(instrumentName).get();
    }

    public static final Set<Instrument> combineAllFromCurrencySet(final Set<ICurrency> currencies) {
        checkNotNull(currencies);

        return MathUtil
            .kPowerSet(currencies, 2)
            .stream()
            .map(ArrayList<ICurrency>::new)
            .map(pair -> maybeFromCurrencies(pair.get(0), pair.get(1)))
            .flatMap(StreamUtil::optionalStream)
            .collect(toSet());
    }

    public static final Set<Instrument> combineAllWithAnchorCurrency(final ICurrency anchorCurrency,
                                                                     final Set<ICurrency> partnerCurrencies) {
        checkNotNull(anchorCurrency);
        checkNotNull(partnerCurrencies);

        return partnerCurrencies
            .stream()
            .map(partnerCurrency -> maybeFromCurrencies(anchorCurrency, partnerCurrency))
            .flatMap(StreamUtil::optionalStream)
            .collect(toSet());
    }
}
