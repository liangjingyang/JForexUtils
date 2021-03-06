package com.jforex.programming.order.task;

import java.util.Collection;

import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.google.common.base.Supplier;
import com.jforex.programming.order.command.MergeCommand;
import com.jforex.programming.order.event.OrderEvent;
import com.jforex.programming.position.PositionUtil;

import io.reactivex.Observable;
import io.reactivex.functions.Function;

public class MergeTask {

    private final CancelSLTPAndMergeTask cancelSLTPAndMergeTask;
    private final PositionUtil positionUtil;

    public MergeTask(final CancelSLTPAndMergeTask cancelSLTPAndMergeTask,
                     final PositionUtil positionUtil) {
        this.cancelSLTPAndMergeTask = cancelSLTPAndMergeTask;
        this.positionUtil = positionUtil;
    }

    public Observable<OrderEvent> merge(final Collection<IOrder> toMergeOrders,
                                        final MergeCommand command) {
        return observeSplit(() -> toMergeOrders, command);
    }

    public Observable<OrderEvent> mergePosition(final Instrument instrument,
                                                final MergeCommand command) {
        return observeSplit(() -> positionUtil.filledOrders(instrument), command);
    }

    private final Observable<OrderEvent> observeSplit(final Supplier<Collection<IOrder>> toMergeOrders,
                                                      final MergeCommand command) {
        return Observable.defer(() -> cancelSLTPAndMergeTask.observe(toMergeOrders.get(), command));
    }

    public Observable<OrderEvent> mergeAllPositions(final Function<Instrument, MergeCommand> commandFactory) {
        return Observable.defer(() -> {
            final Function<Instrument, Observable<OrderEvent>> observablesFromFactory =
                    instrument -> mergePosition(instrument, commandFactory.apply(instrument));
            return Observable.merge(positionUtil.observablesFromFactory(observablesFromFactory));
        });
    }
}
