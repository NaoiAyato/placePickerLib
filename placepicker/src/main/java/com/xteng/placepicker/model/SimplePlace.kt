package com.xteng.placepicker.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SimplePlace(
        @Json(name = "formatted_address")
        val formattedAddress: String,
        @Json(name = "place_id")
        val placeId: String,
        val types: List<String>
)