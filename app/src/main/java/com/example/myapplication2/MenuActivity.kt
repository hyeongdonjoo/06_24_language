package com.example.myapplication2

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore

class MenuActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var menuContainer: LinearLayout
    private lateinit var shopName: String
    private lateinit var categoryContainer: LinearLayout
    private var selectedCategory: String = ""
    private val selectedMenuNames = mutableSetOf<String>()

    private val categoryMapLocalized = mapOf(
        "ko" to mapOf(
            "버거킹" to listOf("버거단품", "세트", "사이드", "음료&디저트"),
            "김밥천국" to listOf("김밥류", "덮밥류", "분식류", "면류"),
            "스타벅스" to listOf("에스프레소", "콜드브루", "리프레셔", "케이크")
        ),
        "en" to mapOf(
            "버거킹" to listOf("Burger", "Set", "Side", "Drink & Dessert"),
            "김밥천국" to listOf("Gimbap", "Rice Bowl", "Snacks", "Noodles"),
            "스타벅스" to listOf("Espresso", "Cold Brew", "Refresher", "Cake")
        ),
        "ja" to mapOf(
            "버거킹" to listOf("バーガー", "セット", "サイドメニュー", "ドリンク・デザート"),
            "김밥천국" to listOf("キンパ", "丼もの", "粉もの", "麺類"),
            "스타벅스" to listOf("エスプレッソ", "コールドブリュー", "リフレッシャー", "ケーキ")
        ),
        "zh" to mapOf(
            "버거킹" to listOf("汉堡", "套餐", "配餐", "饮品与甜点"),
            "김밥천국" to listOf("紫菜包饭", "盖饭", "韩式小吃", "面类"),
            "스타벅스" to listOf("浓缩咖啡", "冷萃咖啡", "清爽饮品", "蛋糕")
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        LocaleHelper.setLocale(this, LocaleHelper.getSavedLanguage(this))
        setContentView(R.layout.activity_menu)

        findViewById<Button>(R.id.buttonBack).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.buttonLanguage).setOnClickListener {
            showLanguageDialog()
        }

        shopName = intent.getStringExtra("shopName") ?: ""
        if (shopName.isBlank()) {
            showToast("가게 이름이 올바르지 않습니다.")
            finish()
            return
        }

        CartManager.currentShopName = shopName
        CartManager.clear()
        selectedMenuNames.clear()

        findViewById<TextView>(R.id.textViewMenuTitle).text = "$shopName 의 메뉴입니다"

        findViewById<Button>(R.id.buttonGoCart).setOnClickListener {
            val intent = Intent(this, CartActivity::class.java)
            intent.putExtra("shopName", shopName)
            startActivity(intent)
        }

        menuContainer = findViewById(R.id.menuContainer)
        categoryContainer = findViewById(R.id.categoryContainer)
        db = FirebaseFirestore.getInstance()

        setupCategoryButtons()

        checkShopExistsAndLoadMenus()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("한국어", "English", "日本語", "中文")
        val codes = arrayOf("ko", "en", "ja", "zh")

        AlertDialog.Builder(this)
            .setTitle("언어 선택")
            .setItems(languages) { _, which ->
                LocaleHelper.saveLanguage(this, codes[which])
                LocaleHelper.setLocale(this, codes[which])
                recreate()
            }
            .show()
    }

    private fun setupCategoryButtons() {
        categoryContainer.removeAllViews()
        val lang = LocaleHelper.getSavedLanguage(this)
        val categories = categoryMapLocalized[lang]?.get(shopName) ?: listOf()

        if (categories.isEmpty()) {
            showToast("이 가게에 카테고리 정보가 없습니다.")
            return
        }

        fun updateButtonStyles(selectedBtn: Button) {
            for (i in 0 until categoryContainer.childCount) {
                val btn = categoryContainer.getChildAt(i) as Button
                btn.setBackgroundColor(Color.TRANSPARENT)
                btn.setTextColor(Color.BLACK)
            }
            selectedBtn.setBackgroundColor(Color.parseColor("#FFBB33"))
            selectedBtn.setTextColor(Color.WHITE)
        }

        categories.forEachIndexed { index, category ->
            val btn = Button(this)
            btn.text = category
            btn.setBackgroundColor(Color.TRANSPARENT)
            btn.setTextColor(Color.BLACK)
            btn.setPadding(20, 10, 20, 10)

            btn.setOnClickListener {
                if (selectedCategory != category) {
                    selectedCategory = category
                    updateButtonStyles(btn)
                    loadMenusFromFirestore()
                }
            }
            categoryContainer.addView(btn)

            if (index == 0) {
                selectedCategory = category
                btn.setBackgroundColor(Color.parseColor("#FFBB33"))
                btn.setTextColor(Color.WHITE)
            }
        }
    }

    private fun checkShopExistsAndLoadMenus() {
        db.collection("shops").document(shopName).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    loadMenusFromFirestore()
                } else {
                    showToast("존재하지 않는 가게입니다.")
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e("MenuActivity", "가게 조회 실패", e)
                showToast("가게 정보 로딩 실패")
                finish()
            }
    }

    private fun loadMenusFromFirestore() {
        db.collection("shops")
            .document(shopName)
            .collection("menus")
            .get()
            .addOnSuccessListener { documents ->
                Log.d("MenuActivity", "✅ 메뉴 ${documents.size()}개 불러옴")
                menuContainer.removeAllViews()

                val lang = LocaleHelper.getSavedLanguage(this)

                for (doc in documents) {
                    val data = doc.data
                    val nameMap = data["name"] as? Map<*, *> ?: continue
                    val descMap = data["desc"] as? Map<*, *> ?: continue
                    val categoryMap = data["category"] as? Map<*, *> ?: continue

                    val name = getLocalizedText(nameMap, lang)
                    val desc = getLocalizedText(descMap, lang)
                    val category = getLocalizedText(categoryMap, lang)

                    val menu = MenuItem(
                        name = name,
                        desc = desc,
                        price = (data["price"] as? Long)?.toInt() ?: 0,
                        image = data["image"] as? String ?: "",
                        category = category
                    )

                    if (menu.category == selectedCategory) {
                        addMenuCard(menu)
                    }
                }
            }
            .addOnFailureListener { e ->
                showToast("❗ 메뉴 불러오기 실패")
                Log.e("MenuActivity", "🔥 메뉴 로딩 실패", e)
            }
    }

    private fun getLocalizedText(map: Map<*, *>, lang: String): String {
        return map[lang]?.toString()
            ?: map["en"]?.toString()
            ?: map["ko"]?.toString()
            ?: ""
    }

    private fun addMenuCard(menu: MenuItem) {
        val view = LayoutInflater.from(this).inflate(R.layout.menu_item, menuContainer, false)
        val menuRoot = view.findViewById<View>(R.id.menuRoot)

        val isSelected = selectedMenuNames.contains(menu.name)
        menuRoot.setBackgroundColor(if (isSelected) Color.parseColor("#66000000") else Color.WHITE)

        menuRoot.setOnClickListener {
            val currentlySelected = selectedMenuNames.contains(menu.name)
            if (currentlySelected) {
                selectedMenuNames.remove(menu.name)
                menuRoot.setBackgroundColor(Color.WHITE)
                CartManager.removeOneItem(menu.name)
                showToast("${menu.name} 빠졌습니다")
            } else {
                selectedMenuNames.add(menu.name)
                menuRoot.setBackgroundColor(Color.parseColor("#66000000"))
                CartManager.addItem(
                    CartItem(
                        name = menu.name,
                        desc = menu.desc,
                        category = menu.category,
                        price = menu.price,
                        quantity = 1
                    )
                )
                showToast("${menu.name} 담았습니다")
            }
        }

        view.findViewById<TextView>(R.id.textMenuName).text = menu.name
        view.findViewById<TextView>(R.id.textMenuDesc).text = menu.desc
        view.findViewById<TextView>(R.id.textMenuPrice).text = "${menu.price}원"

        val imageView = view.findViewById<ImageView>(R.id.imageMenu)
        if (menu.image.isNotBlank()) {
            db.collection("photo")
                .document(menu.image)
                .get()
                .addOnSuccessListener { snapshot ->
                    val url = snapshot.getString("imageUrl")
                    if (!url.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(url)
                            .into(imageView)
                    }
                }
                .addOnFailureListener {
                    Log.e("MenuActivity", "🔥 이미지 로딩 실패", it)
                }
        } else {
            imageView.setImageResource(R.drawable.default_image)
        }

        menuContainer.addView(view)
    }

    private fun showToast(message: String) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.view?.findViewById<TextView>(android.R.id.message)?.setTextColor(Color.BLACK)
        toast.show()
    }
}
