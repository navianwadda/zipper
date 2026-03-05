package com.livetvpro.app.utils

import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.SimpleResource
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder
import com.caverock.androidsvg.SVG

class SvgDrawableTranscoder : ResourceTranscoder<SVG, Drawable> {

    override fun transcode(toTranscode: Resource<SVG>, options: Options): Resource<Drawable> {
        val svg = toTranscode.get()
        val picture = svg.renderToPicture()
        val drawable: Drawable = PictureDrawable(picture)
        return SimpleResource(drawable)
    }
}
