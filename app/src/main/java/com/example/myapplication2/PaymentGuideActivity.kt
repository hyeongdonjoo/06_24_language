package com.example.myapplication2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PaymentGuideActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_guide)

        val shopName = intent.getStringExtra("shopName") ?: ""
        val totalPrice = intent.getIntExtra("totalPrice", 0)
        val menuSummary = intent.getStringExtra("menuSummary") ?: ""

        findViewById<Button>(R.id.buttonStartPayment).setOnClickListener {
            sendOrderToFirebase(shopName, totalPrice, menuSummary)
        }
    }

    private fun sendOrderToFirebase(shopName: String, totalPrice: Int, menuSummary: String) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val cartItems = CartManager.getCartItems()

        val items = cartItems.map {
            mapOf(
                "name" to it.name,
                "desc" to it.desc,
                "category" to it.category,
                "price" to it.price,
                "quantity" to it.quantity
            )
        }

        val orderData = hashMapOf(
            "shopName" to shopName,
            "items" to items,
            "totalPrice" to totalPrice,
            "timestamp" to Timestamp.now()
        )

        db.collection("users")
            .document(userId)
            .collection("orders")
            .add(orderData)
            .addOnSuccessListener {
                val intent = Intent(this, OrderCompleteActivity::class.java).apply {
                    putExtra("shopName", shopName)
                    putExtra("totalPrice", totalPrice)
                    putExtra("menuSummary", menuSummary)
                }
                CartManager.clear()
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "주문 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
