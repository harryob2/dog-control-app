package com.hobengineering.ssdogapp

data class PaymentIntentRequest(
    val amount: Int,
    val currency: String
)

data class PaymentIntentResponse(
    val clientSecret: String
)