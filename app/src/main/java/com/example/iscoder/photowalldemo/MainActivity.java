package com.example.iscoder.photowalldemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.GridView;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    /**
     * 用于展示照片墙的GridView
     */
    private GridView mPhotoWall;
    /**
     * GridView的适配器
     */
    private PhotoWallAdapter mAdapter;
    /**
     * 清理按钮
     */
    private Button clean_button;
    private int mImageThumbSize;
    private int mImageThumbSpacing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageThumbSize = getResources().getDimensionPixelSize(
                R.dimen.image_thumbnail_size);
        mImageThumbSpacing = getResources().getDimensionPixelSize(
                R.dimen.image_thumbnail_spacing);
        clean_button = (Button) findViewById(R.id.button_clean);
        mPhotoWall = (GridView) findViewById(R.id.photo_wall);
        mAdapter = new PhotoWallAdapter(this, 0, Images.imagesUrl, mPhotoWall, clean_button);

        clean_button.setOnClickListener(this);
        clean_button.setText("清理缓存：" + mAdapter.getCacheSize());
        mPhotoWall.setAdapter(mAdapter);
        /**
         * 通过getViewTreeObserver()的方式监听View的布局事件，
         * 当布局完成以后，我们重新修改一下GridView中子View的高度，以保证子View的宽度和高度可以保持一致。
         */
        mPhotoWall.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        int numColumns = (int) Math.floor(mPhotoWall
                                .getWidth()
                                / (mImageThumbSize + mImageThumbSpacing));
                        if (numColumns > 0) {
                            int columnWidth = (mPhotoWall.getWidth() / numColumns)
                                    - mImageThumbSpacing;
                            mAdapter.setItemHeight(columnWidth);
                            mPhotoWall.getViewTreeObserver()
                                    .removeOnGlobalLayoutListener(this);
                        }
                    }
                }
        );
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_clean: {
                mAdapter.cleanCache();
                clean_button.setText("清理缓存：" + mAdapter.getCacheSize());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAdapter.fluchCache();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //退出程序时结束所有的下载任务
        mAdapter.cancelAllTask();
    }


}
