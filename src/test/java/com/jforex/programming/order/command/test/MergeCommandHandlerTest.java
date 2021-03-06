package com.jforex.programming.order.command.test;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import com.dukascopy.api.IOrder;
import com.google.common.collect.Sets;
import com.jforex.programming.order.command.MergeCommand;
import com.jforex.programming.order.command.MergeCommandHandler;
import com.jforex.programming.order.event.OrderEvent;
import com.jforex.programming.order.event.OrderEventTransformer;
import com.jforex.programming.order.task.BasicTask;
import com.jforex.programming.order.task.CancelSLTPTask;
import com.jforex.programming.test.common.InstrumentUtilForTest;

import de.bechte.junit.runners.context.HierarchicalContextRunner;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;

@RunWith(HierarchicalContextRunner.class)
public class MergeCommandHandlerTest extends InstrumentUtilForTest {

    private MergeCommandHandler commandHandler;

    @Mock
    private CancelSLTPTask orderCancelSLAndTPMock;
    @Mock
    private BasicTask orderBasicTaskMock;
    @Mock
    private MergeCommand mergeCommandMock;
    private TestObserver<OrderEvent> testObserver;
    private final Set<IOrder> toMergeOrders = Sets.newHashSet(buyOrderEURUSD, sellOrderEURUSD);
    private final String mergeOrderLabel = "mergeOrderLabel";
    private final OrderEvent testEvent = closeEvent;
    private final OrderEvent composerEvent = changedLabelEvent;
    private final OrderEventTransformer testComposer =
            upstream -> upstream.flatMap(orderEvent -> Observable.just(composerEvent));

    @Before
    public void setUp() {
        setUpMocks();

        commandHandler = new MergeCommandHandler(orderCancelSLAndTPMock, orderBasicTaskMock);
    }

    private void setUpMocks() {
        when(mergeCommandMock.mergeOrderLabel()).thenReturn(mergeOrderLabel);
        when(mergeCommandMock.mergeComposer()).thenReturn(testComposer);

        when(orderCancelSLAndTPMock.observe(toMergeOrders, mergeCommandMock))
            .thenReturn(eventObservable(testEvent));
    }

    @Test
    public void observeCancelSLTPDelegatesToCancelSLTPMock() {
        testObserver = commandHandler
            .observeCancelSLTP(toMergeOrders, mergeCommandMock)
            .test();

        testObserver.assertComplete();
        testObserver.assertValue(testEvent);
    }

    public class ObserveMerge {

        @Before
        public void setUp() {
            when(orderBasicTaskMock.mergeOrders(mergeOrderLabel, toMergeOrders))
                .thenReturn(eventObservable(testEvent));

            testObserver = commandHandler
                .observeMerge(toMergeOrders, mergeCommandMock)
                .test();
        }

        @Test
        public void observeMergeCallsBasicTaskMockCorrect() {
            verify(orderBasicTaskMock).mergeOrders(mergeOrderLabel, toMergeOrders);
        }

        @Test
        public void returnedObservableIsCorrectComposed() {
            testObserver.assertComplete();
            testObserver.assertValue(composerEvent);
        }
    }
}
