package com.hobengineering.ssdogapp;


import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.SpannableString
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hobengineering.ssdogapp.extensions.scrollToLastLine
import com.hobengineering.ssdogapp.viewmodel.MainActivityViewModel
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.*
import com.stripe.stripeterminal.external.models.*
import com.stripe.stripeterminal.log.LogLevel
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback
import com.stripe.stripeterminal.external.models.PaymentIntent
import com.stripe.stripeterminal.external.models.TerminalException
import dagger.hilt.android.AndroidEntryPoint
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import java.util.Locale
import retrofit2.Callback as RetrofitCallback
import retrofit2.Response
import retrofit2.Call
import android.os.Build
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch



@AndroidEntryPoint
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var btPayForDogWash: Button
    private lateinit var tvCountdown: TextView

//     Register the permissions callback to handles the response to the system permissions dialog. t
//    private val requestPermissionLauncher = registerForActivityResult(
//        ActivityResultContracts.RequestMultiplePermissions(),
//        ::onPermissionResult
//    )

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_LOCATION = 1
        private const val REQUEST_CODE_BLUETOOTH_PERMISSIONS = 102

//        private val paymentIntentParams =
//            PaymentIntentParameters.Builder(listOf(PaymentMethodType.CARD_PRESENT))
//                .setAmount(50)
//                .setCurrency("eur")
//                .build()

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

        // Permission check for Bluetooth
        requestBluetoothPermissions()

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

    // Function to request Bluetooth permissions
    private fun requestBluetoothPermissions() {
        // Check for Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Permissions required for Bluetooth scanning and connecting on Android 12 and above
            val requiredPermissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )

            // Check if permissions are granted
            val allPermissionsGranted = requiredPermissions.all { permission ->
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }

            if (!allPermissionsGranted) {
                // Request permissions
                ActivityCompat.requestPermissions(this, requiredPermissions, REQUEST_CODE_BLUETOOTH_PERMISSIONS)
            }
        } else {
            // For Android versions below Android 12, ACCESS_FINE_LOCATION is typically required for Bluetooth operations
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE_LOCATION)
            }
        }
    }



    // Assume this is called in response to a user action, like clicking a button
    private fun createPaymentIntent() {
        ApiClient.createPaymentIntent(51, "eur", object : RetrofitCallback<PaymentIntentResponse> {
            override fun onResponse(call: Call<PaymentIntentResponse>, response: Response<PaymentIntentResponse>) {
                if (response.isSuccessful) {
                    val clientSecret = response.body()?.clientSecret
                    if (clientSecret != null) {
                        proceedWithPaymentFlow(clientSecret)
                    }

                    // Use the clientSecret here to proceed with Stripe payment flow
                } else {
                    Log.e(TAG, "Failed to create payment intent: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<PaymentIntentResponse>, t: Throwable) {
                Log.e(TAG, "Error creating payment intent", t)
            }
        })
    }

    private fun proceedWithPaymentFlow(clientSecret: String) {
        Terminal.getInstance().retrievePaymentIntent(clientSecret, object : PaymentIntentCallback {
            override fun onSuccess(paymentIntent: PaymentIntent) {
                // At this point, you have successfully retrieved the PaymentIntent
                // Now, you can proceed to collect a payment method with this paymentIntent
                collectPaymentMethod(paymentIntent)
            }

            override fun onFailure(exception: TerminalException) {
                // Handle the failure to retrieve the PaymentIntent
                Log.e(TAG, "Failed to retrieve PaymentIntent: ${exception.errorMessage}")
            }
        })
    }

    private fun collectPaymentMethod(paymentIntent: PaymentIntent) {
        // Use the Terminal SDK to collect a payment method for the retrieved PaymentIntent
        // This step involves customer interaction with the payment hardware to insert/swipe/tap their card
        Terminal.getInstance().collectPaymentMethod(paymentIntent, object : PaymentIntentCallback {
            override fun onSuccess(paymentIntent: PaymentIntent) {
                // Successfully collected a payment method
                // You can now proceed to confirm the PaymentIntent to complete the payment
                confirmPaymentIntent(paymentIntent)
            }

            override fun onFailure(exception: TerminalException) {
                // Handle the failure to collect a payment method
                Log.e(TAG, "Failed to collect payment method: ${exception.errorMessage}")
            }
        })
    }

    private fun confirmPaymentIntent(paymentIntent: PaymentIntent) {
        // Use the Terminal SDK to confirm the PaymentIntent and complete the payment
        Terminal.getInstance().confirmPaymentIntent(paymentIntent, object : PaymentIntentCallback {
            override fun onSuccess(paymentIntent: PaymentIntent) {
                // Payment was successfully confirmed and completed
                Log.d(TAG, "PaymentIntent confirmed successfully: ${paymentIntent.id}")
                runOnUiThread {
                    // Send serial command to arduino
                    connectArduinoAndSendSerialCommand()
                    // UI updates after confirming payment
                    updateUIAfterPaymentConfirmation()
                }
            }

            override fun onFailure(exception: TerminalException) {
                // Handle the failure to confirm the PaymentIntent
                Log.e(TAG, "Failed to confirm PaymentIntent: ${exception.errorMessage}")
            }
        })
    }

    private fun connectArduinoAndSendSerialCommand() {
        viewModel.askForConnectionPermission()

        // Observe the granted device LiveData
        viewModel.getGrantedDevice().observe(this@MainActivity) { device ->
            // Ensure device connection before sending the serial command
            // Launch a coroutine in the lifecycleScope of the Activity or Fragment
            lifecycleScope.launch {
                // Wait for the openDeviceAndPort Job to complete
                viewModel.openDeviceAndPort(device).join()

                // After completion, perform the serial write operation
//                        val success = viewModel.serialWrite("O")
                if (!viewModel.serialWrite("O")) {
                    Log.e(TAG, "The 'Open Valve' command was not sent to Arduino")
                } else {
                    // The serial command was successfully sent, proceed with UI updates or other logic as needed
                    Log.e(TAG, "O command successfully sent apparently")
                }
            }
        }

    }

    private fun updateUIAfterPaymentConfirmation() {
        val btPayForDogWash = findViewById<Button>(R.id.btPayForDogWash)
        val tvCountdown = findViewById<TextView>(R.id.tvCountdown)

        btPayForDogWash.visibility = View.INVISIBLE // Hide the button
        tvCountdown.visibility = View.VISIBLE // Show the countdown

        val successMessage = "Payment successful. You have 20 minutes of water."
        speak(successMessage)
        Toast.makeText(applicationContext, successMessage, Toast.LENGTH_LONG).show()

        startCountdown(tvCountdown, 20 * 60 * 1000) // 20 minutes in milliseconds
//        startCountdown(tvCountdown, 10000) // 10 seconds in milliseconds

    }



    private fun startPayment() {
        // Step 1: create payment intent
//        Terminal.getInstance().createPaymentIntent(paymentIntentParams, createPaymentIntentCallback)
        createPaymentIntent()
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
        tvCountdown.textSize = 144f // Sets the font size to 48sp

        object : CountDownTimer(millisInFuture, 1000) { // Update every second
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = millisUntilFinished / 1000 % 60
                tvCountdown.text = String.format("%02d:%02d", minutes, seconds)

                // Milestone notifications
                when (minutes) {
                    15L, 10L, 5L, 2L -> {
                        if (seconds == 0L) { // To ensure it only triggers once at exactly 1 minute left
                            val message = "$minutes minutes remaining."
                            speak(message)
                            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                    1L -> if (seconds == 0L) {
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
                Toast.makeText(applicationContext, finishMessage, Toast.LENGTH_LONG).show() // Show little pop up message
                runOnUiThread {
                    val btPayForDogWash = findViewById<Button>(R.id.btPayForDogWash)
                    btPayForDogWash.visibility = View.VISIBLE // Show the button
                    tvCountdown.visibility = View.INVISIBLE // Hide the countdown
                }
            }
        }.start()
    }

}
