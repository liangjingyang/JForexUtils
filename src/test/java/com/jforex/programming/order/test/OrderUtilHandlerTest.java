package com.jforex.programming.order.test;

import static com.jforex.programming.order.event.OrderEventType.CLOSE_OK;
import static com.jforex.programming.order.event.OrderEventType.CLOSE_REJECTED;
import static com.jforex.programming.order.event.OrderEventType.NOTIFICATION;
import static com.jforex.programming.order.event.OrderEventType.PARTIAL_CLOSE_OK;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.EnumSet;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;

import com.dukascopy.api.IOrder;
import com.dukascopy.api.JFException;
import com.jforex.programming.misc.TaskExecutor;
import com.jforex.programming.order.OrderStaticUtil;
import com.jforex.programming.order.OrderUtilHandler;
import com.jforex.programming.order.call.OrderCallReason;
import com.jforex.programming.order.call.OrderCallRejectException;
import com.jforex.programming.order.call.OrderCallRequest;
import com.jforex.programming.order.command.Command;
import com.jforex.programming.order.event.OrderEvent;
import com.jforex.programming.order.event.OrderEventGateway;
import com.jforex.programming.order.event.OrderEventType;
import com.jforex.programming.order.event.OrderEventTypeData;
import com.jforex.programming.test.common.InstrumentUtilForTest;
import com.jforex.programming.test.common.RxTestUtil;

import de.bechte.junit.runners.context.HierarchicalContextRunner;
import io.reactivex.Single;
import io.reactivex.functions.Action;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

@RunWith(HierarchicalContextRunner.class)
public class OrderUtilHandlerTest extends InstrumentUtilForTest {

    private OrderUtilHandler orderUtilHandler;

    @Mock
    private TaskExecutor taskExecutorMock;
    @Mock
    private OrderEventGateway orderEventGatewayMock;
    @Mock
    private Consumer<OrderEvent> orderEventConsumerMock;
    @Mock
    private Consumer<IOrder> closeActionMock;
    @Mock
    private Action subscribeActionMock;
    @Mock
    private Action completedActionMock;
    @Captor
    private ArgumentCaptor<Callable<IOrder>> orderCallCaptor;
    @Captor
    private ArgumentCaptor<OrderCallRequest> callRequestCaptor;
    private Command closeCommand;
    private Callable<IOrder> callable;
    private final IOrder orderToClose = buyOrderEURUSD;
    private final TestObserver<OrderEvent> subscriber = TestObserver.create();

    private final Subject<OrderEvent> orderEventSubject = PublishSubject.create();

    @Before
    public void setUp() {
        setUpMocks();

        orderUtilHandler = new OrderUtilHandler(taskExecutorMock, orderEventGatewayMock);
    }

    public void setUpMocks() {
        orderUtilForTest.setState(orderToClose, IOrder.State.FILLED);
        callable = OrderStaticUtil.runnableToCallable(() -> orderToClose.close(), orderToClose);

        when(orderEventGatewayMock.observable()).thenReturn(orderEventSubject);
    }

    private OrderEvent sendOrderEvent(final IOrder order,
                                      final OrderEventType orderEventType) {
        final OrderEvent orderEvent = new OrderEvent(order,
                                                     orderEventType,
                                                     true);
        orderEventSubject.onNext(orderEvent);
        return orderEvent;
    }

    public class CloseCallSetup {

        private Runnable closeCall;
        private static final long retryDelay = 1500L;
        OrderEventTypeData typeData = new OrderEventTypeData(EnumSet.of(CLOSE_OK),
                                                             EnumSet.of(CLOSE_REJECTED),
                                                             EnumSet.of(NOTIFICATION, PARTIAL_CLOSE_OK));

        @Before
        public void setUp() {

            closeCommand = Command
                .forClose(callable, typeData)
                .doOnOrderEvent(orderEventConsumerMock)
                .doOnClose(closeActionMock)
                .retry(2, retryDelay)
                .build();
            callable = closeCommand.callable();

            closeCall = () -> orderUtilHandler
                .callObservable(closeCommand)
                .doOnSubscribe(d -> subscribeActionMock.run())
                .doOnComplete(completedActionMock)
                .subscribe(subscriber);

            when(taskExecutorMock.onStrategyThread(callable))
                .thenReturn(Single.fromCallable(callable));
        }

        @Test
        public void whenRejectedCommandCompletesWithNoRetryForCommandWithNoRetry() {
            closeCommand = Command
                .forClose(callable, typeData)
                .build();
            callable = closeCommand.callable();

            when(taskExecutorMock.onStrategyThread(callable))
                .thenReturn(Single.fromCallable(callable));

            orderUtilHandler
                .callObservable(closeCommand)
                .subscribe(subscriber);

            sendOrderEvent(orderToClose, OrderEventType.CLOSE_REJECTED);

            subscriber.assertNoErrors();
            subscriber.assertComplete();
        }

        public class CallableExecutesWithJFException {

            @Before
            public void setUp() throws JFException {
                Mockito
                    .doThrow(jfException)
                    .when(orderToClose)
                    .close();

                closeCall.run();
            }

            @Test
            public void subscriberCompletesWithJFError() {
                subscriber.assertValueCount(0);
                subscriber.assertError(JFException.class);
            }

            @Test
            public void testOrderIsNotRegisteredAtGateway() {
                verify(orderEventGatewayMock, never())
                    .registerOrderCallRequest(any());
            }
        }

        public class ExecutesWithReject {

            private OrderEvent rejectEvent;

            @Before
            public void setUp() {
                closeCall.run();

                rejectEvent = sendOrderEvent(orderToClose, OrderEventType.CLOSE_REJECTED);
            }

            private void advanceRetryDelayTime() {
                RxTestUtil.advanceTimeBy(retryDelay, TimeUnit.MILLISECONDS);
            }

            @Test
            public void orderEventActionIsCalled() {
                verify(orderEventConsumerMock).accept(rejectEvent);
            }

            @Test
            public void callableIsInvokedAgain() throws Exception {
                advanceRetryDelayTime();

                verify(orderToClose, times(2)).close();
            }

            public class ExecutesWithRejectSecondTime {

                @Before
                public void setUp() {
                    advanceRetryDelayTime();

                    sendOrderEvent(orderToClose, OrderEventType.CLOSE_REJECTED);
                }

                @Test
                public void callableIsInvokedAgain() throws Exception {
                    advanceRetryDelayTime();

                    verify(orderToClose, times(3)).close();
                }

                @Test
                public void errorIsEmittedOnThirdReject() {
                    advanceRetryDelayTime();
                    sendOrderEvent(orderToClose, OrderEventType.CLOSE_REJECTED);

                    subscriber.assertValueCount(0);
                    subscriber.assertError(OrderCallRejectException.class);
                    verifyZeroInteractions(completedActionMock);
                }

                @Test
                public void subscriberCompletesOnDoneEvent() throws Exception {
                    advanceRetryDelayTime();
                    final OrderEvent orderEvent = sendOrderEvent(orderToClose, OrderEventType.CLOSE_OK);

                    subscriber.assertNoErrors();
                    subscriber.assertComplete();
                    verify(orderEventConsumerMock).accept(orderEvent);
                    verify(completedActionMock).run();
                    verify(subscribeActionMock).run();
                }
            }
        }

        public class ExecutesOK {

            @Before
            public void setUp() {
                closeCall.run();
            }

            @Test
            public void startActionMockIsInvoked() throws Exception {
                verify(subscribeActionMock).run();
            }

            @Test
            public void orderRegisteredAtGateway() {
                verify(orderEventGatewayMock).registerOrderCallRequest(callRequestCaptor.capture());

                final OrderCallRequest callRequest = callRequestCaptor.getValue();
                assertThat(callRequest.order(), equalTo(orderToClose));
                assertThat(callRequest.reason(), equalTo(OrderCallReason.CLOSE));
            }

            @Test
            public void closeCallIsExecutedOnSubscribe() throws Exception {
                verify(orderToClose).close();
            }

            @Test
            public void subscriberNotYetCompletedWhenNoEventWasSent() {
                subscriber.assertNotComplete();
            }

            @Test
            public void noNotificationIfUnsubscribedEarly() {
                subscriber.dispose();

                sendOrderEvent(orderToClose, OrderEventType.CLOSE_OK);

                subscriber.assertValueCount(0);
            }

            @Test
            public void onPartialCloseSubscriberIsNotCompleted() {
                final OrderEvent orderEvent = sendOrderEvent(orderToClose, OrderEventType.PARTIAL_CLOSE_OK);

                subscriber.assertValueCount(1);
                subscriber.assertNotComplete();
                verify(orderEventConsumerMock).accept(orderEvent);
            }

            @Test
            public void subscriberCompletesOnDoneEvent() throws Exception {
                final OrderEvent orderEvent = sendOrderEvent(orderToClose, OrderEventType.CLOSE_OK);

                subscriber.assertNoErrors();
                subscriber.assertComplete();
                verify(orderEventConsumerMock).accept(orderEvent);
                verify(completedActionMock).run();
            }

            @Test
            public void noMoreNotificationsAfterFinishEvent() {
                sendOrderEvent(orderToClose, OrderEventType.CLOSE_OK);
                subscriber.assertValueCount(1);

                sendOrderEvent(orderToClose, OrderEventType.CLOSE_OK);
                subscriber.assertValueCount(1);

                subscriber.assertNoErrors();
                subscriber.assertComplete();
            }

            @Test
            public void closeActionIsCalled() {
                sendOrderEvent(orderToClose, OrderEventType.CLOSE_OK);

                verify(closeActionMock).accept(buyOrderEURUSD);
            }

            @Test
            public void eventOfOtherOrderIsIgnored() {
                sendOrderEvent(orderUtilForTest.sellOrderAUDUSD(), OrderEventType.CLOSE_OK);

                subscriber.assertNotComplete();
            }

            @Test
            public void unknownOrderEventIsIgnored() {
                final OrderEvent orderEvent = sendOrderEvent(orderToClose, OrderEventType.CHANGED_GTT);

                subscriber.assertNotComplete();
                verify(orderEventConsumerMock, never()).accept(orderEvent);
            }
        }
    }
}
