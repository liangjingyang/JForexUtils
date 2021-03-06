package com.jforex.programming.order.task;

import java.util.Collection;
import java.util.concurrent.Callable;

import com.dukascopy.api.IEngine;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.OfferSide;
import com.jforex.programming.misc.StrategyThreadTask;
import com.jforex.programming.order.OrderParams;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.functions.Action;

public class TaskExecutor {

    private final StrategyThreadTask strategyThreadTask;
    private final IEngine engine;

    public TaskExecutor(final StrategyThreadTask strategyThreadTask,
                        final IEngine engine) {
        this.strategyThreadTask = strategyThreadTask;
        this.engine = engine;
    }

    public Single<IOrder> submitOrder(final OrderParams orderParams) {
        return single(() -> engine.submitOrder(orderParams.label(),
                                               orderParams.instrument(),
                                               orderParams.orderCommand(),
                                               orderParams.amount(),
                                               orderParams.price(),
                                               orderParams.slippage(),
                                               orderParams.stopLossPrice(),
                                               orderParams.takeProfitPrice(),
                                               orderParams.goodTillTime(),
                                               orderParams.comment()));
    }

    public Single<IOrder> mergeOrders(final String mergeOrderLabel,
                                      final Collection<IOrder> toMergeOrders) {
        return single(() -> engine.mergeOrders(mergeOrderLabel, toMergeOrders));
    }

    public Completable close(final IOrder order) {
        return completable(() -> order.close());
    }

    public Completable close(final IOrder order,
                             final double amount) {
        return completable(() -> order.close(amount));
    }

    public Completable close(final IOrder order,
                             final double amount,
                             final double price) {
        return completable(() -> order.close(amount, price));
    }

    public Completable close(final IOrder order,
                             final double amount,
                             final double price,
                             final double slippage) {
        return completable(() -> order.close(amount,
                                             price,
                                             slippage));
    }

    public Completable setLabel(final IOrder order,
                                final String label) {
        return completable(() -> order.setLabel(label));
    }

    public Completable setGoodTillTime(final IOrder order,
                                       final long newGTT) {
        return completable(() -> order.setGoodTillTime(newGTT));
    }

    public Completable setRequestedAmount(final IOrder order,
                                          final double newRequestedAmount) {
        return completable(() -> order.setRequestedAmount(newRequestedAmount));
    }

    public Completable setOpenPrice(final IOrder order,
                                    final double newOpenPrice) {
        return completable(() -> order.setOpenPrice(newOpenPrice));
    }

    public Completable setStopLossPrice(final IOrder order,
                                        final double newSL) {
        return completable(() -> order.setStopLossPrice(newSL));
    }

    public Completable setStopLossPrice(final IOrder order,
                                        final double newSL,
                                        final OfferSide offerSide) {
        return completable(() -> order.setStopLossPrice(newSL, offerSide));
    }

    public Completable setStopLossPrice(final IOrder order,
                                        final double newSL,
                                        final OfferSide offerSide,
                                        final double trailingStep) {
        return completable(() -> order.setStopLossPrice(newSL,
                                                        offerSide,
                                                        trailingStep));
    }

    public Completable setTakeProfitPrice(final IOrder order,
                                          final double newTP) {
        return completable(() -> order.setTakeProfitPrice(newTP));
    }

    private Single<IOrder> single(final Callable<IOrder> callable) {
        return strategyThreadTask.execute(callable);
    }

    private Completable completable(final Action action) {
        return strategyThreadTask.execute(action);
    }
}
