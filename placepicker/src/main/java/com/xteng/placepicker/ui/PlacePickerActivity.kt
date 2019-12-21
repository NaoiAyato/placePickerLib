package com.xteng.placepicker.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.google.maps.android.SphericalUtil
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.single.BasePermissionListener
import com.xteng.placepicker.PlacePicker
import com.xteng.placepicker.R
import com.xteng.placepicker.helper.PermissionsHelper
import com.xteng.placepicker.inject.KoinComponent
import com.xteng.placepicker.viewmodel.PlacePickerViewModel
import com.xteng.placepicker.viewmodel.Resource
import kotlinx.android.synthetic.main.activity_place_picker.*
import org.koin.android.viewmodel.ext.android.viewModel
import kotlin.math.abs


class PlacePickerActivity : AppCompatActivity(), KoinComponent,
    OnMapReadyCallback,
    GoogleMap.OnMarkerClickListener,
    PlaceConfirmDialogFragment.OnPlaceConfirmedListener {


    companion object {

        private const val STATE_CAMERA_POSITION = "state_camera_position"
        private const val STATE_LOCATION = "state_location"

        private const val AUTOCOMPLETE_REQUEST_CODE = 1001

        private const val DIALOG_CONFIRM_PLACE_TAG = "dialog_place_confirm"
    }

    private var googleMap: GoogleMap? = null

    private var isLocationPermissionGranted = false

    private var cameraPosition: CameraPosition? = null

    private val defaultLocation = LatLng(37.4219999, -122.0862462)

    private var defaultZoom = -1f

    private var lastKnownLocation: LatLng? = null

    private var maxLocationRetries: Int = 3

    private var placeAdapter: PlacePickerAdapter? = null

    private val viewModel: PlacePickerViewModel by viewModel()

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_place_picker)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        lastKnownLocation = savedInstanceState
            ?.getParcelable(STATE_LOCATION) ?: lastKnownLocation
        cameraPosition = savedInstanceState
            ?.getParcelable(STATE_CAMERA_POSITION) ?: cameraPosition

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        defaultZoom = resources.getInteger(R.integer.default_zoom).toFloat()

        initializeUi()

        restoreFragments()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if ((requestCode == AUTOCOMPLETE_REQUEST_CODE) && (resultCode == Activity.RESULT_OK)) {
            data?.run {
                val place = Autocomplete.getPlaceFromIntent(this)
                showConfirmPlacePopup(place)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_place_picker, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (android.R.id.home == item.itemId) {
            finish()
            return true
        }

        if (R.id.action_search == item.itemId) {
            requestPlacesSearch()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_CAMERA_POSITION, googleMap?.cameraPosition)
        outState.putParcelable(STATE_LOCATION, lastKnownLocation)
    }

    override fun onMapReady(map: GoogleMap?) {
        googleMap = map
        map?.setOnMarkerClickListener(this)
        checkForPermission()
    }

    override fun onMarkerClick(marker: Marker): Boolean {

        val place = marker.tag as Place
        showConfirmPlacePopup(place)

        return false
    }

    override fun onPlaceConfirmed(place: Place) {
        val data = Intent()
        data.putExtra(PlacePicker.EXTRA_PLACE, place)
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private fun bindPlaces(places: List<Place>) {

        if (placeAdapter == null) {
            placeAdapter = PlacePickerAdapter(places) { showConfirmPlacePopup(it) }
        } else {
            placeAdapter?.swapData(places)
        }

        rvNearbyPlaces.adapter = placeAdapter

        for (place in places) {

            val marker: Marker? = googleMap?.addMarker(
                MarkerOptions()
                    .position(place.latLng!!)
                    .icon(getPlaceMarkerBitmap(place))
            )

            marker?.tag = place
        }
    }

    private fun checkForPermission() {

        PermissionsHelper.checkForLocationPermission(this, object : BasePermissionListener() {

            override fun onPermissionDenied(response: PermissionDeniedResponse?) {
                isLocationPermissionGranted = false
                initMap()
            }

            override fun onPermissionGranted(response: PermissionGrantedResponse?) {
                isLocationPermissionGranted = true
                initMap()
            }
        })
    }

    private fun getCurrentLatLngBounds(): LatLngBounds {

        val radius = resources.getInteger(R.integer.autocomplete_search_bias_radius).toDouble()
        val location: LatLng = lastKnownLocation ?: defaultLocation

        val northEast: LatLng = SphericalUtil.computeOffset(location, radius, 45.0)
        val southWest: LatLng = SphericalUtil.computeOffset(location, radius, 225.0)

        return LatLngBounds(southWest, northEast)
    }

    private fun getDeviceLocation(animate: Boolean) {

        //获取设备的最佳和最新位置，在位置不可用的情况下，这种情况可能很少。

        try {
            val locationResult = fusedLocationProviderClient.lastLocation
            locationResult
                ?.addOnFailureListener(this) { setDefaultLocation() }
                ?.addOnSuccessListener(this) { location: Location? ->

                    // 在极少数情况下，位置可能为空...
                    if (location == null) {
                        if (maxLocationRetries > 0) {
                            maxLocationRetries--
                            Handler().postDelayed({ getDeviceLocation(animate) }, 1000)
                        } else {
                            // 位置不可用。放弃...
                            setDefaultLocation()
                            Snackbar.make(
                                coordinator,
                                R.string.picker_location_unavailable,
                                Snackbar.LENGTH_INDEFINITE
                            )
                                .setAction(R.string.places_try_again) {
                                    getDeviceLocation(animate)
                                }
                                .show()
                        }
                        return@addOnSuccessListener
                    }

                    // 将地图的摄像机位置设置为设备的当前位置。
                    lastKnownLocation = LatLng(location.latitude, location.longitude)

                    val update = CameraUpdateFactory
                        .newLatLngZoom(lastKnownLocation, defaultZoom)

                    if (animate) {
                        googleMap?.animateCamera(update)
                    } else {
                        googleMap?.moveCamera(update)
                    }

                    // 加载此位置附近的位置
                    loadNearbyPlaces()
                }
        } catch (e: SecurityException) {
        }
    }

    @Suppress("DEPRECATION")
    private fun getPlaceMarkerBitmap(place: Place): BitmapDescriptor {

        val innerIconSize: Int = resources.getDimensionPixelSize(R.dimen.marker_inner_icon_size)

        val bgDrawable = ResourcesCompat.getDrawable(
            resources,
            R.drawable.ic_map_marker_solid_red_32dp, null
        )!!

        val fgDrawable = ResourcesCompat.getDrawable(
            resources,
            UiUtils.getPlaceDrawableRes(this, place), null
        )!!
        DrawableCompat.setTint(fgDrawable, ContextCompat.getColor(this,R.color.colorMarkerInnerIcon))

        val bitmap = Bitmap.createBitmap(
            bgDrawable.intrinsicWidth,
            bgDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)

        bgDrawable.setBounds(0, 0, canvas.width, canvas.height)

        val left = (canvas.width - innerIconSize) / 2
        val top = (canvas.height - innerIconSize) / 3
        val right = left + innerIconSize
        val bottom = top + innerIconSize

        fgDrawable.setBounds(left, top, right, bottom)

        bgDrawable.draw(canvas)
        fgDrawable.draw(canvas)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun handlePlaceByLocation(result: Resource<Place?>) {

        when (result.status) {
            Resource.Status.LOADING -> {
                pbLoading.show()
            }
            Resource.Status.SUCCESS -> {
                result.data?.run { showConfirmPlacePopup(this) }
                pbLoading.hide()
            }
            Resource.Status.ERROR -> {
                toast(R.string.picker_load_this_place_error)
                pbLoading.hide()
            }
        }

    }

    private fun handlePlacesLoaded(result: Resource<List<Place>>) {

        when (result.status) {
            Resource.Status.LOADING -> {
                pbLoading.show()
            }
            Resource.Status.SUCCESS -> {
                bindPlaces((result.data ?: listOf()))
                pbLoading.hide()
            }
            Resource.Status.ERROR -> {
                toast(R.string.picker_load_places_error)
                pbLoading.hide()
            }
        }
    }

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun initializeUi() {

        rvNearbyPlaces.layoutManager = LinearLayoutManager(this)

        btnMyLocation.setOnClickListener { getDeviceLocation(true) }
        cardSearch.setOnClickListener { requestPlacesSearch() }
        ivMarkerSelect.setOnClickListener { selectThisPlace() }
        tvLocationSelect.setOnClickListener { selectThisPlace() }

        // 根据宽度隐藏或显示卡片搜索
        cardSearch.visibility =
            if (resources.getBoolean(R.bool.show_card_search)) View.VISIBLE
            else View.GONE

        // 为工具栏添加漂亮的淡入淡出效果
        appBarLayout.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
                toolbar.alpha = abs(verticalOffset / appBarLayout.totalScrollRange.toFloat())
            })

        // 禁用appBarLayout上的垂直滚动（它与地图混淆......）

        // 设置默认行为
        val appBarLayoutParams = appBarLayout.layoutParams as CoordinatorLayout.LayoutParams
        appBarLayoutParams.behavior = AppBarLayout.Behavior()

        // 禁用拖动
        val behavior = appBarLayoutParams.behavior as AppBarLayout.Behavior
        behavior.setDragCallback(object : AppBarLayout.Behavior.DragCallback() {
            override fun canDrag(appBarLayout: AppBarLayout): Boolean {
                return false
            }
        })

        // 将AppBarLayout的大小设置为总高度的68％
        coordinator.doOnLayout {
            val size: Int = (it.height * 68) / 100
            appBarLayoutParams.height = size
        }
    }

    private fun initMap() {

        // 打开/关闭“我的位置”图层以及地图上的相关控件
        updateLocationUI()

        // 恢复任何已保存状态
        restoreMapState()

        if (isLocationPermissionGranted) {

            if (lastKnownLocation == null) {
                // 获取设备的当前位置并设置地图的位置
                getDeviceLocation(false)
            } else {
                // 使用最后知道的位置将地图指向
                setDefaultLocation()
                loadNearbyPlaces()
            }
        } else {
            setDefaultLocation()
        }
    }

    private fun loadNearbyPlaces() {
        viewModel.getNearbyPlaces(lastKnownLocation ?: defaultLocation)
            .observe(this, Observer { handlePlacesLoaded(it) })
    }

    private fun requestPlacesSearch() {
        if(lastKnownLocation==null){
            return
        }

        // Google不会向这些字段收费
        // https://developers.google.com/places/android-sdk/usage-and-billing#basic-data
        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG,
            Place.Field.TYPES,
            Place.Field.PHOTO_METADATAS
        )

        val rectangularBounds = RectangularBounds.newInstance(getCurrentLatLngBounds())

        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, placeFields)
            .setLocationBias(rectangularBounds)
            .build(this)

        startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
    }

    private fun restoreFragments() {
        val confirmFragment = supportFragmentManager
            .findFragmentByTag(DIALOG_CONFIRM_PLACE_TAG) as PlaceConfirmDialogFragment?
        confirmFragment?.run {
            confirmListener = this@PlacePickerActivity
        }
    }

    private fun restoreMapState() {
        cameraPosition?.run {
            googleMap?.moveCamera(CameraUpdateFactory.newCameraPosition(this))
        }
    }

    private fun selectThisPlace() {
        googleMap?.cameraPosition?.run {
            viewModel.getPlaceByLocation(this.target).observe(this@PlacePickerActivity,
                Observer { handlePlaceByLocation(it) })
        }
    }

    private fun setDefaultLocation() {
        val default: LatLng = lastKnownLocation ?: defaultLocation
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(default, defaultZoom))
    }

    private fun showConfirmPlacePopup(place: Place) {
        val fragment = PlaceConfirmDialogFragment.newInstance(place, this)
        fragment.show(supportFragmentManager, DIALOG_CONFIRM_PLACE_TAG)
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationUI() {

        googleMap?.uiSettings?.isMyLocationButtonEnabled = false

        if (isLocationPermissionGranted) {
            googleMap?.isMyLocationEnabled = true
            btnMyLocation.visibility = View.VISIBLE
        } else {
            btnMyLocation.visibility = View.GONE
            googleMap?.isMyLocationEnabled = false
        }
    }



}
