package com.jforex.programming.quote.test;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.dukascopy.api.OfferSide;
import com.google.common.collect.Sets;
import com.jforex.programming.quote.TickQuote;
import com.jforex.programming.quote.TickQuoteProvider;
import com.jforex.programming.quote.TickQuoteRepository;
import com.jforex.programming.test.common.QuoteProviderForTest;

import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;

public class TickQuoteHandlerTest extends QuoteProviderForTest {

    private TickQuoteProvider tickQuoteHandler;

    @Mock
    private TickQuoteRepository tickQuoteRepositoryMock;
    private final Observable<TickQuote> quoteObservable = Observable.just(tickQuoteEURUSD, tickQuoteAUDUSD);
    private final TestObserver<TickQuote> unfilteredQuoteSubscriber = TestObserver.create();
    private final TestObserver<TickQuote> quoteEURUSDAndAUDUSDSubscriber = TestObserver.create();
    private final TestObserver<TickQuote> quoteGBPAUDSubscriber = TestObserver.create();

    @Before
    public void setUp() {
        setUpMocks();

        tickQuoteHandler = new TickQuoteProvider(quoteObservable, tickQuoteRepositoryMock);

        tickQuoteHandler
            .observable()
            .subscribe(unfilteredQuoteSubscriber);

        tickQuoteHandler
            .observableForInstruments(Sets.newHashSet(instrumentEURUSD, instrumentAUDUSD))
            .subscribe(quoteEURUSDAndAUDUSDSubscriber);

        tickQuoteHandler
            .observableForInstruments(Sets.newHashSet(instrumentGBPAUD))
            .subscribe(quoteGBPAUDSubscriber);
    }

    private void setUpMocks() {
        when(tickQuoteRepositoryMock.get(instrumentEURUSD))
            .thenReturn(tickQuoteEURUSD);
        when(tickQuoteRepositoryMock.get(instrumentAUDUSD))
            .thenReturn(tickQuoteAUDUSD);
    }

    private void assertCommonEmittedTicks(final TestObserver<TickQuote> subscriber) {
        subscriber.assertNoErrors();
        subscriber.assertValueCount(2);

        assertThat(getOnNextEvent(subscriber, 0),
                   equalTo(tickQuoteEURUSD));
        assertThat(getOnNextEvent(subscriber, 1),
                   equalTo(tickQuoteAUDUSD));
    }

    @Test
    public void returnedTickIsCorrect() {
        assertThat(tickQuoteHandler.tick(instrumentEURUSD),
                   equalTo(tickEURUSD));
    }

    @Test
    public void returnedAskIsCorrect() {
        assertThat(tickQuoteHandler.ask(instrumentEURUSD),
                   equalTo(tickEURUSD.getAsk()));
    }

    @Test
    public void returnedBidIsCorrect() {
        assertThat(tickQuoteHandler.bid(instrumentEURUSD),
                   equalTo(tickEURUSD.getBid()));
    }

    @Test
    public void returnedAskForOfferSideIsCorrect() {
        assertThat(tickQuoteHandler.forOfferSide(instrumentEURUSD, OfferSide.ASK),
                   equalTo(tickEURUSD.getAsk()));
    }

    @Test
    public void returnedBidForOfferSideIsCorrect() {
        assertThat(tickQuoteHandler.forOfferSide(instrumentEURUSD, OfferSide.BID),
                   equalTo(tickEURUSD.getBid()));
    }

    @Test
    public void ticksAreEmitted() {
        assertCommonEmittedTicks(quoteEURUSDAndAUDUSDSubscriber);
    }

    @Test
    public void unFilteredTicksAreEmitted() {
        assertCommonEmittedTicks(unfilteredQuoteSubscriber);
    }

    @Test
    public void ticksAreNotEmittedForGBPAUDSubscriber() {
        quoteGBPAUDSubscriber.assertNoErrors();
        quoteGBPAUDSubscriber.assertValueCount(0);
    }
}
