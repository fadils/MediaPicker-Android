package org.wordpress.mediapicker.source;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Parcel;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.android.volley.toolbox.ImageLoader;

import org.wordpress.mediapicker.MediaItem;
import org.wordpress.mediapicker.MediaUtils;
import org.wordpress.mediapicker.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MediaSourceDeviceVideos implements MediaSource {
    private static final String[] VIDEO_QUERY_COLUMNS = { MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA };
    private static final String[] THUMBNAIL_QUERY_COLUMNS = { MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_TAKEN };

    private OnMediaChange mListener;
    private ContentResolver mContentResolver;
    private List<MediaItem> mMediaItems;
    private boolean mGatheringMedia;

    public MediaSourceDeviceVideos() {
        mContentResolver = null;
        mGatheringMedia = false;
    }

    private void setMediaItems(List<MediaItem> mediaItems) {
        mMediaItems = mediaItems;
    }

    public MediaSourceDeviceVideos(final ContentResolver contentResolver) {
        mContentResolver = contentResolver;
        createMediaItems();
    }

    @Override
    public void gather() {
        if (!mGatheringMedia) {
            new AsyncTask<Void, String, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    createMediaItems();
                    return null;
                }

                @Override
                public void onPostExecute(Void result) {
                    if (mListener != null) {
                        mListener.onMediaLoaded(true);
                    }

                    mGatheringMedia = false;
                }
            }.execute();

            mGatheringMedia = true;
        }
    }

    @Override
    public void cleanup() {
        if (!mGatheringMedia) {
            mMediaItems.clear();
        }
    }

    @Override
    public void setListener(final OnMediaChange listener) {
        mListener = listener;
    }

    @Override
    public int getCount() {
        return mMediaItems.size();
    }

    @Override
    public MediaItem getMedia(int position) {
        return mMediaItems.get(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent, LayoutInflater inflater, final ImageLoader.ImageCache cache) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.media_item_video, parent, false);
        }

        if (convertView != null && position < mMediaItems.size()) {
            final MediaItem mediaItem = mMediaItems.get(position);
            final Uri imageSource = mediaItem.getPreviewSource();

            final ImageView imageView = (ImageView) convertView.findViewById(R.id.video_view_background);
            if (imageView != null) {
                int width = imageView.getWidth();
                int height = imageView.getHeight();

                if (width <= 0 || height <= 0) {
                    imageView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            int width = imageView.getWidth();
                            int height = imageView.getHeight();
                            setImage(imageSource, cache, imageView, mediaItem, width, height);
                            imageView.getViewTreeObserver().removeOnPreDrawListener(this);
                            return true;
                        }
                    });
                } else {
                    setImage(imageSource, cache, imageView, mediaItem, width, height);
                }
            }
        }

        return convertView;
    }

    private void setImage(Uri imageSource, ImageLoader.ImageCache cache, ImageView imageView, MediaItem mediaItem, int width, int height) {
        if (imageSource != null) {
            Bitmap imageBitmap = null;
            if (cache != null) {
                imageBitmap = cache.getBitmap(imageSource.toString());
            }

            if (imageBitmap == null) {
                imageView.setImageResource(R.drawable.media_item_placeholder);
                MediaUtils.BackgroundFetchThumbnail bgDownload =
                        new MediaUtils.BackgroundFetchThumbnail(imageView,
                                cache,
                                MediaUtils.BackgroundFetchThumbnail.TYPE_VIDEO,
                                width,
                                height,
                                mediaItem.getRotation());
                imageView.setTag(bgDownload);
                bgDownload.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, imageSource);
            } else {
                MediaUtils.fadeInImage(imageView, imageBitmap);
            }
        } else {
            imageView.setTag(null);
            imageView.setImageResource(R.drawable.ic_now_wallpaper_white);
        }
    }

    @Override
    public boolean onMediaItemSelected(MediaItem mediaItem, boolean selected) {
        return !selected;
    }

    private void createMediaItems() {
        final List<String> videoIds = new ArrayList<>();
        final Map<String, String> thumbnailData = getVideoThumbnailData();
        mMediaItems = new ArrayList<>();

        Uri videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        Cursor cursor = MediaStore.Images.Media.query(mContentResolver, videoUri, VIDEO_QUERY_COLUMNS, null,
                null, MediaStore.MediaColumns.DATE_MODIFIED + " DESC");

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    MediaItem newContent = getMediaItemFromCursor(cursor, thumbnailData);

                    if (newContent != null && !videoIds.contains(newContent.getTag())) {
                        mMediaItems.add(newContent);
                        videoIds.add(newContent.getTag());
                    }
                } while (cursor.moveToNext());
            }

            cursor.close();
        }
    }

    private Map<String, String> getVideoThumbnailData() {
        final Map<String, String> data = new HashMap<>();
        Cursor thumbnailCursor = MediaUtils.getDeviceMediaStoreVideos(mContentResolver, THUMBNAIL_QUERY_COLUMNS);

        if (thumbnailCursor != null) {
            if (thumbnailCursor.moveToFirst()) {
                do {
                    int videoIdColumnIndex = thumbnailCursor.getColumnIndex(MediaStore.Video.Media._ID);
                    int thumbnailColumnIndex = thumbnailCursor.getColumnIndex(MediaStore.Video.Media.DATA);

                    if (thumbnailColumnIndex != -1 && videoIdColumnIndex != -1) {
                        data.put(thumbnailCursor.getString(videoIdColumnIndex), thumbnailCursor.getString(thumbnailColumnIndex));
                    }
                } while (thumbnailCursor.moveToNext());
            }

            thumbnailCursor.close();
        }

        return data;
    }

    private MediaItem getMediaItemFromCursor(Cursor videoCursor, Map<String, String> thumbnailData) {
        MediaItem newContent = null;

        int videoIdColumnIndex = videoCursor.getColumnIndex(MediaStore.Video.Media._ID);
        int videoDataColumnIndex = videoCursor.getColumnIndex(MediaStore.Video.Media.DATA);

        if (videoIdColumnIndex != -1) {
            newContent = new MediaItem();
            newContent.setTag(videoCursor.getString(videoIdColumnIndex));
            newContent.setTitle("");

            if (videoDataColumnIndex != -1) {
                newContent.setSource(Uri.parse(videoCursor.getString(videoDataColumnIndex)));
            }
            if (thumbnailData.containsKey(newContent.getTag())) {
                newContent.setPreviewSource(Uri.parse(thumbnailData.get(newContent.getTag())));
            }
        }

        return newContent;
    }

    /**
     * {@link android.os.Parcelable} interface
     */

    public static final Creator<MediaSourceDeviceVideos> CREATOR =
            new Creator<MediaSourceDeviceVideos>() {
                public MediaSourceDeviceVideos createFromParcel(Parcel in) {
                    List<MediaItem> parcelData = new ArrayList<>();
                    in.readTypedList(parcelData, MediaItem.CREATOR);
                    MediaSourceDeviceVideos newItem = new MediaSourceDeviceVideos();

                    if (parcelData.size() > 0) {
                        newItem.setMediaItems(parcelData);
                    }

                    return newItem;
                }

                public MediaSourceDeviceVideos[] newArray(int size) {
                    return new MediaSourceDeviceVideos[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mMediaItems);
    }
}
