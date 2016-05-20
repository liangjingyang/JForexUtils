package com.jforex.programming.order;

import java.util.Collection;

import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.jforex.programming.order.event.OrderEvent;
import com.jforex.programming.position.Position;
import com.jforex.programming.position.RestoreSLTPPolicy;

import rx.Completable;
import rx.Observable;

public class OrderUtil {

    private final OrderPositionUtil orderPositionUtil;
    private final OrderChangeUtil orderChangeUtil;

    public OrderUtil(final OrderPositionUtil orderPositionUtil,
                     final OrderChangeUtil orderChangeUtil) {
        this.orderPositionUtil = orderPositionUtil;
        this.orderChangeUtil = orderChangeUtil;
    }

    public Position position(final Instrument instrument) {
        return orderPositionUtil.position(instrument);
    }

    public Observable<OrderEvent> submitOrder(final OrderParams orderParams) {
        return orderPositionUtil.submitOrder(orderParams);
    }

    public Observable<OrderEvent> mergeOrders(final String mergeOrderLabel,
                                              final Collection<IOrder> toMergeOrders) {
        return orderPositionUtil.mergeOrders(mergeOrderLabel, toMergeOrders);
    }

    public Observable<OrderEvent> mergePositionOrders(final String mergeOrderLabel,
                                                      final Instrument instrument,
                                                      final RestoreSLTPPolicy restoreSLTPPolicy) {
        return orderPositionUtil.mergePositionOrders(mergeOrderLabel, instrument, restoreSLTPPolicy);
    }

    public Completable closePosition(final Instrument instrument) {
        return orderPositionUtil.closePosition(instrument);
    }

    public Observable<OrderEvent> close(final IOrder orderToClose) {
        return orderChangeUtil.close(orderToClose);
    }

    public Observable<OrderEvent> setLabel(final IOrder orderToChangeLabel,
                                           final String newLabel) {
        return orderChangeUtil.setLabel(orderToChangeLabel, newLabel);
    }

    public Observable<OrderEvent> setGoodTillTime(final IOrder orderToChangeGTT,
                                                  final long newGTT) {
        return orderChangeUtil.setGoodTillTime(orderToChangeGTT, newGTT);
    }

    public Observable<OrderEvent> setOpenPrice(final IOrder orderToChangeOpenPrice,
                                               final double newOpenPrice) {
        return orderChangeUtil.setOpenPrice(orderToChangeOpenPrice, newOpenPrice);
    }

    public Observable<OrderEvent> setRequestedAmount(final IOrder orderToChangeRequestedAmount,
                                                     final double newRequestedAmount) {
        return orderChangeUtil.setRequestedAmount(orderToChangeRequestedAmount, newRequestedAmount);
    }

    public Observable<OrderEvent> setStopLossPrice(final IOrder orderToChangeSL,
                                                   final double newSL) {
        return orderChangeUtil.setStopLossPrice(orderToChangeSL, newSL);
    }

    public Observable<OrderEvent> setTakeProfitPrice(final IOrder orderToChangeTP,
                                                     final double newTP) {
        return orderChangeUtil.setTakeProfitPrice(orderToChangeTP, newTP);
    }
}
