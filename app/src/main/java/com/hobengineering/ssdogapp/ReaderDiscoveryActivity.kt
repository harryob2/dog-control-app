package com.hobengineering.ssdogapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.Callback
import com.stripe.stripeterminal.external.callable.DiscoveryListener
import com.stripe.stripeterminal.external.callable.ReaderCallback
import com.stripe.stripeterminal.external.models.ConnectionConfiguration
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.TerminalException

class ReaderDiscoveryActivity : AppCompatActivity(), DiscoveryListener {

    private var selectedReader: Reader? = null
    private val locationId = "YOUR_LOCATION_ID_HERE"  // Replace with your location ID from Stripe Dashboard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader_discovery)  // Set your layout resource

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1)
        } else {
            discoverReadersAction()
        }
    }

    private fun discoverReadersAction() {
        val config = DiscoveryConfiguration.UsbDiscoveryConfiguration()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        Terminal.getInstance().discoverReaders(config, this, object : Callback {
            override fun onSuccess() {
                println("discoverReaders succeeded")
                // Update your UI here to reflect that reader discovery has started
            }

            override fun onFailure(e: TerminalException) {
                e.printStackTrace()
                // Update your UI here to reflect the failure
            }
        })
    }

    override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
        // TODO: Implement your mechanism to display readers to the user and allow them to select one.
        // For example, update a list in the UI here.

        // This is where you would set `selectedReader` based on user interaction.
        // For example, you might set it when a user taps on a reader in the list.
    }

    private fun connectToReader() {
        val reader = selectedReader ?: return

        val connectionConfig = ConnectionConfiguration.UsbConnectionConfiguration(locationId)

        Terminal.getInstance().connectUsbReader(reader, connectionConfig, object : ReaderCallback {
            override fun onSuccess(reader: Reader) {
                println("Successfully connected to reader")
                // Update your UI here to reflect that the reader is connected
            }

            override fun onFailure(e: TerminalException) {
                e.printStackTrace()
                // Update your UI here to reflect the failure
            }
        })
    }

    // Call this method when the user selects a reader from the list
    fun onReaderSelected(reader: Reader) {
        selectedReader = reader
        connectToReader()
    }
}
