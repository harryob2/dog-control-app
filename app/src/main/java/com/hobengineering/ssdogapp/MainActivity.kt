package com.hobengineering.ssdogapp;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.viewModels;
import androidx.appcompat.app.AppCompatActivity;
import dagger.hilt.android.AndroidEntryPoint;
import com.hobengineering.ssdogapp.extensions.scrollToLastLine;
import com.hobengineering.ssdogapp.viewmodel.MainActivityViewModel;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.Toast


import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.*
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration
import com.stripe.stripeterminal.log.LogLevel
import com.stripe.stripeterminal.external.models.*
import retrofit2.Call
import retrofit2.Response
import java.lang.ref.WeakReference



@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // Register the permissions callback to handles the response to the system permissions dialog.
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        ::onPermissionResult
    )

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_LOCATION = 1

        private val paymentIntentParams =
            PaymentIntentParameters.Builder(listOf(PaymentMethodType.CARD_PRESENT))
                .setAmount(500)
                .setCurrency("eur")
                .build()

        private val discoveryConfig =
            DiscoveryConfiguration.BluetoothDiscoveryConfiguration(isSimulated = false)
        /*** Payment processing callbacks ***/

        // (Step 1 found below in the startPayment function)
        // Step 2 - once we've created the payment intent, it's time to read the card
        private val createPaymentIntentCallback by lazy {
            object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    Terminal.getInstance()
                        .collectPaymentMethod(paymentIntent, collectPaymentMethodCallback)
                }

                override fun onFailure(e: TerminalException) {
                    // Update UI w/ failure
                }
            }
        }

        // Step 3 - we've collected the payment method, so it's time to confirm the payment
        private val collectPaymentMethodCallback by lazy {
            object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    Terminal.getInstance().confirmPaymentIntent(paymentIntent, confirmPaymentIntentCallback)
                }

                override fun onFailure(e: TerminalException) {
                    // Update UI w/ failure
                }
            }
        }

        // Step 4 - we've confirmed the payment! Show a success screen
        private val confirmPaymentIntentCallback by lazy {
            object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    paymentIntent.id?.let { ApiClient.capturePaymentIntent(it) }
                }

                override fun onFailure(e: TerminalException) {
                    // Update UI w/ failure
                }
            }
        }
    }

    private val readerClickListener = ReaderClickListener(WeakReference(this))
    private val readerAdapter = ReaderAdapter(readerClickListener)

    private val viewModel by viewModels<MainActivityViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Permission check for ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val permissions = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_LOCATION)
        }

        val tvOutput = findViewById<TextView>(R.id.tvOutput);
        val btOpenValve = findViewById<Button>(R.id.btOpenValve); // Assuming you have a button with this ID in your layout

        // make the text view scrollable:
        tvOutput.movementMethod = ScrollingMovementMethod();

        // open the device and port when the permission is granted by user.
        viewModel.getGrantedDevice().observe(this) { device ->
            viewModel.openDeviceAndPort(device);
        }

        viewModel.getLiveOutput().observe(this) {
            val spannable = SpannableString(it.text);
            spannable.setSpan(
                it.getAppearance(this),
                0,
                it.text.length,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        viewModel.output.observe(this) {
            tvOutput.apply {
                text = it;
                scrollToLastLine();
            }
        }

        btOpenValve.setOnClickListener {
            if (Terminal.getInstance().connectedReader == null) {
                // Prompt the user to connect the reader if it's not connected.
                Toast.makeText(this, "Please connect the reader first.", Toast.LENGTH_SHORT).show()
            } else {
//                startPaymentProcess(500) // 500 cents = €5
            }
        }



        if (BluetoothAdapter.getDefaultAdapter()?.isEnabled == false) {
            BluetoothAdapter.getDefaultAdapter().enable()
        }

        findViewById<RecyclerView>(R.id.reader_recycler_view).apply {
            adapter = readerAdapter
        }

        findViewById<View>(R.id.discover_button).setOnClickListener {
            discoverReaders()
        }

        findViewById<View>(R.id.collect_payment_button).setOnClickListener {
            startPayment()
        }

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.actionConnect -> {
                viewModel.askForConnectionPermission();
                true;
            }
            R.id.actionDisconnect -> {
                viewModel.disconnect();
                true;
            }
            else -> super.onOptionsItemSelected(item);
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater;
        inflater.inflate(R.menu.activity_main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_LOCATION && grantResults.isNotEmpty()
            && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            throw RuntimeException("Location services are required in order to " + "connect to a reader.")
        }
    }

    override fun onResume() {
        super.onResume()
        requestPermissionsIfNecessary()
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionsIfNecessary() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissionsIfNecessarySdk31()
        } else {
            requestPermissionsIfNecessarySdkBelow31()
        }
    }

    private fun requestPermissionsIfNecessarySdkBelow31() {
        // Check for location permissions
        if (!isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            // If we don't have them yet, request them before doing anything else
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        } else if (!Terminal.isInitialized() && verifyGpsEnabled()) {
            initialize()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestPermissionsIfNecessarySdk31() {
        // Check for location and bluetooth permissions
        val deniedPermissions = mutableListOf<String>().apply {
            if (!isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (!isGranted(Manifest.permission.BLUETOOTH_CONNECT)) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (!isGranted(Manifest.permission.BLUETOOTH_SCAN)) add(Manifest.permission.BLUETOOTH_SCAN)
        }.toTypedArray()

        if (deniedPermissions.isNotEmpty()) {
            // If we don't have them yet, request them before doing anything else
            requestPermissionLauncher.launch(deniedPermissions)
        } else if (!Terminal.isInitialized() && verifyGpsEnabled()) {
            initialize()
        }
    }

    /**
     * Receive the result of our permissions check, and initialize if we can
     */
    private fun onPermissionResult(result: Map<String, Boolean>) {
        val deniedPermissions: List<String> = result
            .filter { !it.value }
            .map { it.key }

        // If we receive a response to our permission check, initialize
        if (deniedPermissions.isEmpty() && !Terminal.isInitialized() && verifyGpsEnabled()) {
            initialize()
        }
    }

    fun updateReaderConnection(isConnected: Boolean) {
        val recyclerView = findViewById<RecyclerView>(R.id.reader_recycler_view)
        findViewById<View>(R.id.collect_payment_button).visibility =
            if (isConnected) View.VISIBLE else View.INVISIBLE
        findViewById<View>(R.id.discover_button).visibility =
            if (isConnected) View.INVISIBLE else View.VISIBLE
        recyclerView.visibility = if (isConnected) View.INVISIBLE else View.VISIBLE

        if (!isConnected) {
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = readerAdapter
        }
    }

    private fun initialize() {
        // Initialize the Terminal as soon as possible
        try {
            Terminal.initTerminal(
                applicationContext, LogLevel.VERBOSE, TokenProvider(), TerminalEventListener()
            )
        } catch (e: TerminalException) {
            throw RuntimeException(
                "Location services are required in order to initialize " +
                        "the Terminal.",
                e
            )
        }

        val isConnectedToReader = Terminal.getInstance().connectedReader != null
        updateReaderConnection(isConnectedToReader)
    }

    private fun discoverReaders() {
        val discoveryCallback = object : Callback {
            override fun onSuccess() {
                // Update your UI
            }

            override fun onFailure(e: TerminalException) {
                // Update your UI
            }
        }


        val discoveryListener = object : DiscoveryListener {
            override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                runOnUiThread {
                    readerAdapter.updateReaders(readers)
                }
            }
        }

        Terminal.getInstance().discoverReaders(discoveryConfig, discoveryListener, discoveryCallback)
    }

    private fun startPayment() {
        // Step 1: create payment intent
        Terminal.getInstance().createPaymentIntent(paymentIntentParams, createPaymentIntentCallback)
    }

    private fun verifyGpsEnabled(): Boolean {
        val locationManager: LocationManager? =
            applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        var gpsEnabled = false

        try {
            gpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
        } catch (exception: Exception) {}

        if (!gpsEnabled) {
            // notify user
            AlertDialog.Builder(ContextThemeWrapper(this, R.style.Theme_MaterialComponents_DayNight_DarkActionBar))
                .setMessage("Please enable location services")
                .setCancelable(false)
                .setPositiveButton("Open location settings") { param, paramInt ->
                    this.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .create()
                .show()
        }

        return gpsEnabled
    }


}

