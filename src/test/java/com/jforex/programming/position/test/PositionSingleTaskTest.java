package com.jforex.programming.position.test;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import com.jforex.programming.order.OrderUtilHandler;
import com.jforex.programming.order.command.OrderChangeCommand;
import com.jforex.programming.order.event.OrderEvent;
import com.jforex.programming.order.event.OrderEventType;
import com.jforex.programming.position.PositionSingleTask;
import com.jforex.programming.test.common.PositionCommonTest;
import com.jforex.programming.test.fakes.IOrderForTest;

import com.dukascopy.api.IOrder;

import de.bechte.junit.runners.context.HierarchicalContextRunner;
import rx.Observable;
import rx.observers.TestSubscriber;

@RunWith(HierarchicalContextRunner.class)
public class PositionSingleTaskTest extends PositionCommonTest {

    private PositionSingleTask positionSingleTask;

    @Mock
    private OrderUtilHandler orderUtilHandlerMock;
    @Captor
    private ArgumentCaptor<Supplier<Observable<OrderEvent>>> orderCallCaptor;
    private final IOrderForTest orderUnderTest = IOrderForTest.buyOrderEURUSD();
    private final TestSubscriber<OrderEvent> taskSubscriber = new TestSubscriber<>();

    private void assertOrderEventNotification(final OrderEvent expectedEvent) {
        taskSubscriber.assertValueCount(1);

        final OrderEvent orderEvent = taskSubscriber.getOnNextEvents().get(0);

        assertThat(orderEvent.order(), equalTo(expectedEvent.order()));
        assertThat(orderEvent.type(), equalTo(expectedEvent.type()));
    }

    @Before
    public void setUp() {
        initCommonTestFramework();

        positionSingleTask = new PositionSingleTask(orderUtilHandlerMock);
    }

    public class SetSLSetup {

        @Before
        public void setUp() {
            orderUnderTest.setState(IOrder.State.FILLED);
        }

        public class SLIsAlreadySet {

            private final double orderSL = orderUnderTest.getStopLossPrice();

            @Before
            public void setUp() {
                positionSingleTask.setSLObservable(orderUnderTest, orderSL)
                        .subscribe(taskSubscriber);
            }

            @Test
            public void testNoCallToChangeUtil() {
                verify(orderUtilHandlerMock, never()).observable(any());
            }

            @Test
            public void testSubscriberCompleted() {
                taskSubscriber.assertCompleted();
            }
        }

        public class SLIsNew {

            private final OrderEvent changedSLEvent =
                    new OrderEvent(orderUnderTest, OrderEventType.CHANGED_SL);
            private final OrderEvent rejectEvent =
                    new OrderEvent(orderUnderTest, OrderEventType.CHANGE_SL_REJECTED);
            private static final double newSL = 1.10123;
            private final Runnable setSLObservableCall =
                    () -> positionSingleTask.setSLObservable(orderUnderTest, newSL).subscribe(taskSubscriber);

            private void setSLChangeMockResult(final Observable<OrderEvent> observable) {
                when(orderUtilHandlerMock.observable(any()))
                        .thenReturn(observable);
            }

            @Test
            public void testSubscriberNotYetCompletedWhenChangeUtilIsBusy() {
                setSLChangeMockResult(busyObservable());

                setSLObservableCall.run();

                taskSubscriber.assertNotCompleted();
            }

            public class SetSLWithJFException {

                @Before
                public void setUp() {
                    setSLChangeMockResult(exceptionObservable());

                    setSLObservableCall.run();
                }

                @Test
                public void testSetSLOnChangeUtilHasBeenCalledWithoutRetry() {
                    verify(orderUtilHandlerMock).observable(any(OrderChangeCommand.class));
                }

                @Test
                public void testSubscriberGetsJFExceptionNotification() {
                    assertJFException(taskSubscriber);
                }
            }

            public class SetSLCallWhichExceedsRetries {

                @Before
                public void setUp() {
                    setRetryExceededMock(() -> orderUtilHandlerMock.observable(any(OrderChangeCommand.class)),
                                         rejectEvent);

                    setSLObservableCall.run();

                    rxTestUtil.advanceTimeForAllOrderRetries();
                }

                @Test
                public void testSetSLCalledWithAllRetries() {
                    verify(orderUtilHandlerMock, times(retryExceedCount))
                            .observable(any(OrderChangeCommand.class));
                }

                @Test
                public void testSubscriberGetsRejectExceptionNotification() {
                    assertRejectException(taskSubscriber);
                }
            }

            public class SetSLCallWithFullRetriesThenSuccess {

                @Before
                public void setUp() {
                    setFullRetryMock(() -> orderUtilHandlerMock.observable(any(OrderChangeCommand.class)),
                                     rejectEvent);

                    setSLObservableCall.run();

                    rxTestUtil.advanceTimeForAllOrderRetries();
                }

                @Test
                public void testSetSLCalledWithAllRetries() {
                    verify(orderUtilHandlerMock, times(retryExceedCount))
                            .observable(any(OrderChangeCommand.class));
                }

                @Test
                public void testSubscriberCompleted() {
                    taskSubscriber.assertCompleted();
                }
            }

            public class SetSLOKCall {

                @Before
                public void setUp() {
                    setSLChangeMockResult(doneEventObservable(changedSLEvent));

                    setSLObservableCall.run();
                }

                @Test
                public void testSetSLOnChangeUtilHasBeenCalledCorrect() {
                    verify(orderUtilHandlerMock).observable(any(OrderChangeCommand.class));
                }

                @Test
                public void testSubscriberHasBeenNotifiedWithOrderEvent() {
                    assertOrderEventNotification(changedSLEvent);
                }

                @Test
                public void testSubscriberCompleted() {
                    taskSubscriber.assertCompleted();
                }
            }
        }
    }

    public class SetTPSetup {

        @Before
        public void setUp() {
            orderUnderTest.setState(IOrder.State.FILLED);
        }

        public class TPIsAlreadySet {

            private final double orderTP = orderUnderTest.getTakeProfitPrice();

            @Before
            public void setUp() {
                positionSingleTask.setTPObservable(orderUnderTest, orderTP)
                        .subscribe(taskSubscriber);
            }

            @Test
            public void testNoCallToChangeUtil() {
                verify(orderUtilHandlerMock, never()).observable(any());
            }

            @Test
            public void testSubscriberCompleted() {
                taskSubscriber.assertCompleted();
            }
        }

        public class TPIsNew {

            private final OrderEvent changedTPEvent =
                    new OrderEvent(orderUnderTest, OrderEventType.CHANGED_TP);
            private final OrderEvent rejectEvent =
                    new OrderEvent(orderUnderTest, OrderEventType.CHANGE_TP_REJECTED);
            private static final double newTP = 1.12123;
            private final Runnable setTPObservableCall =
                    () -> positionSingleTask.setTPObservable(orderUnderTest, newTP).subscribe(taskSubscriber);

            private void setTPChangeMockResult(final Observable<OrderEvent> observable) {
                when(orderUtilHandlerMock.observable(any(OrderChangeCommand.class)))
                        .thenReturn(observable);
            }

            @Test
            public void testSubscriberNotYetCompletedWhenChangeUtilIsBusy() {
                setTPChangeMockResult(busyObservable());

                setTPObservableCall.run();

                taskSubscriber.assertNotCompleted();
            }

            public class SetTPWithJFException {

                @Before
                public void setUp() {
                    setTPChangeMockResult(exceptionObservable());

                    setTPObservableCall.run();
                }

                @Test
                public void testSetTPOnChangeUtilHasBeenCalledWithoutRetry() {
                    verify(orderUtilHandlerMock).observable(any(OrderChangeCommand.class));
                }

                @Test
                public void testSubscriberGetsJFExceptionNotification() {
                    assertJFException(taskSubscriber);
                }
            }

            public class SetTPCallWhichExceedsRetries {

                @Before
                public void setUp() {
                    setRetryExceededMock(() -> orderUtilHandlerMock.observable(any(OrderChangeCommand.class)),
                                         rejectEvent);

                    setTPObservableCall.run();

                    rxTestUtil.advanceTimeForAllOrderRetries();
                }

                @Test
                public void testSetSLCalledWithAllRetries() {
                    verify(orderUtilHandlerMock, times(retryExceedCount))
                            .observable(any(OrderChangeCommand.class));
                }

                @Test
                public void testSubscriberGetsRejectExceptionNotification() {
                    assertRejectException(taskSubscriber);
                }
            }

            public class SetTPCallWithFullRetriesThenSuccess {

                @Before
                public void setUp() {
                    setFullRetryMock(() -> orderUtilHandlerMock.observable(any(OrderChangeCommand.class)),
                                     rejectEvent);

                    setTPObservableCall.run();

                    rxTestUtil.advanceTimeForAllOrderRetries();
                }

                @Test
                public void testSetTPCalledWithAllRetries() {
                    verify(orderUtilHandlerMock, times(retryExceedCount))
                            .observable(any(OrderChangeCommand.class));
                }

                @Test
                public void testSubscriberCompleted() {
                    taskSubscriber.assertCompleted();
                }
            }

            public class SetTPOKCall {

                @Before
                public void setUp() {
                    setTPChangeMockResult(doneEventObservable(changedTPEvent));

                    setTPObservableCall.run();
                }

                @Test
                public void testSetTPOnChangeUtilHasBeenCalledCorrect() {
                    verify(orderUtilHandlerMock).observable(any(OrderChangeCommand.class));
                }

                @Test
                public void testSubscriberHasBeenNotifiedWithOrderEvent() {
                    assertOrderEventNotification(changedTPEvent);
                }

                @Test
                public void testSubscriberCompleted() {
                    taskSubscriber.assertCompleted();
                }
            }
        }
    }

    public class CloseSetup {

        public class OrderIsAlreadyClosed {

            @Before
            public void setUp() {
                orderUnderTest.setState(IOrder.State.CLOSED);

                positionSingleTask.closeObservable(orderUnderTest)
                        .subscribe(taskSubscriber);
            }

            @Test
            public void testNoCallToChangeUtil() {
                verify(orderUtilHandlerMock, never()).observable(any());
            }

            @Test
            public void testSubscriberCompleted() {
                taskSubscriber.assertCompleted();
            }
        }

        public class OrderIsFilled {

            private final OrderEvent closeOKEvent =
                    new OrderEvent(orderUnderTest, OrderEventType.CLOSE_OK);
            private final OrderEvent rejectEvent =
                    new OrderEvent(orderUnderTest, OrderEventType.CLOSE_REJECTED);
            private final Runnable closeCall =
                    () -> positionSingleTask.closeObservable(orderUnderTest).subscribe(taskSubscriber);

            private void setCloseMockResult(final Observable<OrderEvent> observable) {
                when(orderUtilHandlerMock.observable(any(OrderChangeCommand.class)))
                        .thenReturn(observable);
            }

            @Before
            public void setUp() {
                orderUnderTest.setState(IOrder.State.FILLED);
            }

            @Test
            public void testSubscriberNotYetCompletedWhenChangeUtilIsBusy() {
                setCloseMockResult(busyObservable());

                closeCall.run();

                taskSubscriber.assertNotCompleted();
            }

            public class CloseWithJFException {

                @Before
                public void setUp() {
                    setCloseMockResult(exceptionObservable());

                    closeCall.run();
                }

                @Test
                public void testCloseOnChangeUtilHasBeenCalledWithoutRetry() {
                    verify(orderUtilHandlerMock).observable(any(OrderChangeCommand.class));
                }

                @Test
                public void testSubscriberGetsJFExceptionNotification() {
                    assertJFException(taskSubscriber);
                }
            }

            public class CloseCallWhichExceedsRetries {

                @Before
                public void setUp() {
                    setRetryExceededMock(() -> orderUtilHandlerMock.observable(any(OrderChangeCommand.class)),
                                         rejectEvent);

                    closeCall.run();

                    rxTestUtil.advanceTimeForAllOrderRetries();
                }

                @Test
                public void testCloseCalledWithAllRetries() {
                    verify(orderUtilHandlerMock, times(retryExceedCount))
                            .observable(any(OrderChangeCommand.class));
                }

                @Test
                public void testSubscriberGetsRejectExceptionNotification() {
                    assertRejectException(taskSubscriber);
                }
            }

            public class CloseCallWithFullRetriesThenSuccess {

                @Before
                public void setUp() {
                    setFullRetryMock(() -> orderUtilHandlerMock.observable(any(OrderChangeCommand.class)),
                                     rejectEvent);

                    closeCall.run();

                    rxTestUtil.advanceTimeForAllOrderRetries();
                }

                @Test
                public void testCloseCalledWithAllRetries() {
                    verify(orderUtilHandlerMock, times(retryExceedCount))
                            .observable(any(OrderChangeCommand.class));
                }

                @Test
                public void testSubscriberCompleted() {
                    taskSubscriber.assertCompleted();
                }
            }

            public class CloseOKCall {

                @Before
                public void setUp() {
                    setCloseMockResult(doneEventObservable(closeOKEvent));

                    closeCall.run();
                }

                @Test
                public void testCloseOnChangeUtilHasBeenCalledCorrect() {
                    verify(orderUtilHandlerMock).observable(any(OrderChangeCommand.class));
                }

                @Test
                public void testSubscriberHasBeenNotifiedWithOrderEvent() {
                    assertOrderEventNotification(closeOKEvent);
                }

                @Test
                public void testSubscriberCompleted() {
                    taskSubscriber.assertCompleted();
                }
            }
        }
    }
}
