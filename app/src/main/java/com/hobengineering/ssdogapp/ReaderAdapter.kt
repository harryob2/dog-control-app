package com.hobengineering.ssdogapp

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.ReaderCallback
import com.stripe.stripeterminal.external.models.ConnectionConfiguration
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.TerminalException
import java.lang.ref.WeakReference

// A simple [RecyclerView.ViewHolder] that contains a representation of each discovered reader
class ReaderHolder(val view: MaterialButton) : RecyclerView.ViewHolder(view)

// Our [RecyclerView.Adapter] implementation that allows us to update the list of readers
class ReaderAdapter(
    private val clickListener: ReaderClickListener
) : RecyclerView.Adapter<ReaderHolder>() {

    private var readers: List<Reader> = listOf()

    fun updateReaders(readers: List<Reader>) {
        this.readers = readers
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return readers.size
    }

    override fun onBindViewHolder(holder: ReaderHolder, position: Int) {
        holder.view.text = readers[position].serialNumber
        holder.view.setOnClickListener {
            clickListener.onClick(readers[position])
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReaderHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_reader, parent, false) as MaterialButton
        return ReaderHolder(view)
    }
}

class ReaderClickListener(val activityRef: WeakReference<MainActivity>) {
    fun onClick(reader: Reader) {
        println("ReaderClick, Attempting to connect to reader: ${reader.serialNumber}")

        val connectionConfig =
            ConnectionConfiguration.BluetoothConnectionConfiguration(reader.location!!.id!!)

        println("ReaderClick, Connection configuration set with location ID: ${reader.location!!.id!!}")

        val readerCallback = object: ReaderCallback {
            override fun onSuccess(reader: Reader) {
                println("ReaderCallback, Successfully connected to reader: ${reader.serialNumber}")
                activityRef.get()?.let {
                    it.runOnUiThread {
                        it.updateReaderConnection(isConnected = true)
                    }
                }
            }

            override fun onFailure(e: TerminalException) {
                println("ReaderCallback, Failed to connect to reader: ${e.localizedMessage}")
                e.printStackTrace()
                activityRef.get()?.let {
                    it.runOnUiThread {
                        Toast.makeText(it, "Failed to connect to reader", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        Terminal.getInstance().connectBluetoothReader(
            reader,
            connectionConfig,
            TerminalBluetoothReaderListener(),
            readerCallback,
        )
    }
}
