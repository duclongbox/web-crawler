package com.crawler.queue;

import com.crawler.model.CrawlTask;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe priority URL frontier.
 * BFS ordering: tasks with lower depth dequeue first.
 */
public class UrlFrontier {

    private final PriorityBlockingQueue<CrawlTask> queue;
    private final int maxSize;
    private final AtomicInteger totalEnqueued = new AtomicInteger(0);

    public UrlFrontier(int maxSize) {
        this.maxSize = maxSize;
        this.queue = new PriorityBlockingQueue<>(Math.min(maxSize, 1000));
    }

    /**
     * Adds a task if the frontier is not full.
     * @return true if added, false if capacity exceeded
     */
    public boolean offer(CrawlTask task) {
        if (queue.size() >= maxSize) return false;
        boolean added = queue.offer(task);
        if (added) totalEnqueued.incrementAndGet();
        return added;
    }

    /** Blocks until a task is available or the timeout expires. */
    public CrawlTask poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public int size() { return queue.size(); }
    public boolean isEmpty() { return queue.isEmpty(); }
    public int getTotalEnqueued() { return totalEnqueued.get(); }
}
