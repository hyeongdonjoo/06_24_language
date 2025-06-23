package com.example.myapplication2

data class MenuItem(
    val name: String,
    val desc: String,
    val category: String,
    val price: Int,
    val image: String = "",        // ← 이미지 추가
    val quantity: Int = 1          // ← 기본값 설정
)
