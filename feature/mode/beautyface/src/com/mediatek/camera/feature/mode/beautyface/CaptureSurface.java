/*
 *   Copyright Statement:
 *
 *     This software/firmware and related documentation ("mediatek Software") are
 *     protected under relevant copyright laws. The information contained herein is
 *     confidential and proprietary to mediatek Inc. and/or its licensors. Without
 *     the prior written permission of mediatek inc. and/or its licensors, any
 *     reproduction, modification, use or disclosure of mediatek Software, and
 *     information contained herein, in whole or in part, shall be strictly
 *     prohibited.
 *
 *     mediatek Inc. (C) 2017. All rights reserved.
 *
 *     BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 *    THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("mediatek SOFTWARE")
 *     RECEIVED FROM mediatek AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 *     ON AN "AS-IS" BASIS ONLY. mediatek EXPRESSLY DISCLAIMS ANY AND ALL
 *     WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 *     WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 *     NONINFRINGEMENT. NEITHER DOES mediatek PROVIDE ANY WARRANTY WHATSOEVER WITH
 *     RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 *     INCORPORATED IN, OR SUPPLIED WITH THE mediatek SOFTWARE, AND RECEIVER AGREES
 *     TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 *     RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 *     OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN mediatek
 *     SOFTWARE. mediatek SHALL ALSO NOT BE RESPONSIBLE FOR ANY mediatek SOFTWARE
 *     RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 *     STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND mediatek'S
 *     ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE mediatek SOFTWARE
 *     RELEASED HEREUNDER WILL BE, AT mediatek'S OPTION, TO REVISE OR REPLACE THE
 *     mediatek SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 *     CHARGE PAID BY RECEIVER TO mediatek FOR SUCH mediatek SOFTWARE AT ISSUE.
 *
 *     The following software/firmware and/or related documentation ("mediatek
 *     Software") have been modified by mediatek Inc. All revisions are subject to
 *     any receiver's applicable license agreements with mediatek Inc.
 */
package com.mediatek.camera.feature.mode.beautyface;

import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import com.mediatek.camera.common.debug.LogHelper;
import com.mediatek.camera.common.debug.LogUtil;

import java.nio.ByteBuffer;

/**
 * Used for capture surface.
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
class CaptureSurface {
    private static final LogUtil.Tag TAG = new LogUtil.Tag(CaptureSurface.class.getSimpleName());
    private int mPictureWidth;
    private int mPictureHeight;
    @SuppressWarnings("deprecation")
    private int mFormat = PixelFormat.JPEG;
    private int mMaxImages = 2;
    private ImageReader mCaptureImageReader;
    private final Handler mCaptureHandler;
    private final Object mImageReaderSync = new Object();
    private ImageCallback mImageCallback;

    /**
     * Capture image callback.
     */
    public interface ImageCallback {
        /**
         * Called when capture callback received.
         *
         * @param data picture data.
         */
        void onPictureCallback(byte[] data);
    }

    /**
     * Set image callback.
     *
     * @param captureCallback The callback used to receive capture callback.
     */
    public void setCaptureCallback(ImageCallback captureCallback) {
        mImageCallback = captureCallback;
    }

    /**
     * Prepare the capture surface handler.
     */
    public CaptureSurface() {
        LogHelper.d(TAG, "[CaptureSurface] Construct");
        HandlerThread captureHandlerThread = new HandlerThread("cap_surface");
        captureHandlerThread.start();
        mCaptureHandler = new Handler(captureHandlerThread.getLooper());
    }

    /**
     * Update a new picture info,such as size ,format , max image.
     *
     * @param width    the target picture width.
     * @param height   the target picture height.
     * @param format   The format of the Image that this reader will produce.
     *                 this must be one of the {@link ImageFormat} or
     *                 {@link PixelFormat} constants. Note that not
     *                 all formats are supported, like ImageFormat.NV21. The default value is
     *                 PixelFormat.JPEG;
     * @param maxImage The maximum number of images the user will want to
     *                 access simultaneously. This should be as small as possible to
     *                 limit memory use. Once maxImages Images are obtained by the
     *                 user, one of them has to be released before a new Image will
     *                 become available for access through onImageAvailable().
     *                 Must be greater than 0.
     * @return if surface is changed, will return true, otherwise will false.
     */
    public boolean updatePictureInfo(int width, int height, int format, int maxImage) {
        // Check picture info whether is same as before or not.
        // if the info don't change, No need create it again.
        LogHelper.i(TAG, "[updatePictureInfo] width = " + width + ",height = " + height + "," +
                "format = " + format + ",maxImage = " + maxImage + ",mCaptureImageReader = " +
                mCaptureImageReader);
        if (mCaptureImageReader != null && mPictureWidth == width && mPictureHeight == height &&
                format == mFormat && maxImage == mMaxImages) {
            LogHelper.d(TAG, "[updatePictureInfo],the info : " + mPictureWidth + " x " +
                    mPictureHeight + ",format = " + format + ",maxImage = " + maxImage + " is " +
                    "same as before");
            return false;
        }
        // Save the new picture info.
        mPictureWidth = width;
        mPictureHeight = height;
        mFormat = format;
        mMaxImages = maxImage;

        // Create a image reader for images of the desired size,format and max image.
        synchronized (mImageReaderSync) {
            mCaptureImageReader = ImageReader.newInstance(mPictureWidth, mPictureHeight, mFormat,
                    mMaxImages);
            mCaptureImageReader.setOnImageAvailableListener(mCaptureImageListener, mCaptureHandler);
        }
        return true;
    }

    /**
     * Get the capture surface from image reader.
     *
     * @return the surface is from image reader.
     * if don't have call the updatePictureInfo() before getSurface() will be return null.
     * such as you have calling releaseCaptureSurface(), the value is null.
     */
    public Surface getSurface() {
        synchronized (mImageReaderSync) {
            if (mCaptureImageReader != null) {
                return mCaptureImageReader.getSurface();
            }
            return null;
        }
    }

    /**
     * Release the capture surface when don't need again.
     */
    public void releaseCaptureSurface() {
        LogHelper.d(TAG, "[releaseCaptureSurface], mCaptureImageReader = " + mCaptureImageReader);
        synchronized (mImageReaderSync) {
            if (mCaptureImageReader != null) {
                mCaptureImageReader.close();
                mCaptureImageReader = null;
            }
        }
    }

    /**
     * When activity destroy, release the resource.
     */
    public void release() {
        if (mCaptureHandler != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mCaptureHandler.getLooper().quitSafely();
            } else {
                mCaptureHandler.getLooper().quit();
            }
        }
    }

    private final OnImageAvailableListener mCaptureImageListener = new OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            LogHelper.i(TAG, "[onImageAvailable]");
            if (mImageCallback != null) {
                mImageCallback.onPictureCallback(getJpeg(imageReader.acquireLatestImage()));
            }
        }
    };

    private byte[] getJpeg(Image image) {
        synchronized (mImageReaderSync) {
            ByteBuffer buffer;
            if (ImageFormat.JPEG == image.getFormat()) {
                Image.Plane plane = image.getPlanes()[0];
                buffer = plane.getBuffer();
                byte[] imageBytes = new byte[buffer.remaining()];
                buffer.get(imageBytes);
                buffer.rewind();
                image.close();
                return imageBytes;
            } else {
                image.close();
                throw new RuntimeException("[getJpeg] image format not supported.");
            }
        }
    }
}
