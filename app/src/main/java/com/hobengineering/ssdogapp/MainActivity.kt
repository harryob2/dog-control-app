package com.hobengineering.ssdogapp;


import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hobengineering.ssdogapp.extensions.scrollToLastLine
import com.hobengineering.ssdogapp.viewmodel.MainActivityViewModel
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.*
import com.stripe.stripeterminal.external.models.*
import com.stripe.stripeterminal.external.models.ConnectionConfiguration.BluetoothConnectionConfiguration
import com.stripe.stripeterminal.log.LogLevel
import dagger.hilt.android.AndroidEntryPoint
import java.lang.ref.WeakReference
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import java.util.Locale


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var btPayForDogWash: Button
    private lateinit var tvCountdown: TextView

    // Register the permissions callback to handles the response to the system permissions dialog. t
//    private val requestPermissionLauncher = registerForActivityResult(
//        ActivityResultContracts.RequestMultiplePermissions(),
//        ::onPermissionResult
//    )

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_LOCATION = 1

        private val paymentIntentParams =
            PaymentIntentParameters.Builder(listOf(PaymentMethodType.CARD_PRESENT))
                .setAmount(50)
                .setCurrency("eur")
                .build()

        private val discoveryConfig =
            DiscoveryConfiguration.BluetoothDiscoveryConfiguration(isSimulated = false)
        /*** Payment processing callbacks ***/

    }


    private val viewModel by viewModels<MainActivityViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // initialize
        initialize()


        // Permission check for ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val permissions = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_LOCATION)
        }

        tts = TextToSpeech(this, this)
        val tvOutput = findViewById<TextView>(R.id.tvOutput)
        val btOpenValve = findViewById<Button>(R.id.btOpenValve)
        val btPayForDogWash = findViewById<Button>(R.id.btPayForDogWash)




        // make the text view scrollable:
//        tvOutput.movementMethod = ScrollingMovementMethod();

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

        btPayForDogWash.setOnClickListener {
            if (Terminal.getInstance().connectedReader == null) {
                // No reader is connected, start discovery and then payment
                discoverAndConnectReader()
            } else {
                // Reader already connected, start payment directly
                startPayment()
            }
        }

        if (BluetoothAdapter.getDefaultAdapter()?.isEnabled == false) {
            BluetoothAdapter.getDefaultAdapter().enable()
        }

    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.UK
            tts.setSpeechRate(1f)
            // You can also check if the language data is missing or the language is not supported.
        } else {
            Log.e(TAG, "Initialization of TextToSpeech failed.")
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
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

    }

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
                runOnUiThread {
                    val btPayForDogWash = findViewById<Button>(R.id.btPayForDogWash)
                    val tvCountdown = findViewById<TextView>(R.id.tvCountdown)

                    btPayForDogWash.visibility = View.GONE // Hide the button
                    tvCountdown.visibility = View.VISIBLE // Show the countdown

                    val successMessage = "Payment successful. You have 20 minutes of water."
                    speak(successMessage)
                    Toast.makeText(applicationContext, successMessage, Toast.LENGTH_LONG).show()

                    startCountdown(tvCountdown, 20 * 60 * 1000) // 20 minutes in milliseconds
                }
            }

            override fun onFailure(e: TerminalException) {
                // Update UI w/ failure
            }
        }
    }

    private fun startPayment() {
        // Step 1: create payment intent
        Terminal.getInstance().createPaymentIntent(paymentIntentParams, createPaymentIntentCallback)
    }

    private fun discoverAndConnectReader() {
        // Discover readers
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        Terminal.getInstance().discoverReaders(discoveryConfig, object : DiscoveryListener {
            override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                if (readers.isNotEmpty()) {
                    // Automatically connect to the first discovered reader
                    connectToReader(readers.first())
                }
            }
        }, object : Callback {
            override fun onSuccess() {
                Log.d(TAG, "Reader discovery started")
            }

            override fun onFailure(e: TerminalException) {
                Log.e(TAG, "Reader discovery failed: ${e.errorMessage}")
            }
        })
    }

    private fun connectToReader(reader: Reader) {
        println("Attempting to connect to reader: ${reader.serialNumber}")

        val connectionConfig = ConnectionConfiguration.BluetoothConnectionConfiguration("tml_Fb4Gcg8m8jPGlE")

        val readerCallback = object : ReaderCallback {
            override fun onSuccess(reader: Reader) {
                println("Successfully connected to reader: ${reader.serialNumber}")
                runOnUiThread {
                    // Initiate payment after successful connection
                    startPayment()
                }
            }

            override fun onFailure(e: TerminalException) {
                println("Failed to connect to reader: ${e.localizedMessage}")
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed to connect to reader", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (connectionConfig != null) {
            Terminal.getInstance().connectBluetoothReader(
                reader, connectionConfig, TerminalBluetoothReaderListener(), readerCallback
            )
        }
    }

    fun startCountdown(tvCountdown: TextView, millisInFuture: Long) {
        // Triple the font size for the countdown timer
        val originalTextSize = tvCountdown.textSize
        tvCountdown.textSize = originalTextSize * 3

        object : CountDownTimer(millisInFuture, 1000) { // Update every second
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = millisUntilFinished / 1000 % 60
                tvCountdown.text = String.format("%02d:%02d", minutes, seconds)

                // Milestone notifications
                when (minutes) {
                    15L, 10L, 5L, 2L -> {
                        val message = "$minutes minutes remaining."
                        speak(message)
                        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                    }
                    1L -> if (seconds == 0L) { // To ensure it only triggers once at exactly 1 minute left
                        val message = "1 minute remaining."
                        speak(message)
                        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFinish() {
                tvCountdown.text = "00:00"
                val finishMessage = "Time is up. You can now dry your dog."
                speak(finishMessage)
                Toast.makeText(applicationContext, finishMessage, Toast.LENGTH_LONG).show()
            }
        }.start()
    }



}