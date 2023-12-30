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


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_LOCATION = 1
    }

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

        // send the "o" command to device when the button is clicked.
        btOpenValve.setOnClickListener {
            if (!viewModel.serialWrite("O")) {
                Log.e(TAG, "The 'Open Valve' command was not sent to Arduino");
            }
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
}

