package com.cinch.ui.onboarding

/** Ekranların tanıtım balonları; sıra, gösterilme sırasıdır. */
object CoachMarkSteps {

    val HOME = listOf(
        CoachMark(
            key = "home.today",
            title = "Bugünkü payın",
            text = "Haftalık sınırının bugüne düşen kısmı. Az harcarsan yarınki payın büyür, çok harcarsan küçülür. " +
                "Alttaki çubuklar haftanın günleri.",
        ),
        CoachMark(
            key = "home.entry",
            title = "Harcamayı buraya yaz",
            text = "\"195 market\", \"dün 120 taksi\", \"300 yemek yarı yarıya\" gibi. Tutar, kategori ve tarih " +
                "kendiliğinden anlaşılır; 📷 ile fiş de okutabilirsin.",
        ),
        CoachMark(
            key = "home.progress",
            title = "Seri ve kumbara",
            text = "Payının içinde kaldığın her gün serin uzar 🔥. Haftadan artanı kumbarana atarsın 💰. " +
                "Seriye dokun, rozetlerini gör.",
        ),
        CoachMark(
            key = "home.history",
            title = "Geçmiş ve düzeltme",
            text = "Geçmiş haftalar, arama ve kategori süzgeci burada. Bir harcamayı düzeltmek için üstüne dokun, " +
                "silmek için sola kaydır.",
        ),
    )

    val MONTH = listOf(
        CoachMark(
            key = "month.summary",
            title = "Bu ayın hesabı",
            text = "Gelir − sabit giderler − harcamalar − kumbara = elinde kalan. Maaşını ekleyince hesap " +
                "kendiliğinden kurulur.",
        ),
        CoachMark(
            key = "month.cash",
            title = "Para takvimi",
            text = "Hesabındaki parayı yaz; maaşa kadar yetip yetmediğini ve hangi gün sıkışacağını gör.",
        ),
        CoachMark(
            key = "month.fixed",
            title = "Sabit giderler",
            text = "Kira, fatura, taksit, abonelik… Ödeyince yanındaki yuvarlağa dokun. Koç ödeme gününden önce " +
                "hatırlatır; faturayı 📷 ile de okutabilirsin.",
        ),
    )

    val CARDS = listOf(
        CoachMark(
            key = "cards.add",
            title = "Kartını ekle",
            text = "Kredi kartında kesim günü ve borç, banka kartında yalnız ad yeter. Harcama yazarken kartın adını " +
                "geçirirsen (\"195 market qnb\") o karta bağlanır.",
        ),
        CoachMark(
            key = "cards.statement",
            title = "Ekstreyi fotoğraftan oku",
            text = "Banka uygulamasındaki kart ekranını başka bir telefonla çek ya da galeriden seç. Son ödeme, borç " +
                "ve hareketler okunur; girmeyi unuttukların bulunur.",
        ),
    )

    val ALL = HOME + MONTH + CARDS
}
