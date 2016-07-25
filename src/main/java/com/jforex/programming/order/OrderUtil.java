package com.jforex.programming.order;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.jforex.programming.order.OrderStaticUtil.isAmountSetTo;
import static com.jforex.programming.order.OrderStaticUtil.isClosed;
import static com.jforex.programming.order.OrderStaticUtil.isGTTSetTo;
import static com.jforex.programming.order.OrderStaticUtil.isLabelSetTo;
import static com.jforex.programming.order.OrderStaticUtil.isOpenPriceSetTo;
import static com.jforex.programming.order.OrderStaticUtil.isSLSetTo;
import static com.jforex.programming.order.OrderStaticUtil.isTPSetTo;

import java.util.Collection;
import java.util.Set;

import org.aeonbits.owner.ConfigFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.dukascopy.api.IEngine;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.jforex.programming.order.command.CloseCommand;
import com.jforex.programming.order.command.MergeCommand;
import com.jforex.programming.order.command.SetAmountCommand;
import com.jforex.programming.order.command.SetGTTCommand;
import com.jforex.programming.order.command.SetLabelCommand;
import com.jforex.programming.order.command.SetOpenPriceCommand;
import com.jforex.programming.order.command.SetSLCommand;
import com.jforex.programming.order.command.SetTPCommand;
import com.jforex.programming.order.command.SubmitCommand;
import com.jforex.programming.order.event.OrderEvent;
import com.jforex.programming.order.event.OrderEventTypeData;
import com.jforex.programming.position.OrderProcessState;
import com.jforex.programming.position.Position;
import com.jforex.programming.position.PositionFactory;
import com.jforex.programming.position.PositionOrders;
import com.jforex.programming.settings.PlatformSettings;

import rx.Observable;

public class OrderUtil {

    private final IEngine engine;
    private final PositionFactory positionFactory;
    private final OrderUtilHandler orderUtilHandler;

    private static final PlatformSettings platformSettings =
            ConfigFactory.create(PlatformSettings.class);
    private static final Logger logger = LogManager.getLogger(OrderUtil.class);

    public OrderUtil(final IEngine engine,
                     final PositionFactory positionFactory,
                     final OrderUtilHandler orderUtilHandler) {
        this.engine = engine;
        this.positionFactory = positionFactory;
        this.orderUtilHandler = orderUtilHandler;
    }

    public PositionOrders positionOrders(final Instrument instrument) {
        return positionFactory.forInstrument(checkNotNull(instrument));
    }

    public Observable<OrderEvent> submitOrder(final OrderParams orderParams) {
        checkNotNull(orderParams);

        return orderUtilHandler
                .callObservable(new SubmitCommand(orderParams, engine))
                .doOnNext(submitEvent -> addOrderToPositionIfDone(submitEvent,
                                                                  OrderEventTypeData.submitData));
    }

    private void addOrderToPositionIfDone(final OrderEvent orderEvent,
                                          final OrderEventTypeData orderEventTypeData) {
        if (orderEventTypeData.isDoneType(orderEvent.type())) {
            final IOrder order = orderEvent.order();
            position(order.getInstrument()).addOrder(order);
        }
    }

    public Observable<OrderEvent> mergeOrders(final String mergeOrderLabel,
                                              final Collection<IOrder> toMergeOrders) {
        checkNotNull(mergeOrderLabel);
        checkNotNull(toMergeOrders);

        return Observable
                .just(toMergeOrders)
                .filter(orders -> orders.size() > 1)
                .flatMap(orders -> orderUtilHandler
                        .callObservable(new MergeCommand(mergeOrderLabel, toMergeOrders, engine))
                        .doOnNext(mergeEvent -> addOrderToPositionIfDone(mergeEvent,
                                                                         OrderEventTypeData.mergeData)));
    }

    private Position position(final Instrument instrument) {
        return (Position) positionOrders(instrument);
    }

    public Observable<OrderEvent> mergePositionOrders(final String mergeOrderLabel,
                                                      final Instrument instrument) {
        checkNotNull(mergeOrderLabel);
        checkNotNull(instrument);

        final Position position = position(instrument);
        final Set<IOrder> toMergeOrders = position(instrument).filled();
        return Observable
                .just(toMergeOrders)
                .doOnSubscribe(() -> logger.debug("Starting position merge for " +
                        instrument + " with label " + mergeOrderLabel))
                .filter(orders -> orders.size() > 1)
                .doOnNext(orders -> position.markAllOrders(OrderProcessState.ACTIVE))
                .flatMap(this::removeTPSLObservable)
                .toCompletable()
                .andThen(mergeOrders(mergeOrderLabel, toMergeOrders))
                .doOnTerminate(() -> position.markAllOrders(OrderProcessState.IDLE))
                .doOnError(e -> logger.error("Position merge for " + instrument
                        + "  with label " + mergeOrderLabel + " failed!" +
                        "Exception: " + e.getMessage()))
                .doOnCompleted(() -> logger.debug("Position merge for " + instrument
                        + "  with label " + mergeOrderLabel + " was successful."));
    }

    public Observable<OrderEvent> closePosition(final Instrument instrument) {
        final Position position = position(checkNotNull(instrument));
        return Observable
                .from(position.filledOrOpened())
                .doOnSubscribe(() -> {
                    logger.debug("Starting position close for " + checkNotNull(instrument));
                    position.markAllOrders(OrderProcessState.ACTIVE);
                })
                .flatMap(this::close)
                .doOnTerminate(() -> position.markAllOrders(OrderProcessState.IDLE))
                .doOnCompleted(() -> logger.debug("Closing position "
                        + instrument + " was successful."))
                .doOnError(e -> logger.error("Closing position " + instrument
                        + " failed! Exception: " + e.getMessage()));
    }

    public Observable<OrderEvent> close(final IOrder orderToClose) {
        checkNotNull(orderToClose);

        return Observable
                .just(orderToClose)
                .filter(order -> !isClosed.test(order))
                .flatMap(order -> orderUtilHandler.callObservable(new CloseCommand(order)));
    }

    public Observable<OrderEvent> setLabel(final IOrder orderToChangeLabel,
                                           final String newLabel) {
        checkNotNull(orderToChangeLabel);

        return Observable
                .just(orderToChangeLabel)
                .filter(order -> !isLabelSetTo(newLabel).test(order))
                .flatMap(order -> orderUtilHandler
                        .callObservable(new SetLabelCommand(orderToChangeLabel, newLabel)));
    }

    public Observable<OrderEvent> setGoodTillTime(final IOrder orderToChangeGTT,
                                                  final long newGTT) {
        checkNotNull(orderToChangeGTT);

        return Observable
                .just(orderToChangeGTT)
                .filter(order -> !isGTTSetTo(newGTT).test(order))
                .flatMap(order -> orderUtilHandler
                        .callObservable(new SetGTTCommand(orderToChangeGTT, newGTT)));
    }

    public Observable<OrderEvent> setOpenPrice(final IOrder orderToChangeOpenPrice,
                                               final double newOpenPrice) {
        checkNotNull(orderToChangeOpenPrice);

        return Observable
                .just(orderToChangeOpenPrice)
                .filter(order -> !isOpenPriceSetTo(newOpenPrice).test(order))
                .flatMap(order -> orderUtilHandler
                        .callObservable(new SetOpenPriceCommand(orderToChangeOpenPrice, newOpenPrice)));
    }

    public Observable<OrderEvent> setRequestedAmount(final IOrder orderToChangeAmount,
                                                     final double newRequestedAmount) {
        checkNotNull(orderToChangeAmount);

        return Observable
                .just(orderToChangeAmount)
                .filter(order -> !isAmountSetTo(newRequestedAmount).test(order))
                .flatMap(order -> orderUtilHandler
                        .callObservable(new SetAmountCommand(orderToChangeAmount, newRequestedAmount)));
    }

    public Observable<OrderEvent> setStopLossPrice(final IOrder orderToChangeSL,
                                                   final double newSL) {
        checkNotNull(orderToChangeSL);

        return Observable
                .just(orderToChangeSL)
                .filter(order -> !isSLSetTo(newSL).test(order))
                .flatMap(order -> orderUtilHandler
                        .callObservable(new SetSLCommand(orderToChangeSL, newSL)));
    }

    public Observable<OrderEvent> setTakeProfitPrice(final IOrder orderToChangeTP,
                                                     final double newTP) {
        checkNotNull(orderToChangeTP);

        return Observable
                .just(orderToChangeTP)
                .filter(order -> !isTPSetTo(newTP).test(order))
                .flatMap(order -> orderUtilHandler
                        .callObservable(new SetTPCommand(orderToChangeTP, newTP)));
    }

    private Observable<OrderEvent> removeTPSLObservable(final Set<IOrder> filledOrders) {
        final Instrument instrument = filledOrders.iterator().next().getInstrument();

        return Observable
                .from(filledOrders)
                .doOnSubscribe(() -> logger.debug("Starting remove TPSL task for position "
                        + instrument))
                .flatMap(this::removeSingleTPSLObservable)
                .doOnCompleted(() -> logger.debug("Removing TPSL task from "
                        + instrument + " was successful."))
                .doOnError(e -> logger.error("Removing TPSL from " + instrument
                        + " failed! Exception: " + e.getMessage()));
    }

    private final Observable<OrderEvent> removeSingleTPSLObservable(final IOrder orderToRemoveSLTP) {
        return setTakeProfitPrice(orderToRemoveSLTP, platformSettings.noSLPrice())
                .concatWith(setStopLossPrice(orderToRemoveSLTP, platformSettings.noTPPrice()));
    }
}
