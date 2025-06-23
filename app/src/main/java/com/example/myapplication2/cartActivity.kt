package com.example.myapplication2

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CartActivity : AppCompatActivity() {

    private lateinit var layoutCartItems: LinearLayout
    private lateinit var textTotalPrice: TextView
    private lateinit var textEmptyCart: TextView
    private lateinit var buttonOrder: Button
    private lateinit var buttonBack: Button
    private lateinit var shopName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_cart)

        layoutCartItems = findViewById(R.id.layoutCartItems)
        textTotalPrice = findViewById(R.id.textTotalPrice)
        textEmptyCart = findViewById(R.id.textEmptyCart)
        buttonOrder = findViewById(R.id.buttonOrder)
        buttonBack = findViewById(R.id.buttonBack)

        shopName = intent.getStringExtra("shopName") ?: getString(R.string.unknown_shop)

        buttonBack.setOnClickListener {
            finish()
        }

        displayCartItems()

        buttonOrder.setOnClickListener {
            if (CartManager.getCartItems().isEmpty()) {
                Toast.makeText(this, getString(R.string.cart_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 주문 정보 준비
            val cartItems = CartManager.getCartItems()
            val menuSummary = cartItems.joinToString(", ") { "${it.name} x ${it.quantity}" }
            val totalPrice = CartManager.getTotalPrice()

            // 결제 안내 화면으로 이동 (주문 정보 전달)
            val intent = Intent(this, PaymentGuideActivity::class.java).apply {
                putExtra("shopName", shopName)
                putExtra("totalPrice", totalPrice)
                putExtra("menuSummary", menuSummary)
            }
            startActivity(intent)
        }
    }

    private fun formatPrice(price: Int): String {
        return getString(R.string.price_format, price)
    }

    private fun displayCartItems() {
        layoutCartItems.removeAllViews()

        val cartItems = CartManager.getCartItems()
        if (cartItems.isEmpty()) {
            textEmptyCart.visibility = View.VISIBLE
            buttonOrder.visibility = View.GONE
            textTotalPrice.text = ""
            return
        }

        textEmptyCart.visibility = View.GONE
        buttonOrder.visibility = View.VISIBLE

        for (item in cartItems) {
            val itemView = layoutInflater.inflate(R.layout.cart_item, layoutCartItems, false)
            val textItemName = itemView.findViewById<TextView>(R.id.textItemName)
            val textItemPrice = itemView.findViewById<TextView>(R.id.textItemPrice)
            val textQuantity = itemView.findViewById<TextView>(R.id.textQuantity)
            val buttonIncrease = itemView.findViewById<Button>(R.id.buttonIncrease)
            val buttonDecrease = itemView.findViewById<Button>(R.id.buttonDecrease)
            val buttonRemove = itemView.findViewById<Button>(R.id.buttonRemove)

            textItemName.text = item.name
            textQuantity.text = item.quantity.toString()
            textItemPrice.text = formatPrice(item.price * item.quantity)

            buttonIncrease.setOnClickListener {
                item.quantity++
                displayCartItems()
            }

            buttonDecrease.setOnClickListener {
                if (item.quantity > 1) {
                    item.quantity--
                    displayCartItems()
                } else {
                    Toast.makeText(this, getString(R.string.min_quantity_warning), Toast.LENGTH_SHORT).show()
                }
            }

            buttonRemove.setText(R.string.remove)
            buttonRemove.setOnClickListener {
                CartManager.removeItem(item)
                displayCartItems()
            }

            layoutCartItems.addView(itemView)
        }

        textTotalPrice.text = getString(R.string.total_format, formatPrice(CartManager.getTotalPrice()))
    }

    fun sendOrderToFirebase(shopName: String, menuSummary: String, totalPrice: Int) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, getString(R.string.login_required), Toast.LENGTH_SHORT).show()
            buttonOrder.isEnabled = true
            return
        }

        val cartItems = CartManager.getCartItems()

        val orderData = hashMapOf(
            "shopName" to shopName,
            "items" to cartItems.map {
                mapOf(
                    "name" to it.name,
                    "price" to it.price,
                    "quantity" to it.quantity
                )
            },
            "totalPrice" to totalPrice,
            "timestamp" to Timestamp.now()
        )

        db.collection("users")
            .document(userId)
            .collection("orders")
            .add(orderData)
            .addOnSuccessListener {
                val intent = Intent(this@CartActivity, OrderCompleteActivity::class.java).apply {
                    putExtra("shopName", shopName)
                    putExtra("totalPrice", totalPrice)
                    putExtra("menuSummary", menuSummary)
                }
                CartManager.clear()
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, getString(R.string.order_failed, e.message), Toast.LENGTH_SHORT).show()
                buttonOrder.isEnabled = true
            }
    }
}
