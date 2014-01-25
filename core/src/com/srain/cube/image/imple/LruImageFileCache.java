package com.srain.cube.image.imple;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.support.v4.app.FragmentManager;
import android.util.Log;

import com.srain.cube.concurrent.SimpleExcutor;
import com.srain.cube.concurrent.SimpleTask;
import com.srain.cube.file.DiskLruCache;
import com.srain.cube.file.DiskLruCache.Editor;
import com.srain.cube.file.FileUtil;
import com.srain.cube.image.iface.ImageFileCache;
import com.srain.cube.util.CLog;

/**
 * 
 * This class handles disk and memory caching of bitmaps.
 * 
 * Most of the code is taken from the Android best practice of displaying Bitmaps <a href="http://developer.android.com/training/displaying-bitmaps/index.html">Displaying Bitmaps Efficiently</a>.
 * 
 * @author huqiu.lhq
 */
public class LruImageFileCache {

	protected static final boolean DEBUG = CLog.DEBUG_IMAGE;

	protected static final String TAG = "image_provider";

	private static final String DEFAULT_CACHE_DIR = "cube-image";
	private static final int DEFAULT_CACHE_SIZE = 1024 * 1024 * 10;
	private static LruImageFileCache sDefault;

	// Compression settings when writing images to disk cache
	private static final CompressFormat DEFAULT_COMPRESS_FORMAT = CompressFormat.JPEG;
	private static final int DEFAULT_COMPRESS_QUALITY = 70;
	private static final int DISK_CACHE_INDEX = 0;

	private DiskLruCache mDiskLruCache;
	private final Object mDiskCacheLock = new Object();
	private boolean mDiskCacheStarting = true;
	private boolean mDiskCacheReady = false;
	private File mDiskCacheDir;
	private int mDiskCacheSize;

	private long mLastFlushTime = 0;

	protected enum FileCacheTaskType {
		init_cache, close_cache, flush_cache
	}

	/**
	 * Create a new ImageProvider object using the specified parameters. This should not be called directly by other classes, instead use {@link ImageFileCache#getInstance(FragmentManager, ImageCacheParams)} to fetch an ImageProvider instance.
	 * 
	 * @param cacheParams
	 *            The cache parameters to use to initialize the cache
	 */
	public LruImageFileCache(int sizeInKB, File path) {
		mDiskCacheSize = sizeInKB;
		mDiskCacheDir = path;
	}

	public static LruImageFileCache getDefault(Context context) {
		if (null == sDefault) {
			sDefault = new LruImageFileCache(DEFAULT_CACHE_SIZE, FileUtil.getDiskCacheDir(context, DEFAULT_CACHE_DIR, DEFAULT_CACHE_SIZE));
			sDefault.initDiskCacheAsync();
		}
		return sDefault;
	}

	/**
	 * Initializes the disk cache. Note that this includes disk access so this should not be executed on the main/UI thread. By default an ImageProvider does not initialize the disk cache when it is created, instead you should call initDiskCache() to initialize it on a background thread.
	 */
	public void initDiskCache() {
		if (DEBUG) {
			Log.d(TAG, "initDiskCache " + this);
		}
		// Set up disk cache
		synchronized (mDiskCacheLock) {
			if (mDiskLruCache == null || mDiskLruCache.isClosed()) {
				if (mDiskCacheDir != null) {
					if (!mDiskCacheDir.exists()) {
						mDiskCacheDir.mkdirs();
					}
					if (FileUtil.getUsableSpace(mDiskCacheDir) > mDiskCacheSize) {
						try {
							mDiskLruCache = DiskLruCache.open(mDiskCacheDir, 1, 1, mDiskCacheSize);
							if (DEBUG) {
								Log.d(TAG, "Disk cache initialized " + this);
							}
						} catch (final IOException e) {
							Log.e(TAG, "initDiskCache - " + e);
						}
					} else {
						Log.e(TAG, String.format("no enough space for initDiskCache %s %s", FileUtil.getUsableSpace(mDiskCacheDir), mDiskCacheSize));
					}
				}
			}
			mDiskCacheStarting = false;
			mDiskCacheReady = true;
			mDiskCacheLock.notifyAll();
		}
	}

	/**
	 * Adds a bitmap to both memory and disk cache
	 * 
	 * @param key
	 *            Unique identifier for the bitmap to store
	 * @param bitmap
	 *            The bitmap to store
	 */
	public void write(String key, Bitmap bitmap) {
		if (key == null || bitmap == null) {
			return;
		}

		synchronized (mDiskCacheLock) {

			// Add to disk cache
			if (mDiskLruCache != null) {
				OutputStream out = null;
				try {
					DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
					if (snapshot == null) {
						final DiskLruCache.Editor editor = mDiskLruCache.edit(key);
						if (editor != null) {
							out = editor.newOutputStream(DISK_CACHE_INDEX);
							bitmap.compress(DEFAULT_COMPRESS_FORMAT, DEFAULT_COMPRESS_QUALITY, out);
							editor.commit();
							out.close();
						}
					}
				} catch (final IOException e) {
					Log.e(TAG, "addBitmapToCache - " + e);
				} catch (Exception e) {
					Log.e(TAG, "addBitmapToCache - " + e);
				} finally {
					try {
						if (out != null) {
							out.close();
						}
					} catch (IOException e) {
					}
				}
			}
		}
	}

	public InputStream read(String fileCacheKey) {
		if (!mDiskCacheReady) {
			initDiskCache();
		}

		synchronized (mDiskCacheLock) {
			while (mDiskCacheStarting) {
				try {
					if (DEBUG) {
						Log.d(TAG, "read wait " + this);
					}
					mDiskCacheLock.wait();
				} catch (InterruptedException e) {
				}
			}
			if (mDiskLruCache != null) {
				InputStream inputStream = null;
				DiskLruCache.Snapshot snapshot = null;
				try {
					snapshot = mDiskLruCache.get(fileCacheKey);

				} catch (final IOException e) {
					Log.e(TAG, "getBitmapFromDiskCache - " + e);
				}

				if (snapshot == null) {
					return null;
				} else {
					inputStream = snapshot.getInputStream(DISK_CACHE_INDEX);
					return inputStream;
				}
			}
			return null;
		}
	}

	public Editor open(String key) throws IOException {
		if (null != mDiskLruCache) {
			return mDiskLruCache.edit(key);
		}
		return null;
	}

	/**
	 * Clears both the memory and disk cache associated with this ImageProvider object. Note that this includes disk access so this should not be executed on the main/UI thread.
	 */
	public void clearCache() {

		synchronized (mDiskCacheLock) {
			mDiskCacheStarting = true;
			mDiskCacheReady = false;

			if (mDiskLruCache != null && !mDiskLruCache.isClosed()) {
				try {
					mDiskLruCache.delete();
					if (DEBUG) {
						Log.d(TAG, "Disk cache cleared");
					}
				} catch (IOException e) {
					Log.e(TAG, "clearCache - " + e);
				}
				mDiskLruCache = null;

				initDiskCache();
			}
		}
	}

	/**
	 * Flushes the disk cache associated with this ImageProvider object. Note that this includes disk access so this should not be executed on the main/UI thread.
	 */
	public void flushDishCache() {
		synchronized (mDiskCacheLock) {
			long now = System.currentTimeMillis();
			if (now - 1000 < mLastFlushTime) {
				return;
			}
			mLastFlushTime = now;
			if (mDiskLruCache != null) {
				try {
					mDiskLruCache.flush();
					if (DEBUG) {
						Log.d(TAG, "Disk cache flushed");
					}
				} catch (IOException e) {
					Log.e(TAG, "flush - " + e);
				}
			}
		}
	}

	/**
	 * Closes the disk cache associated with this ImageProvider object. Note that this includes disk access so this should not be executed on the main/UI thread.
	 */
	public void closeDiskCache() {
		synchronized (mDiskCacheLock) {
			if (mDiskLruCache != null) {
				try {
					if (!mDiskLruCache.isClosed()) {
						mDiskLruCache.close();
						mDiskLruCache = null;
						if (DEBUG) {
							Log.d(TAG, "Disk cache closed");
						}
					}
				} catch (IOException e) {
					Log.e(TAG, "close - " + e);
				}
			}
		}
	}

	/**
	 * A helper class to encapsulate the operate into a Work which will be executed by the Worker.
	 * 
	 * @author huqiu.lhq
	 * 
	 */
	private class FileCacheTask extends SimpleTask {

		private FileCacheTask(FileCacheTaskType taskType) {
			mTaskType = taskType;
		}

		private FileCacheTaskType mTaskType;

		@Override
		public void doInBackground() {

			switch (mTaskType) {
			case init_cache:
				initDiskCache();
				break;
			case close_cache:
				closeDiskCache();
				break;
			case flush_cache:
				flushDishCache();
				break;
			default:
				break;
			}
		}

		@Override
		public void onFinish() {
		}

		void excute() {
			SimpleExcutor.getInstance().execute(this);
		}
	}

	/**
	 * initiate the disk cache
	 */
	public void initDiskCacheAsync() {
		if (DEBUG) {
			Log.d(TAG, "initDiskCacheAsync " + this);
		}
		new FileCacheTask(FileCacheTaskType.init_cache).excute();
	}

	/**
	 * close the disk cache
	 */
	public void closeDiskCacheAsync() {
		if (DEBUG) {
			Log.d(TAG, "closeDiskCacheAsync");
		}
		new FileCacheTask(FileCacheTaskType.close_cache).excute();
	}

	/**
	 * flush the data to disk cache
	 */
	public void flushDishCacheAsync() {
		if (DEBUG) {
			Log.d(TAG, "flushDishCacheAsync");
		}
		new FileCacheTask(FileCacheTaskType.flush_cache).excute();
	}
}
