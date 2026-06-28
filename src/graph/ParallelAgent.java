package graph;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ParallelAgent implements Agent {
    private static final int QUEUE_CAPACITY = 1024;
    private static final long CLOSE_JOIN_TIMEOUT_MILLIS = 2000;

    private final Agent agent;
    private final BlockingQueue<CallbackRequest> queue;
    private final Thread worker;
    private volatile boolean closed;

    public ParallelAgent(Agent agent) {
        this(agent, QUEUE_CAPACITY);
    }

    public ParallelAgent(Agent agent, int capacity) {
        if (agent == null) {
            throw new IllegalArgumentException("Wrapped agent cannot be null");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Queue capacity must be positive");
        }
        this.agent = agent;
        this.queue = new ArrayBlockingQueue<>(capacity);
        // Active Object boundary: all callbacks for the wrapped agent are serialized
        // on this worker, so concrete agents can keep simple mutable state.
        this.worker = new Thread(this::runWorker, "ParallelAgent-" + agent.getName());
        this.worker.start();
    }

    @Override
    public String getName() {
        return agent.getName();
    }

    @Override
    public void reset() {
        agent.reset();
    }

    @Override
    public void callback(String topic, Message msg) {
        if (closed) {
            return;
        }
        try {
            queue.put(CallbackRequest.callback(topic, msg));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        enqueueShutdownRequest();
        waitForWorkerToExit();
        agent.close();
    }

    private void runWorker() {
        while (true) {
            try {
                CallbackRequest request = queue.take();
                if (request.shutdown) {
                    return;
                }
                agent.callback(request.topic, request.msg);
            } catch (InterruptedException e) {
                if (closed) {
                    return;
                }
            }
        }
    }

    private void waitForWorkerToExit() {
        boolean interrupted = false;
        long deadline = System.currentTimeMillis() + CLOSE_JOIN_TIMEOUT_MILLIS;
        while (worker.isAlive() && System.currentTimeMillis() < deadline) {
            try {
                long remaining = Math.max(1, deadline - System.currentTimeMillis());
                worker.join(remaining);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (worker.isAlive()) {
            worker.interrupt();
            try {
                worker.join(CLOSE_JOIN_TIMEOUT_MILLIS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void enqueueShutdownRequest() {
        // Close is allowed to discard queued callbacks. This keeps shutdown bounded
        // even when a cycle or burst of publishes fills the queue.
        queue.clear();
        queue.offer(CallbackRequest.shutdown());
        worker.interrupt();
    }

    private static class CallbackRequest {
        private final String topic;
        private final Message msg;
        private final boolean shutdown;

        private CallbackRequest(String topic, Message msg, boolean shutdown) {
            this.topic = topic;
            this.msg = msg;
            this.shutdown = shutdown;
        }

        private static CallbackRequest callback(String topic, Message msg) {
            return new CallbackRequest(topic, msg, false);
        }

        private static CallbackRequest shutdown() {
            return new CallbackRequest(null, null, true);
        }
    }
}
