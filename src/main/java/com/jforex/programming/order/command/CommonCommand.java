package com.jforex.programming.order.command;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

import com.dukascopy.api.IOrder;
import com.jforex.programming.order.call.OrderCallReason;
import com.jforex.programming.order.event.OrderEvent;
import com.jforex.programming.order.event.OrderEventType;
import com.jforex.programming.order.event.OrderEventTypeData;

import rx.Completable;
import rx.functions.Action0;

public abstract class CommonCommand {

    private final Action0 subscribeAction;
    private final Action0 completedAction;
    private final Consumer<Throwable> errorAction;
    private final Consumer<OrderEvent> eventAction;
    private final Callable<IOrder> callable;
    private final OrderCallReason callReason;
    private final OrderEventTypeData orderEventTypeData;
    private final int noOfRetries;
    private final long retryDelayInMillis;
    private final Map<OrderEventType, Consumer<IOrder>> eventHandlerForType;
    private final Function<CommonCommand, Completable> startFunction;

    @SuppressWarnings("unchecked")
    protected CommonCommand(final CommonBuilder<?> builder) {
        callable = builder.callable;
        callReason = builder.callReason;
        orderEventTypeData = builder.orderEventTypeData;
        subscribeAction = builder.startAction;
        completedAction = builder.completedAction;
        eventAction = builder.eventAction;
        errorAction = builder.errorAction;
        noOfRetries = builder.noOfRetries;
        retryDelayInMillis = builder.retryDelayInMillis;
        eventHandlerForType = builder.eventHandlerForType;
        startFunction = (Function<CommonCommand, Completable>) builder.startFunction;
    }

    public final Completable completable() {
        return startFunction.apply(this);
    }

    public final Action0 startAction() {
        return subscribeAction;
    }

    public final Action0 completedAction() {
        return completedAction;
    }

    public final Consumer<OrderEvent> eventAction() {
        return eventAction;
    }

    public final Consumer<Throwable> errorAction() {
        return errorAction;
    }

    public final Callable<IOrder> callable() {
        return callable;
    }

    public final OrderCallReason callReason() {
        return callReason;
    }

    public final boolean isEventTypeForCommand(final OrderEventType orderEventType) {
        return orderEventTypeData
            .allEventTypes()
            .contains(orderEventType);
    }

    private final boolean isDoneEventType(final OrderEventType orderEventType) {
        return orderEventTypeData
            .doneEventTypes()
            .contains(orderEventType);
    }

    public final boolean isRejectEventType(final OrderEventType orderEventType) {
        return orderEventTypeData
            .rejectEventTypes()
            .contains(orderEventType);
    }

    public final boolean isFinishEventType(final OrderEventType orderEventType) {
        return isDoneEventType(orderEventType) || isRejectEventType(orderEventType);
    }

    public final int noOfRetries() {
        return noOfRetries;
    }

    public final long retryDelayInMillis() {
        return retryDelayInMillis;
    }

    public final Map<OrderEventType, Consumer<IOrder>> eventHandlerForType() {
        return eventHandlerForType;
    }
}
