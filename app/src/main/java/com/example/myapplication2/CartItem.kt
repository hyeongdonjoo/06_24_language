package com.example.myapplication2

data class CartItem(
    val name: String,
    val desc: String,
    val category: String,
    val price: Int,
    var quantity: Int
)