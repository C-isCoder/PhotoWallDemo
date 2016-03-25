# PhotoWallDemo
郭神博客，Android照片墙完整版，完美结合LruCache和DiskLruCache
##核心类：
<pre>package com.example.iscoder.photowalldemo;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;

import com.example.iscoder.libcore.io.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by iscod on 2016/3/24.
 */
public class PhotoWallAdapter extends ArrayAdapter<String> {
    private Context mContext;
    private Button mClean;
    /**
     * 记录所有正在下载或等待下载的任务
     */
    private Set<BitmapWorkerTask> taskCollection;
    /**
     * 图片缓存技术的核心类，用于缓存所有下载好的图片，在程序内存达到设定值时，会将最少最近使用的图片移除掉。
     */
    private LruCache<String, Bitmap> mMemoryCache;
    /**
     * 图片硬盘缓存核心类。
     */
    private DiskLruCache mDiskLruCache;
    /**
     * GridView实例
     */
    private GridView mPhotoWall;
    /**
     * 记录每个子项高度。
     */
    private int mItemHeight = 0;

    public PhotoWallAdapter(Context context, int textViewResourceId, String[] objects,
                            GridView photoWall, Button clean_button) {
        super(context, textViewResourceId, objects);
        mContext = context;
        mPhotoWall = photoWall;
        mClean = clean_button;
        taskCollection = new HashSet<BitmapWorkerTask>();
        //获取应用最大可用内存
        int maxMemoery = (int) Runtime.getRuntime().maxMemory();
        //设置图片缓存大小为应用最大内存的1/8
        int cacheSize = maxMemoery / 8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            //重写sizeOf方法，返回图片的占用字节数而不是图片的个数，每次添加图片是会被调用
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };
        initDiskLruCache(context);
    }

    private void initDiskLruCache(Context context) {
        try {
            //获取图片缓存路径
            File cacheDir = Utils.getDiskCacheDir(context, "thumb");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            //创建DiskLruCache实例，初始化缓存数据。
            mDiskLruCache = DiskLruCache
                    .open(cacheDir, Utils.getAppVersion(context), 1, 10 * 1024 * 1024);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final String url = getItem(position);
        View view;
        if (convertView == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.phote_item_layout, null);
        } else {
            view = convertView;
        }
        final ImageView imageView = (ImageView) view.findViewById(R.id.photo);
        if (imageView.getLayoutParams().height != mItemHeight) {
            imageView.getLayoutParams().height = mItemHeight;
        }
        imageView.setTag(url);
        //设置个默认图片，这样在移除屏幕重绘的时候会显示这张默认图。不然会显示上次显示过的照片，体验不好。
        imageView.setImageResource(R.mipmap.empty_photo);
        loadBitmaps(imageView, url);
        return view;
    }

    /**
     * 将一张图片存储到LruCache中。
     *
     * @param key    lruCache的键，这里传入图片的url；
     * @param bitmap lruCache的值，这里传入从网路下载的Bitmap对象。
     */
    public void addBitmapToMemroyCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemoryCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    /**
     * 从lruCache中获取一张图片，如果不存在就返回null。
     *
     * @param key lruCache的键，这里传入图片的url
     * @return 返回对应键的Bitmap对象，或者null
     */
    public Bitmap getBitmapFromMemoryCache(String key) {
        return mMemoryCache.get(key);
    }

    /**
     * 加载Bitmap对象。此方法会在LruCache中检查所有屏幕中可见的IamgeView的Bitmap对象。
     * 如果发现任何一个ImageView的Bitmap对象不存在缓存中，就会开启异步线程去下载图片。
     *
     * @param imageView
     * @param imageUrl
     */
    public void loadBitmaps(ImageView imageView, String imageUrl) {
        Bitmap bitmap = getBitmapFromMemoryCache(imageUrl);
        if (bitmap == null) {
            BitmapWorkerTask task = new BitmapWorkerTask();
            taskCollection.add(task);
            task.execute(imageUrl);
        } else {
            if (imageView != null && bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }
        }
    }

    /**
     * 取消所有正在下载或等待下载的任务
     */
    public void cancelAllTask() {
        if (taskCollection != null) {
            for (BitmapWorkerTask task : taskCollection) {
                task.cancel(false);
            }
        }
    }

    /**
     * 设置Item子项高度
     *
     * @param height
     */
    public void setItemHeight(int height) {
        if (height == mItemHeight) return;
        mItemHeight = height;
        notifyDataSetChanged();
    }


    /**
     * 将缓存记录同步到journal文件中。
     */
    public void fluchCache() {
        if (mDiskLruCache != null && !mDiskLruCache.isClosed()) {
            try {
                mDiskLruCache.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取缓存大小
     *
     * @return
     */
    public String getCacheSize() {
        double size = 0;
        if (mDiskLruCache != null && mMemoryCache != null) {
            size = mDiskLruCache.size();
            size += mMemoryCache.size();
        }
        return Utils.getFormatSize(size);
    }

    /**
     * 清理缓存
     */
    public void cleanCache() {
        if (!mDiskLruCache.isClosed() && mDiskLruCache != null) {
            try {
                mDiskLruCache.delete();
                initDiskLruCache(mContext);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mMemoryCache != null) {
            mMemoryCache.evictAll();//清空cache
        }
    }

    /**
     * 异步下载图片任务
     * Created by iscod on 2016/3/24.
     */
    class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
        /**
         * 图片Url地址
         */
        private String imageUrl;

        @Override
        protected Bitmap doInBackground(String... params) {
            imageUrl = params[0];
            FileDescriptor fileDescriptor = null;
            FileInputStream fileInputStream = null;
            DiskLruCache.Snapshot snapshot = null;
            try {
                //生成图片Url对应的key
                final String key = Utils.hashKeyForDisk(imageUrl);
                //查找key对应的缓存
                snapshot = mDiskLruCache.get(key);
                if (snapshot == null) {
                    //如果没有找到对应的缓存，则准备从网络上请求数据，并写入缓存。
                    DiskLruCache.Editor editor = mDiskLruCache.edit(key);
                    if (editor != null && !mDiskLruCache.isClosed()) {
                        OutputStream outputStream = editor.newOutputStream(0);
                        if (downloadUrlToStream(imageUrl, outputStream)) {
                            editor.commit();
                            handler.sendEmptyMessage(UPDATE_CACHE);
                        } else {
                            try {
                                editor.abort();
                            } catch (Exception e) {

                            }
                        }
                    }
                    //缓存被写入后，再次查找key对应的缓存
                    snapshot = mDiskLruCache.get(key);
                }
                if (snapshot != null) {
                    fileInputStream = (FileInputStream) snapshot.getInputStream(0);
                    fileDescriptor = fileInputStream.getFD();
                }
                //将缓存的数据解析成Bitmap对象.
                Bitmap bitmap = null;
                if (fileDescriptor != null) {
                    bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                    //这个方法也可以 上面方法更好，区分不同设备不同标准详见印象笔记Day：2016-03-24
                    //bitmap = BitmapFactory.decodeStream(fileInputStream);
                }
                if (bitmap != null) {
                    //将Bitmap对象添加到内存缓存中
                    addBitmapToMemroyCache(params[0], bitmap);
                }
                return bitmap;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fileDescriptor == null && fileInputStream != null) {
                    try {
                        fileInputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            //根据Tag找到相应的Image控件，将下载好的图片显示出来。
            ImageView imageView = (ImageView) mPhotoWall.findViewWithTag(imageUrl);
            if (imageView != null && bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }
            taskCollection.remove(this);
        }

        /**
         * 建立Http请求，并获取Bitmap对象。
         *
         * @param urlString    图片的url地址
         * @param outputStream 解析后的Bitmap对象
         * @return
         */
        private boolean downloadUrlToStream(String urlString, OutputStream outputStream) {
            HttpURLConnection urlConnection = null;
            BufferedInputStream in = null;
            BufferedOutputStream out = null;
            try {
                URL url = new URL(urlString);
                urlConnection = (HttpURLConnection) url.openConnection();
                in = new BufferedInputStream(urlConnection.getInputStream(), 8 * 1024);
                out = new BufferedOutputStream(outputStream, 8 * 1024);
                int b;
                while ((b = in.read()) != -1) {
                    out.write(b);
                }
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                try {
                    if (out != null) {
                        out.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }
    }

    /**
     * 动态更新缓存大小
     */
    private static final int UPDATE_CACHE = 1;
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case UPDATE_CACHE: {
                    mClean.setText("清理缓存:" + getCacheSize());
                }
            }
        }
    };

}</pre>
