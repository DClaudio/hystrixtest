package com.test;

import java.sql.SQLOutput;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class HystrixTest {

    public static void main(String... args) throws InterruptedException {
        CommandCreator commandCreator = new CommandCreator(50, 10);

//        wrapWithPrint(() -> doMultipleWithAllOf(commandCreator));
//        Thread.sleep(2000);
//        System.out.println("after main sleep");
//        Thread.sleep(2000);
//        System.out.println("Exiting after 2nd sleep");

        wrapWithPrint(() -> doMultipleWithSemaphore(commandCreator));


    }

    private static void doMultipleWithSemaphore(CommandCreator commandCreator) {
        int highLevelTimeout = 300;
        List<CompletableFuture<String>> listOfFutures = IntStream.range(0, 100)
                .mapToObj(commandCreator::getCommand)
                .collect(toList());
        CompletableFuture<Void> allOfTheFutures = CompletableFuture.allOf(listOfFutures.toArray(new CompletableFuture[0]));

        Semaphore s = new Semaphore(1);
        unchecked(() -> s.acquire());

        Executor timeoutPool = new ForkJoinPool(1000);
        allOfTheFutures.whenCompleteAsync((nothing, exception) -> s.release(), timeoutPool);

        try {
            boolean timedOutWaiting = !s.tryAcquire(highLevelTimeout, TimeUnit.MILLISECONDS);
            if (timedOutWaiting) {
                cancelFutures(allOfTheFutures, listOfFutures);
            }
        } catch (InterruptedException e) {
            cancelFutures(allOfTheFutures, listOfFutures);
        }

    }
    private static <T> void cancelFutures(CompletableFuture<?> waitForEverythingFuture, Collection<CompletableFuture<T>> bidderFutures) {
        bidderFutures.forEach(f -> f.cancel(true));
        waitForEverythingFuture.cancel(true);
    }

    private static void doMultipleWithAllOf(CommandCreator commandCreator) {
        List<CompletableFuture<String>> listOfFutures = IntStream.range(0, 100)
                .mapToObj(commandCreator::getCommand)
                .collect(toList());
        CompletableFuture<Void> voidCompletableFuture = CompletableFuture.allOf(listOfFutures.toArray(new CompletableFuture[0]));

        voidCompletableFuture.whenCompleteAsync((aVoid, throwable) -> {
            List<String> finishedFutures = listOfFutures.stream().
                    map(stringCompletableFuture -> stringCompletableFuture.getNow("NOT_FINISHED"))
                    .collect(toList());
            System.out.println(finishedFutures);
        });

    }

    private static void doMultipleWithJoin(CommandCreator commandCreator) {
        List<String> results = IntStream.range(0, 100)
                .mapToObj(commandCreator::getCommand)
                .map(CompletableFuture::join)
                .collect(toList());
        System.out.println("results are: " + results);
    }


    private static void doMultipleSequential(CommandCreator commandCreator) {
        IntStream.range(0, 100).forEach(number -> {
            String getNow = commandCreator.executeCommandSync(number);
            System.out.println("Future value: " + getNow);
        });
    }

    private static void doOne(CommandCreator commandCreator) {
        String getNow;
        try {
            CompletableFuture<String> command = commandCreator.getCommand(1);
            getNow = command.get();
        } catch (InterruptedException e) {
            getNow = "EXCEPTION InterruptedException";
            e.printStackTrace();
        } catch (ExecutionException e) {
            getNow = "EXCEPTION ExecutionException";
            e.printStackTrace();
        }
        System.out.println("Future value: " + getNow);
    }


    private static void wrapWithPrint(Runnable runnable) {
        System.out.println("Start main computation");
        long startTime = System.currentTimeMillis();
        runnable.run();
        long endTime = System.currentTimeMillis();
        System.out.println("The End!" + "It took " + (endTime - startTime) + "ms");

    }

    public static <T,E extends Exception> void unchecked(ExceptionalVoid<T,E> supplier) {
        try {
            supplier.get();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public interface ExceptionalVoid<T,E extends Exception> {
        void get() throws E;
    }
}
