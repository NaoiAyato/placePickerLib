package com.xteng.placepicker.respository

import android.graphics.Bitmap
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import io.reactivex.Single

/**
 * 我们决定连接Places存储库，因为有很多空间可以改善场所搜索和检索。
 * 我们可以使用不同的存储库从缓存数据库本地获取位置，或者从Google以外的其他提供程序获取位置
 */
interface PlaceRepository {

    fun getNearbyPlaces(): Single<List<Place>>

    fun getPlacePhoto(photoMetadata: PhotoMetadata): Single<Bitmap>

    fun getPlaceByLocation(location: LatLng): Single<Place?>
}