package com.cloth.wardrobe

import android.app.Application
import com.cloth.wardrobe.data.WardrobeStore

class ClothApplication : Application() {
    val store: WardrobeStore by lazy { WardrobeStore(this) }
}
