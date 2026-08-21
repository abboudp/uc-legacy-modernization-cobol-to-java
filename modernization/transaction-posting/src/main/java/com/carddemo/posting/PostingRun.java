package com.carddemo.posting;

import java.util.List;

/**
 * The {@code PROCEDURE DIVISION} driver of {@code CBTRN02C}: read the daily transaction file to end of file,
 * validate and post or reject each record, and report the counts the job step ends with.
 */
public final class PostingRun {

    private final PostingEngine engine;

    public PostingRun(PostingEngine engine) {
        this.engine = engine;
    }

    /**
     * @param processed {@code WS-TRANSACTION-COUNT}
     * @param rejected  {@code WS-REJECT-COUNT}
     */
    public record Summary(long processed, long rejected) {

        /** {@code RETURN-CODE} is set to 4 when anything was rejected, otherwise it stays 0. */
        public int returnCode() {
            return rejected > 0 ? 4 : 0;
        }
    }

    public Summary process(List<String> dailyTransactionRecords) {
        long processed = 0;
        long rejected = 0;
        for (String record : dailyTransactionRecords) {
            processed++;
            if (engine.post(DailyTransaction.decode(record)) instanceof PostingEngine.Outcome.Rejected) {
                rejected++;
            }
        }
        return new Summary(processed, rejected);
    }
}
