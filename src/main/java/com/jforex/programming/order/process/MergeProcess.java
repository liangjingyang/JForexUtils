package com.jforex.programming.order.process;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;

import com.dukascopy.api.IOrder;
import com.jforex.programming.order.process.option.MergeOption;

public class MergeProcess extends CommonProcess {

    private final String mergeOrderLabel;
    private final Collection<IOrder> toMergeOrders;

    public interface Option extends MergeOption<Option> {

        public MergeProcess build();
    }

    private MergeProcess(final Builder builder) {
        super(builder);
        mergeOrderLabel = builder.mergeOrderLabel;
        toMergeOrders = builder.toMergeOrders;
    }

    public final String mergeOrderLabel() {
        return mergeOrderLabel;
    }

    public final Collection<IOrder> toMergeOrders() {
        return toMergeOrders;
    }

    public static final Option forParams(final String mergeOrderLabel,
                                         final Collection<IOrder> toMergeOrders) {
        return new Builder(checkNotNull(mergeOrderLabel), checkNotNull(toMergeOrders));
    }

    private static class Builder extends CommonBuilder<Option>
                                 implements Option {

        private final String mergeOrderLabel;
        private final Collection<IOrder> toMergeOrders;

        private Builder(final String mergeOrderLabel,
                        final Collection<IOrder> toMergeOrders) {
            this.mergeOrderLabel = mergeOrderLabel;
            this.toMergeOrders = toMergeOrders;
        }

        @Override
        public MergeProcess build() {
            return new MergeProcess(this);
        }
    }
}
