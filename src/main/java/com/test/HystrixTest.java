package com.test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class HystrixTest {

    public static void main(String... args) throws Exception {
        CommandCreator commandCreator = new CommandCreator(50, 10);

//        wrapWithPrint(() -> doMultipleWithAllOf(commandCreator));
//        Thread.sleep(2000);
//        System.out.println("after main sleep");
//        Thread.sleep(2000);
//        System.out.println("Exiting after 2nd sleep");

        wrapWithPrint("doMultipleWithAllOf", () -> doMultipleWithAllOf(commandCreator));
        Thread.sleep(400);

        wrapWithPrint("doMultipleWithSemaphore", () -> doMultipleWithSemaphore(commandCreator));

        wrapWithPrint("doMultipleWithJoin", () -> doMultipleWithJoin(commandCreator));

        Thread.sleep(2000);

    }

    private static List<String> doMultipleWithSemaphore(CommandCreator commandCreator) {
        int highLevelTimeout = 300;
        List<CompletableFuture<String>> listOfFutures = IntStream.range(0, 100)
                .mapToObj(commandCreator::getCommand)
                .collect(toList());
        CompletableFuture<Void> allOfTheFutures = CompletableFuture.allOf(listOfFutures.toArray(new CompletableFuture[0]));

        Semaphore s = new Semaphore(1);
        unchecked(s::acquire);

        Executor timeoutPool = new ForkJoinPool(200);
        allOfTheFutures.whenCompleteAsync((nothing, exception) -> s.release(), timeoutPool);

        try {
            boolean timedOutWaiting = !s.tryAcquire(highLevelTimeout, TimeUnit.MILLISECONDS);
            if (timedOutWaiting) {
                cancelFutures(allOfTheFutures, listOfFutures);
            }
        } catch (InterruptedException e) {
            cancelFutures(allOfTheFutures, listOfFutures);
        }

        //now we are sure we can complete them
        return listOfFutures.stream().map(HystrixTest::complete).collect(toList());

    }

    private static <T> void cancelFutures(CompletableFuture<?> waitForEverythingFuture, Collection<CompletableFuture<T>> bidderFutures) {
        bidderFutures.forEach(f -> f.cancel(true));
        waitForEverythingFuture.cancel(true);
    }

    private static String complete(CompletableFuture<String> future) {
        try {
            return future.getNow("IT_FAILED");
        } catch (CancellationException e) {
            return "TIMEOUT";
        } catch (RuntimeException ex) {
            return "Other Exception";
        }

    }


    private static List<String> doMultipleWithAllOf(CommandCreator commandCreator) {
        List<CompletableFuture<String>> listOfFutures = IntStream.range(0, 100)
                .mapToObj(commandCreator::getCommand)
                .collect(toList());
        CompletableFuture<Void> voidCompletableFuture = CompletableFuture.allOf(listOfFutures.toArray(new CompletableFuture[0]));

        voidCompletableFuture.whenCompleteAsync((aVoid, throwable) -> {
            List<String> finishedFutures = listOfFutures.stream().
                    map(stringCompletableFuture -> stringCompletableFuture.getNow("NOT_FINISHED"))
                    .collect(toList());
            System.out.println("results are from doMultipleWithAllOf : " + finishedFutures);
        });

        return Collections.emptyList();
    }

    private static List<String> doMultipleWithJoin(CommandCreator commandCreator) {
        List<String> results = IntStream.range(0, 100).parallel()
                .mapToObj(commandCreator::getCommand)
                .map(CompletableFuture::join)
                .collect(toList());
        return results;
    }


    private static void doMultipleSequential(CommandCreator commandCreator) {
        IntStream.range(0, 100).forEach(number -> {
            String getNow = commandCreator.executeCommandSync(number);
            System.out.println("results are: " + getNow);
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
        System.out.println("results are: " + getNow);
    }


    private static void wrapWithPrint(String name, Callable<List<String>> runnable) throws Exception {
        System.out.println("Start main computation for " + name);
        long startTime = System.currentTimeMillis();
        List<String> allResults = runnable.call();
        long endTime = System.currentTimeMillis();
        System.out.println("The End!" + "It took " + (endTime - startTime) + "ms for " + name);
//        System.out.println("Results are : " + allResults);
        List<String> justFailures = allResults.stream().filter(s -> !s.contains("Success")).collect(Collectors.toList());
        System.out.println("Failures are : " + justFailures);

    }

    public static <T, E extends Exception> void unchecked(ExceptionalVoid<T, E> supplier) {
        try {
            supplier.get();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public interface ExceptionalVoid<T, E extends Exception> {
        void get() throws E;
    }


    // awaitQuiescence
//    public static void doThatThing(){
//        ForkJoinPool.commonPool().awaitQuiescence(1, TimeUnit.SECONDS);
//    }
}
