package com.xteng.placepicker.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.xteng.placepicker.respository.PlaceRepository
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

class PlacePickerViewModel constructor(private var repository: PlaceRepository)
    : BaseViewModel() {

    // 将地点列表保留在此视图模型状态中
    private val placeList: MutableLiveData<Resource<List<Place>>> = MutableLiveData()

    private var lastLocation: LatLng = LatLng(0.0, 0.0)

    fun getNearbyPlaces(location: LatLng): LiveData<Resource<List<Place>>> {

        // 如果我们已经加载了此位置的位置，则返回相同的实时数据
        // 而不是再次提取.
        placeList.value?.let {
            if (lastLocation == location) return placeList
        }

        // 更新上次获取的位置
        lastLocation = location

        val disposable: Disposable = repository.getNearbyPlaces()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { placeList.value = Resource.loading() }
                .subscribe(
                        { result: List<Place> -> placeList.value =
                            Resource.success(result)
                        },
                        { error: Throwable -> placeList.value =
                            Resource.error(error)
                        }
                )

        // 在ViewModel生命周期中跟踪此一次性用法
        addDisposable(disposable)

        return placeList
    }

    fun getPlaceByLocation(location: LatLng): LiveData<Resource<Place?>> {

        val liveData = MutableLiveData<Resource<Place?>>()

        val disposable: Disposable = repository.getPlaceByLocation(location)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { liveData.value = Resource.loading() }
                .subscribe(
                        { result: Place? -> liveData.value =
                            Resource.success(result)
                        },
                        { error: Throwable -> liveData.value =
                            Resource.error(error)
                        }
                )

        // 在ViewModel生命周期中跟踪此一次性用法
        addDisposable(disposable)

        return liveData
    }
}