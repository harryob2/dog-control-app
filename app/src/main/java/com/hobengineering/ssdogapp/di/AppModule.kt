package com.hobengineering.ssdogapp.di

import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.hobengineering.ssdogapp.arduino.ArduinoHelper
import com.hobengineering.ssdogapp.arduino.ArduinoPermissionBroadcastReceiver
import com.hobengineering.ssdogapp.arduino.ArduinoSerialReceiver

/**
 * Created by Ali Kabiri on 13.04.20.
 */
@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @Provides
    fun provideContext(
        app: Application
    ): Context = app.applicationContext

    @Provides
    fun provideArduinoPermissionBroadcastReceiver() = ArduinoPermissionBroadcastReceiver()

    @Provides
    fun providesArduinoSerialReceiver() = ArduinoSerialReceiver()

    @Provides
    fun providesArduinoHelper(
        context: Context,
        arduinoPermReceiver: ArduinoPermissionBroadcastReceiver,
        arduinoSerialReceiver: ArduinoSerialReceiver,
    ): ArduinoHelper {
        return ArduinoHelper(
            context = context,
            arduinoPermReceiver = arduinoPermReceiver,
            arduinoSerialReceiver = arduinoSerialReceiver,
        )
    }
}