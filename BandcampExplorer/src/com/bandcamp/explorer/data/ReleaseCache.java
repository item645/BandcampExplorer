package com.bandcamp.explorer.data;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Logger;

/**
 * Implements a thread-safe caching mechanism for storing and reusing
 * instantiated Release objects.
 */
class ReleaseCache {
	/*
	 * The implementation is loosely based on "Memoizer" example from
	 * "Java Concurrency in Practice" book (Section 5.6.) with added support
	 * for weak reference values and automatic cleanup of stale entries.
	 */

	private static final Logger LOGGER = Logger.getLogger(ReleaseCache.class.getName());

	/**
	 * Sole instance of ReleaseCache
	 */
	static final ReleaseCache INSTANCE = new ReleaseCache();

	/**
	 * An instance of concurrent hash map used for actual storage
	 */
	private final ConcurrentMap<String, FutureTask<CacheValue>> cache = new ConcurrentHashMap<>(500, 0.75f, 7);

	/**
	 * Reference queue used for tracking down GC'ed releases
	 */
	private final ReferenceQueue<Release> refQueue = new ReferenceQueue<>();


	/**
	 * Weak reference wrapper for Release object together with its identifier,
	 * representing a cache value.
	 */
	private static class CacheValue extends WeakReference<Release> {
		/**
		 * Release ID
		 */
		final String id;

		/**
		 * Constructs new cache value.
		 * 
		 * @param id release ID
		 * @param release release object
		 * @param refQueue reference queue
		 * @throws NullPointerException if release object is null
		 */
		CacheValue(String id, Release release, ReferenceQueue<Release> refQueue) {
			super(Objects.requireNonNull(release), refQueue);
			this.id = id;
		}

	}


	/**
	 * Instantiates a release cache.
	 */
	private ReleaseCache() {
		// Dedicated thread to perform automatic removal of
		// stale entries from the cache
		Thread cleanupThread = new Thread(() -> {
			while (true) {
				try {
					while (true) {
						// Once release is marked by GC and its reference appended to
						// queue, we need to remove corresponding cache entry.
						// Before removal we must check that reference obtained from
						// the queue is actually the same as the one stored in cache
						// at the moment. Thus we ensure that, in case some other
						// thread has concurrently updated the entry to a new value,
						// we won't remove the updated value, but only stale one.
						CacheValue staleValue = (CacheValue)refQueue.remove();
						FutureTask<CacheValue> valueTask = cache.get(staleValue.id);
						if (valueTask.isDone()) {
							try {
								if (valueTask.get() == staleValue) {
									cache.remove(staleValue.id, valueTask);
									LOGGER.finer("Purged from cache: " + staleValue.id);
								}
							}
							catch (ExecutionException ignored) {}
						}
					}
				}
				catch (InterruptedException whoCares) {
					// We don't need to restore interruption status here because
					// there is no code higher up on the call stack that would be
					// dealing with it.
					// The only thing we want to do in case of possible interruption
					// is to continue our work until program's exit.
				}
			}
		}, "Release Cache Cleanup");
		cleanupThread.setDaemon(true);
		cleanupThread.start();
	}


	/**
	 * Returns the release with the specified identifier.
	 * If Release object with such ID is available in cache, it is returned.
	 * Otherwise, supplied loader function is used to instantiate the release.
	 * Newly created object is stored in cache and returned as a result.
	 * 
	 * @param id release identifier
	 * @param releaseLoader a callback function used to load the release if
	 *        it is not in cache yet
	 * @return a release
	 * @throws IOException if there was an IO error while loading release web page
	 *         or if release page does not contain valid data
	 * @throws RuntimeException wraps any other exception (checked or unchecked)
	 *         that might occur during release instantiation or internal operations
	 *         within a cache
	 */
	Release getRelease(String id, Callable<Release> releaseLoader) throws IOException {
		assert id != null;
		assert releaseLoader != null;

		while (true) {
			FutureTask<CacheValue> task = cache.get(id);
			if (task == null) {
				FutureTask<CacheValue> task1 = new FutureTask<>(
						() -> new CacheValue(id, releaseLoader.call(), refQueue));
				task = cache.putIfAbsent(id, task1); // make sure that second "check-then-act" sequence is atomic
				if (task == null)
					(task = task1).run();
			}
			try {
				Release release = task.get().get();
				if (release == null)
					// Result being null means that weak reference, previously available in cache,
					// has been cleared by GC and we obtained cleared value before cleanup thread had
					// a chance to remove stale entry.
					// In this case we need to remove the entry (only if it still holds the same completed
					// future task with cleared result) and fall through to the beginning of the loop to
					// repeat the whole sequence once again.
					cache.remove(id, task);
				else
					return release;
			}
			catch (CancellationException | InterruptedException | ExecutionException e) {
				cache.remove(id);
				if (e instanceof InterruptedException)
					Thread.currentThread().interrupt();
				else if (e instanceof ExecutionException) {
					Throwable cause = e.getCause();
					if (cause instanceof IOException)
						throw (IOException)cause;
				}
				throw new RuntimeException(e);
			}
		}
	}

}
