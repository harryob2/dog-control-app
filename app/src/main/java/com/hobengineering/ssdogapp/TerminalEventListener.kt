package com.hobengineering.ssdogapp

import com.stripe.stripeterminal.external.callable.TerminalListener
import com.stripe.stripeterminal.external.models.Reader

class TerminalEventListener : TerminalListener {
    override fun onUnexpectedReaderDisconnect(reader: Reader) {
        // Show UI that your reader disconnected
    }
}