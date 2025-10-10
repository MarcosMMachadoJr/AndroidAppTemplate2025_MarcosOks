package com.ifpr.androidapptemplate.baseclasses

data class Item(
    var objeto: String? = null,
    var quantidade: Int? = null,
    val base64Image: String? = null,
    val imageUrl: String? = null
)
