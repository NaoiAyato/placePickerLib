package com.xteng.placepicker.ui

import android.content.Context
import com.google.android.libraries.places.api.model.Place
import com.xteng.placepicker.R


object UiUtils {

    /**
     * 根据类型获取可绘制资源的位置
     */
    fun getPlaceDrawableRes(context: Context, place: Place): Int {

        val defType = "drawable"
        val defPackage = context.packageName

        place.types?.let {
            for (type: Place.Type in it) {
                val name = type.name.toLowerCase()
                val id: Int = context.resources
                        .getIdentifier("ic_places_$name", defType, defPackage)
                if (id > 0) return id
            }
        }
        // 默认
        return R.drawable.ic_map_marker_black_24dp
    }
}