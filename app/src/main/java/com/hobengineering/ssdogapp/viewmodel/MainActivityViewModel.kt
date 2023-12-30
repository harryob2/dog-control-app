package com.hobengineering.ssdogapp.viewmodel

import android.hardware.usb.UsbDevice
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import com.hobengineering.ssdogapp.arduino.ArduinoHelper
import com.hobengineering.ssdogapp.model.OutputText
import javax.inject.Inject
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap



/**
 * Created by Ali Kabiri on 12.04.20.
 */
@HiltViewModel
class MainActivityViewModel
@Inject constructor(
    private val arduinoHelper: ArduinoHelper,
): ViewModel() {

    private val _outputLive = MutableLiveData("")
    val output = _outputLive

    fun askForConnectionPermission() = arduinoHelper.askForConnectionPermission()

    fun disconnect() = arduinoHelper.disconnect()
    fun getGrantedDevice() = arduinoHelper.getGrantedDevice()
    fun openDeviceAndPort(device: UsbDevice) = viewModelScope.launch {
        arduinoHelper.openDeviceAndPort(device)
    }
    fun serialWrite(command: String): Boolean {
        _outputLive.value = "${output.value}\n$command\n"
        return arduinoHelper.serialWrite(command)
    }

    /**
     * Transforms the outputs from ArduinoHelper into spannable text
     * and merges them in one single live data.
     */
    fun getLiveOutput(): LiveData<OutputText> {

        val liveOutput = arduinoHelper.getLiveOutput()
        val liveInfoOutput = arduinoHelper.getLiveInfoOutput()
        val liveErrorOutput = arduinoHelper.getLiveErrorOutput()

        val liveSpannedOutput: LiveData<OutputText> = liveOutput.map {
            _outputLive.value = _outputLive.value + it
            OutputText(it, OutputText.OutputType.TYPE_NORMAL)
        }

        val liveSpannedInfoOutput: LiveData<OutputText> = liveInfoOutput.map {
            _outputLive.value = _outputLive.value + it
            OutputText(it, OutputText.OutputType.TYPE_INFO)
        }

        val liveSpannedErrorOutput: LiveData<OutputText> = liveErrorOutput.map {
            _outputLive.value = _outputLive.value + it
            OutputText(it, OutputText.OutputType.TYPE_ERROR)
        }


        val liveDataMerger = MediatorLiveData<OutputText>()
        liveDataMerger.addSource(liveSpannedOutput) { liveDataMerger.value = it }
        liveDataMerger.addSource(liveSpannedInfoOutput) { liveDataMerger.value = it }
        liveDataMerger.addSource(liveSpannedErrorOutput) { liveDataMerger.value = it }

        return liveDataMerger
    }
}