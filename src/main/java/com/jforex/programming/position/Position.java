package com.jforex.programming.position;

import static com.jforex.programming.order.OrderStaticUtil.isCanceled;
import static com.jforex.programming.order.OrderStaticUtil.isClosed;
import static com.jforex.programming.order.OrderStaticUtil.isFilled;
import static com.jforex.programming.order.OrderStaticUtil.isOpened;
import static com.jforex.programming.order.OrderStaticUtil.ofInstrument;
import static java.util.stream.Collectors.toSet;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapMaker;
import com.jforex.programming.order.OrderDirection;
import com.jforex.programming.order.OrderStaticUtil;
import com.jforex.programming.order.event.OrderEvent;

import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;

import rx.Observable;

public class Position implements PositionChange, PositionOrders {

    private final Instrument instrument;
    private final ConcurrentMap<IOrder, OrderProcessState> orderRepository =
            new MapMaker().weakKeys().makeMap();

    private enum OrderProcessState {
        IDLE, ACTIVE
    }

    private static final Logger logger = LogManager.getLogger(Position.class);

    public Position(final Instrument instrument,
                    final Observable<OrderEvent> orderEventObservable) {
        this.instrument = instrument;

        orderEventObservable
                .map(OrderEvent::order)
                .filter(this::contains)
                .filter(isClosed.or(isCanceled)::test)
                .doOnNext(this::removeOrder)
                .subscribe();
    }

    @Override
    public synchronized void addOrder(final IOrder order) {
        if (!ofInstrument(instrument).test(order))
            logger.error("Tried to add instrument " + order.getInstrument() + " from order " +
                    order.getLabel() + " to position " + instrument + ". Will be ignored!");
        else {
            orderRepository.put(order, OrderProcessState.IDLE);
            logger.debug("Added order " + order.getLabel() + " to position " + instrument + " Orderstate: "
                    + order.getState() + " repo size " + orderRepository.size());
        }
    }

    @Override
    public synchronized void markAllOrdersActive() {
        orderRepository.replaceAll((k, v) -> OrderProcessState.ACTIVE);
    }

    private synchronized void removeOrder(final IOrder order) {
        orderRepository.remove(order);
        logger.debug("Removed order " + order.getLabel() + " from position " + instrument + " Orderstate: "
                + order.getState() + " repo size " + orderRepository.size());
    }

    @Override
    public Instrument instrument() {
        return instrument;
    }

    @Override
    public boolean contains(final IOrder order) {
        return orderRepository.containsKey(order);
    }

    @Override
    public int size() {
        return orderRepository.size();
    }

    @Override
    public OrderDirection direction() {
        return OrderStaticUtil.combinedDirection(filterOrders(isFilled));
    }

    @Override
    public double signedExposure() {
        return filterOrders(isFilled)
                .stream()
                .mapToDouble(OrderStaticUtil::signedAmount)
                .sum();
    }

    @Override
    public Set<IOrder> orders() {
        return ImmutableSet.copyOf(orderRepository.keySet());
    }

    @Override
    public Set<IOrder> filterOrders(final Predicate<IOrder> orderPredicate) {
        return orderRepository
                .keySet()
                .stream()
                .filter(orderPredicate)
                .collect(toSet());
    }

    @Override
    public Set<IOrder> notProcessingOrders(final Predicate<IOrder> orderPredicate) {
        return orderRepository
                .entrySet()
                .stream()
                .filter(entry -> orderPredicate.test(entry.getKey()))
                .filter(entry -> entry.getValue() == OrderProcessState.IDLE)
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()))
                .keySet();
    }

    @Override
    public Set<IOrder> filledOrders() {
        return notProcessingOrders(isFilled);
    }

    @Override
    public Set<IOrder> filledOrOpenedOrders() {
        return notProcessingOrders(isFilled.or(isOpened));
    }
}