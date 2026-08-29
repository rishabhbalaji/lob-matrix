package com.lobmatrix.wal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ultra-low latency asynchronous WAL writer.
 * Decouples market data ingestion from disk I/O using a bounded queue and dedicated writer thread.
 */
public class AsyncBinaryWALWriter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncBinaryWALWriter.class);
    private static final int DEFAULT_QUEUE_CAPACITY = 65_536;
    private static final long FLUSH_INTERVAL_MS = 1_000L;

    private final Path baseDir;
    private final short connectionId;
    private final BlockingQueue<byte[]> queue;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong globalSequence = new AtomicLong(0);

    private Thread writerThread;
    private FileChannel fileChannel;
    private Path currentFilePath;
    private long lastFlushNanos;

    public AsyncBinaryWALWriter(Path baseDir, short connectionId, int queueCapacity) {
        this.baseDir = baseDir;
        this.connectionId = connectionId;
        this.queue = new ArrayBlockingQueue<>(queueCapacity > 0 ? queueCapacity : DEFAULT_QUEUE_CAPACITY);
    }

    public AsyncBinaryWALWriter(Path baseDir, short connectionId) {
        this(baseDir, connectionId, DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * Initializes the WAL file and starts the background writer thread.
     */
    public synchronized void start() throws IOException {
        if (isRunning.compareAndSet(false, true)) {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            Path dateDir = baseDir.resolve(dateStr);
            Files.createDirectories(dateDir);

            String fileName = String.format("feed_conn%d_%d.raw", connectionId, System.currentTimeMillis());
            this.currentFilePath = dateDir.resolve(fileName);
            this.fileChannel = FileChannel.open(currentFilePath,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.READ);

            this.lastFlushNanos = System.nanoTime();

            writerThread = new Thread(this::writerLoop, "async-wal-writer-" + connectionId);
            writerThread.setDaemon(false); // Ensure all data flushes before JVM exits
            writerThread.start();

            log.info("AsyncBinaryWALWriter started. Active WAL: {}", currentFilePath.toAbsolutePath());
        }
    }

    /**
     * High-speed non-blocking enqueue method called by WebSocket receiver threads.
     * Serializes 40-byte framing and pushes into the bounded queue.
     *
     * @return true if appended successfully, false if queue is saturated
     */
    public boolean append(long arrivalNanos, long arrivalMicros, byte[] payload) {
        if (!isRunning.get() || payload == null) {
            return false;
        }

        long seq = globalSequence.getAndIncrement();
        byte[] envelope = BinaryWALSerializer.serialize(connectionId, seq, arrivalNanos, arrivalMicros, payload);

        // Non-blocking queue offer
        boolean offered = queue.offer(envelope);
        if (!offered) {
            log.error("WAL Ingestion overflow! Dropping frame sequence: {}", seq);
        }
        return offered;
    }

    private void writerLoop() {
        while (isRunning.get() || !queue.isEmpty()) {
            try {
                byte[] envelope = queue.poll(100, TimeUnit.MILLISECONDS);
                if (envelope != null) {
                    ByteBuffer buffer = ByteBuffer.wrap(envelope);
                    while (buffer.hasRemaining()) {
                        fileChannel.write(buffer);
                    }
                }

                // Check periodic 1,000ms disk sync
                long now = System.nanoTime();
                if ((now - lastFlushNanos) >= (FLUSH_INTERVAL_MS * 1_000_000L)) {
                    fileChannel.force(false);
                    lastFlushNanos = now;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                log.error("I/O error in WAL writer loop", e);
            }
        }

        // Final shutdown flush
        try {
            if (fileChannel != null && fileChannel.isOpen()) {
                fileChannel.force(true);
            }
        } catch (IOException e) {
            log.error("Error during final WAL force flush", e);
        }
    }

    public Path getCurrentFilePath() {
        return currentFilePath;
    }

    public long getWrittenSequenceCount() {
        return globalSequence.get();
    }

    @Override
    public synchronized void close() throws Exception {
        if (isRunning.compareAndSet(true, false)) {
            if (writerThread != null) {
                writerThread.join(5000);
            }
            if (fileChannel != null && fileChannel.isOpen()) {
                fileChannel.force(true);
                fileChannel.close();
            }
            log.info("AsyncBinaryWALWriter closed cleanly. Total envelopes: {}", globalSequence.get());
        }
    }
}
