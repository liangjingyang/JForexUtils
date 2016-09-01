package com.jforex.programming.order.process;

import static com.google.common.base.Preconditions.checkNotNull;

import com.dukascopy.api.Instrument;
import com.jforex.programming.order.process.option.MergeOption;

public class MergePositionProcess extends CommonProcess {

    private final String mergeOrderLabel;
    private final Instrument instrument;

    public interface Option extends MergeOption<Option> {

        public MergePositionProcess build();
    }

    private MergePositionProcess(final Builder builder) {
        super(builder);
        mergeOrderLabel = builder.mergeOrderLabel;
        instrument = builder.instrument;
    }

    public final String mergeOrderLabel() {
        return mergeOrderLabel;
    }

    public final Instrument instrument() {
        return instrument;
    }

    public static final Option forParams(final String mergeOrderLabel,
                                         final Instrument instrument) {
        return new Builder(checkNotNull(mergeOrderLabel), checkNotNull(instrument));
    }

    private static class Builder extends CommonBuilder<Option>
                                 implements Option {

        private final String mergeOrderLabel;
        private final Instrument instrument;

        private Builder(final String mergeOrderLabel,
                        final Instrument instrument) {
            this.mergeOrderLabel = mergeOrderLabel;
            this.instrument = instrument;
        }

        @Override
        public MergePositionProcess build() {
            return new MergePositionProcess(this);
        }
    }
}
