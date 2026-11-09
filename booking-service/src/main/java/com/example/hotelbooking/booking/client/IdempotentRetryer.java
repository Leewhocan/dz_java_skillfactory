package com.example.hotelbooking.booking.client;

import feign.Request.HttpMethod;
import feign.RetryableException;
import feign.Retryer;

public class IdempotentRetryer implements Retryer {

    private static final int MAX_BACKOFF_SHIFT = 5;

    private final long periodMillis;
    private final long maxPeriodMillis;
    private final int maxAttempts;

    private int attempt;

    public IdempotentRetryer(long periodMillis, long maxPeriodMillis, int maxAttempts) {
        this.periodMillis = periodMillis;
        this.maxPeriodMillis = maxPeriodMillis;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void continueOrPropagate(RetryableException e) {
        if (!isIdempotent(e.method()) || ++attempt >= maxAttempts) {
            throw e;
        }
        try {
            Thread.sleep(nextInterval());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    @Override
    public Retryer clone() {
        return new IdempotentRetryer(periodMillis, maxPeriodMillis, maxAttempts);
    }

    private static boolean isIdempotent(HttpMethod method) {
        return HttpMethod.GET.equals(method)
                || HttpMethod.HEAD.equals(method)
                || HttpMethod.OPTIONS.equals(method);
    }

    private long nextInterval() {
        return Math.min(maxPeriodMillis, periodMillis * (1L << Math.min(attempt, MAX_BACKOFF_SHIFT)));
    }
}
