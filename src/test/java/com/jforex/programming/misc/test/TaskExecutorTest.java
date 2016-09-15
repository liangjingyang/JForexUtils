package com.jforex.programming.misc.test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import com.dukascopy.api.IOrder;
import com.dukascopy.api.JFException;
import com.jforex.programming.misc.TaskExecutor;
import com.jforex.programming.test.common.CommonUtilForTest;

import de.bechte.junit.runners.context.HierarchicalContextRunner;
import io.reactivex.subscribers.TestSubscriber;

@RunWith(HierarchicalContextRunner.class)
public class TaskExecutorTest extends CommonUtilForTest {

    private TaskExecutor taskExecutor;

    @Mock
    private Callable<IOrder> orderCallMock;
    @Mock
    private Future<IOrder> futureMock;
    private TestSubscriber<IOrder> orderSubscriber = TestSubscriber.create();
    private final Runnable onStrategyThreadCall = () -> orderSubscriber = taskExecutor
        .onStrategyThread(orderCallMock)
        .test();
    private final Runnable onCurrentThreadCall = () -> orderSubscriber = taskExecutor
        .onCurrentThread(orderCallMock)
        .test();

    @Before
    public void setUp() throws Exception {
        setUpMocks();

        taskExecutor = new TaskExecutor(contextMock);
    }

    private void setUpMocks() throws Exception {
        when(orderCallMock.call()).thenReturn(buyOrderEURUSD);

        when(futureMock.get()).thenReturn(buyOrderEURUSD);

        when(contextMock.executeTask(orderCallMock)).thenReturn(futureMock);
    }

    private void verifyNoExecutions() {
        verifyZeroInteractions(orderCallMock);
        verifyZeroInteractions(contextMock);
        verifyZeroInteractions(futureMock);
    }

    private void assertOrderEmissionAndCompletion() {
        orderSubscriber.assertComplete();
        orderSubscriber.assertNoErrors();
        orderSubscriber.assertValue(buyOrderEURUSD);
    }

    @Test
    public void whenNotSubscribedNoExecutionHappens() {
        taskExecutor.onStrategyThread(orderCallMock);
        taskExecutor.onCurrentThread(orderCallMock);

        verifyNoExecutions();
    }

    public class WhenStrategyThread {

        @Before
        public void setUp() {
            CommonUtilForTest.setStrategyThread();
        }

        public class WhenOnStrategyThreadSubscribe {

            @Before
            public void setUp() {
                onStrategyThreadCall.run();
            }

            @Test
            public void noExecutionWithContextHappensSinceAlreadyOnStrategyThread() throws InterruptedException,
                                                                                    ExecutionException {
                verify(contextMock, never()).executeTask(orderCallMock);
                verify(futureMock, never()).get();
            }

            @Test
            public void correctOrderIsEmitted() {
                assertOrderEmissionAndCompletion();
            }
        }

        public class WhenOnCurrentThreadSubscribe {

            @Before
            public void setUp() {
                onCurrentThreadCall.run();
            }

            @Test
            public void noExecutionWithContextHappens() throws InterruptedException,
                                                        ExecutionException {
                verify(contextMock, never()).executeTask(orderCallMock);
                verify(futureMock, never()).get();
            }

            @Test
            public void correctOrderIsEmitted() {
                assertOrderEmissionAndCompletion();
            }
        }
    }

    public class WhenNonStrategyThread {

        @Before
        public void setUp() {
            CommonUtilForTest.setNotStrategyThread();
        }

        public class WhenOnStrategyThreadCall {

            @Test
            public void onErrorExceptionIsEmitted() throws Exception {
                when(contextMock.executeTask(orderCallMock)).thenThrow(new RuntimeException());

                onStrategyThreadCall.run();

                orderSubscriber.assertError(RuntimeException.class);
            }

            public class WhenOnStrategyThreadSubscribe {

                @Before
                public void setUp() {
                    onStrategyThreadCall.run();
                }

                @Test
                public void executionWithContextHappens() throws InterruptedException,
                                                          ExecutionException {
                    verify(contextMock).executeTask(orderCallMock);
                    verify(futureMock).get();
                }

                @Test
                public void correctOrderIsEmitted() {
                    assertOrderEmissionAndCompletion();
                }
            }
        }

        public class WhenCurrentThreadThreadCall {

            @Test
            public void onErrorExceptionIsEmitted() throws Exception {
                when(orderCallMock.call()).thenThrow(jfException);

                onCurrentThreadCall.run();

                orderSubscriber.assertError(JFException.class);
            }

            public class WhenOnCurrentThreadSubscribe {

                @Before
                public void setUp() {
                    onCurrentThreadCall.run();
                }

                @Test
                public void noExecutionWithContextHappens() throws InterruptedException,
                                                            ExecutionException {
                    verify(contextMock, never()).executeTask(orderCallMock);
                    verify(futureMock, never()).get();
                }

                @Test
                public void correctOrderIsEmitted() {
                    assertOrderEmissionAndCompletion();
                }
            }
        }
    }
}
