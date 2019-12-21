package com.xteng.placepicker

import android.app.Activity
import android.app.Application
import android.content.Intent
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.libraries.places.api.model.Place
import com.xteng.placepicker.inject.KoinContext
import com.xteng.placepicker.inject.repositoryModule
import com.xteng.placepicker.inject.viewModelModule
import com.xteng.placepicker.model.PlaceModel
import com.xteng.placepicker.ui.PlacePickerActivity
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.dsl.koinApplication

class PlacePicker private constructor() {

    class IntentBuilder {

        private val intent = Intent()

        /**
         * 此密钥将用于附近对Google Places API的所有请求
         */
        fun setAndroidApiKey(androidKey: String): IntentBuilder {
            androidApiKey = androidKey
            return this
        }

        /**
         * 此密钥将用于对Google Maps API的所有反向地理编码请求
         */
        @Deprecated("This function will be removed in a future release.",
            ReplaceWith("setGeocodingApiKey(geoKey)"))
        fun setGeolocationApiKey(geoKey: String): IntentBuilder {
            return setGeocodingApiKey(geoKey)
        }

        /**
         * 此密钥将用于对Google Maps API的所有反向地理编码请求.
         */
        fun setGeocodingApiKey(geoKey: String): IntentBuilder {
            geoLocationApiKey = geoKey
            return this
        }

        @Throws(GooglePlayServicesNotAvailableException::class)
        fun build(activity: Activity): Intent {

            initKoin(activity.application)

            val result: Int =
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)

            if (ConnectionResult.SUCCESS != result) {
                throw GooglePlayServicesNotAvailableException(result)
            }

            intent.setClass(activity, PlacePickerActivity::class.java)
            return intent
        }

        /**
         * 通过传递当前应用程序上下文来初始化依赖注入框架
         */
        private fun initKoin(application: Application) {
            KoinContext.koinApp = koinApplication {
                androidLogger()
                androidContext(application)
                modules(
                    repositoryModule,
                    viewModelModule
                )
            }
        }
    }

    companion object {

        const val EXTRA_PLACE = "extra_place"

        var androidApiKey: String = ""
        var geoLocationApiKey: String = ""

        fun getPlace(intent: Intent): Place? {
            return intent.getParcelableExtra(EXTRA_PLACE)
        }

        fun getPlaceModel(intent:Intent):PlaceModel?{
            try {
                val place=intent.getParcelableExtra(EXTRA_PLACE) as Place?
                return if(place==null){
                    null
                }else{
                    PlaceModel(place.name,place.address,place.latLng?.latitude,place.latLng?.longitude)
                }
            }catch (e:Exception){
                e.printStackTrace()
            }
            return null
        }
    }
}