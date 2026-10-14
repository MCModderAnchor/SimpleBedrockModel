package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** 跨帧上传任务；只在调用 drain 的线程执行，不创建 OpenGL 工作线程。 */
public class MeshUploadQueue<K> {
    public interface Upload {
        long bytes();
        boolean admitted();
        void upload();
        void cancel();
    }

    private class Job {
        final K key;
        final ArrayDeque<Upload> remaining;
        final BooleanSupplier current;
        final Consumer<RuntimeException> failed;

        Job(K key, List<? extends Upload> uploads, BooleanSupplier current,
            Consumer<RuntimeException> failed) {
            this.key = key;
            this.remaining = new ArrayDeque<>(uploads);
            this.current = current;
            this.failed = failed;
        }
    }

    private final MeshPreparationPolicy policy;
    private final LongSupplier clock;
    private final Map<K, Job> jobs = new HashMap<>();
    private final ArrayDeque<Job> queue = new ArrayDeque<>();
    private long frame = Long.MIN_VALUE;
    private int attempted, uploaded;
    private long attemptedBytes, uploadedBytes, nanos, pendingBytes;
    private int pendingParts;

    public MeshUploadQueue(MeshPreparationPolicy policy, LongSupplier clock) {
        this.policy = Objects.requireNonNull(policy);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 同键已有任务时保留原任务；新数据的释放责任仍在提交者。 */
    public boolean enqueue(K key, List<? extends Upload> uploads, BooleanSupplier current,
                           Consumer<RuntimeException> failed) {
        if (jobs.containsKey(key)) return false;
        if (uploads.isEmpty()) throw new IllegalArgumentException("Empty upload job");
        for (Upload upload : uploads) if (upload.bytes() < 0) throw new IllegalArgumentException("Negative upload bytes");
        Job job = new Job(key, uploads, current, failed);
        jobs.put(key, job);
        queue.addLast(job);
        for (Upload upload : uploads) { pendingBytes += upload.bytes(); pendingParts++; }
        return true;
    }

    /** 相同帧再次进入不会重置额度；多个渲染阶段共享预算。 */
    public void beginFrame(long frame) {
        if (this.frame == frame) return;
        this.frame = frame;
        attempted = uploaded = 0;
        attemptedBytes = uploadedBytes = nanos = 0;
    }

    public void drain() {
        int blocked = 0;
        while (!queue.isEmpty() && attempted < policy.maxUploads()
                && (attempted == 0 || nanos < policy.uploadNanos())) {
            Job job = queue.removeFirst();
            if (jobs.get(job.key) != job) continue;
            if (!job.current.getAsBoolean()) { cancel(job.key); continue; }
            Upload next = job.remaining.peekFirst();
            boolean fits = attempted == 0 || next.bytes() <= policy.uploadBytes() - attemptedBytes;
            if (!fits || !next.admitted()) {
                queue.addLast(job);
                if (++blocked >= queue.size()) break;
                continue;
            }
            blocked = 0;
            job.remaining.removeFirst();
            pendingParts--;
            pendingBytes -= next.bytes();
            attempted++;
            attemptedBytes += next.bytes();
            long start = clock.getAsLong();
            try {
                next.upload();
                uploaded++;
                uploadedBytes += next.bytes();
                // 上传钩子可能在执行过程中失效整个所有者。
                if (jobs.get(job.key) == job) {
                    if (job.remaining.isEmpty()) jobs.remove(job.key);
                    else queue.addLast(job);
                }
            } catch (RuntimeException failure) {
                if (jobs.get(job.key) == job) {
                    cancel(job.key);
                    job.failed.accept(failure);
                }
            }
            nanos += Math.max(0, clock.getAsLong() - start);
        }
    }

    public void cancel(K key) {
        Job job = jobs.remove(key);
        if (job == null) return;
        queue.remove(job);
        Upload upload;
        while ((upload = job.remaining.pollFirst()) != null) {
            pendingParts--;
            pendingBytes -= upload.bytes();
            upload.cancel();
        }
        // 已开始的 upload 由执行者完成清理，不在其 OpenGL 调用中途销毁资源。
    }

    public void clear() { for (K key : List.copyOf(jobs.keySet())) cancel(key); }
    public boolean contains(K key) { return jobs.containsKey(key); }
    public int pendingParts() { return pendingParts; }
    public long pendingBytes() { return pendingBytes; }
    public int uploadedParts() { return uploaded; }
    public long uploadedBytes() { return uploadedBytes; }
    public long uploadNanos() { return nanos; }
}
