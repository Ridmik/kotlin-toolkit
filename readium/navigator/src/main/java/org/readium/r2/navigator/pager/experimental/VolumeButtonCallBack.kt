package org.readium.r2.navigator.pager.experimental

public interface VolumeButtonCallBack {
    fun onVolumeUp()
    fun onVolumeDown()
}

public interface VolumeButtonControllable {
    fun setVolumeButtonCallBack(callBack: VolumeButtonCallBack?)
}