package jpiccoli.io.ssl;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;

public class ConsumingQueue<E> {

    private final Deque<E> queue;
    private E consuming;

    public ConsumingQueue() {
        queue = new LinkedList<>();
    }

    public synchronized void add(E e) {
        queue.add(e);
    }

    public synchronized E consume() {
        if (consuming == null) {
            consuming = queue.poll();
            return consuming;
        }
        return null;
    }

    public synchronized boolean consumed(E e) {
        if (consuming == e) {
            consuming = null;
            return true;
        }
        return false;
    }

    public synchronized boolean replay(E e) {
        if (consuming == e) {
            queue.addFirst(e);
            consuming = null;
            return true;
        }
        return false;
    }

    public synchronized Queue<E> copyAndClear() {
        Queue<E> queueCopy = new LinkedList<>(queue);
        queue.clear();
        return queueCopy;
    }

}
