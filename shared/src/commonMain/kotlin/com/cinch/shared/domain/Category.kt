package com.cinch.shared.domain

/** Harcama kategorileri. Dil bağımsız sabit anahtarlar; görünen ad UI katmanında çevrilir. */
enum class Category {
    MARKET,      // market, bakkal, zincir marketler
    YEMEK,       // yemek, restoran, kafe, su, ekmek, büfe
    ULASIM,      // taksi, otobüs, metro, benzin, otopark
    FATURA,      // elektrik, su, doğalgaz, internet, telefon
    SAGLIK,      // eczane, doktor, hastane
    EGLENCE,     // sinema, oyun, abonelik
    GIYIM,       // giyim, ayakkabı
    KISISEL,     // kuaför, kozmetik
    EV,          // kira, mobilya, ev eşyası
    DIGER;       // sınıflandırılamayan
}
