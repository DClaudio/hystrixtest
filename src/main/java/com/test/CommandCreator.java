package com.test;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.exception.HystrixTimeoutException;
import rx.Observable;

import java.util.concurrent.CompletableFuture;

import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey;
import static com.netflix.hystrix.HystrixCommandProperties.Setter;

public class CommandCreator {
    private final int timeout;
    private final int sleepTime;

    public CommandCreator(int timeout, int sleepTime) {
        this.timeout = timeout;
        this.sleepTime = sleepTime;
    }

    public CompletableFuture<String> getCommand(int number){
        StubCommand stubCommand = new StubCommand(this.timeout, number, this.sleepTime);
        Observable<String> observe = stubCommand.observe();
        return fromSingleObservable(observe);

    }

    public String executeCommandSync(int number){
        StubCommand stubCommand = new StubCommand(this.timeout, number, this.sleepTime);
        return stubCommand.execute();
    }


    private static <T> CompletableFuture<T> fromSingleObservable(Observable<T> observable) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        observable
                .doOnError(future::completeExceptionally)
                .single()
                .forEach(future::complete);
        return future;
    }


    private class StubCommand extends HystrixCommand<String> {

        private final int number;
        private final int waitTime;

        StubCommand(int timeout, int number, int sleepTimer) {
            super(
                    Setter
                            .withGroupKey(asKey("hystrix.groupKey"))
                            .andCommandKey(HystrixCommandKey.Factory.asKey("command"))
                            .andCommandPropertiesDefaults(Setter()
                                    .withExecutionTimeoutInMilliseconds(timeout)
                                    .withCircuitBreakerErrorThresholdPercentage(95)
                                    .withCircuitBreakerRequestVolumeThreshold(100)
                                    .withCircuitBreakerSleepWindowInMilliseconds(5_000)
                            )
                            .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter()
                                    .withAllowMaximumSizeToDivergeFromCoreSize(true)
                                    .withMaximumSize(4)
                            )
            );
            this.number = number;
            this.waitTime = sleepTimer;
        }

        @Override
        protected String run() throws InterruptedException {
            Thread.sleep(this.waitTime);
            return "Success from " + this.number;
        }

        @Override
        public String getFallback() {

            if (this.executionResult != null) {
                if (this.executionResult.getExecutionException() instanceof HystrixTimeoutException) {
                    return "Fallback: it was a timeout";
                } else if (this.executionResult.getExecutionException() != null) {
                    return "Fallback point 1." + this.executionResult.getExecutionException().getMessage();
                } else if (this.executionResult.getException() != null) {
                    return "Fallback point 2." + this.executionResult.getException().getMessage();
                }
            }
            return "Fallback point 3. Hystrix fallback was called but we are not sure why...";
        }
    }

}
