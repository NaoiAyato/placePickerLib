package com.xteng.placepicker.respository.googlemaps

import com.xteng.placepicker.model.GeocodeResult
import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleMapsAPI {

    @GET("geocode/json")
    fun findByLocation(@Query("latlng") location: String,
                       @Query("key") apiKey: String)
            : Single<GeocodeResult>
}