package com.livetvpro.app.utils

import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target

object GlideExtensions {

    fun loadImage(
        imageView: ImageView,
        url: String?,
        placeholderResId: Int? = null,
        errorResId: Int? = null,
        isCircular: Boolean = false
    ) {
        if (url.isNullOrEmpty()) {
            if (placeholderResId != null) imageView.setImageResource(placeholderResId)
            else imageView.setImageDrawable(null)
            return
        }

        val isSvg = url.contains(".svg", ignoreCase = true)

        if (isSvg) {
            val request = Glide.with(imageView.context)
                .`as`(Drawable::class.java)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        if (errorResId != null) imageView.setImageResource(errorResId)
                        return true
                    }
                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        return false
                    }
                })

            if (isCircular) {
                request.apply(RequestOptions.bitmapTransform(CircleCrop())).into(imageView)
            } else {
                request.into(imageView)
            }
        } else {
            var options = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)

            if (placeholderResId != null) options = options.placeholder(placeholderResId)
            if (errorResId != null) options = options.error(errorResId)
            if (isCircular) options = options.transform(CircleCrop())

            Glide.with(imageView.context)
                .load(url)
                .apply(options)
                .into(imageView)
        }
    }
}
