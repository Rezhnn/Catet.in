package com.catetin.parser.parse

object CategoryGuesser {

    private val rules: List<Pair<List<String>, String>> = listOf(
        listOf(
            "kopi", "cafe", "kafe", "restoran", "restaurant", "makan", "warung", "bakery",
            "gofood", "grabfood", "shopeefood", "food", "minuman", "boba", "pizza", "burger",
            "kfc", "mcd", "mcdonald", "starbucks", "jajan",
        ) to "Makanan & Minuman",
        listOf(
            "ojek", "grab", "gojek", "bensin", "pertalite", "pertamax", "transport", "tol",
            "parkir", "krl", "mrt", "lrt", "transjakarta", "taksi", "taxi", "travel",
        ) to "Transportasi",
        listOf(
            "shopee", "tokopedia", "lazada", "blibli", "belanja", "marketplace", "mall",
            "store", "toko", "supermarket", "indomaret", "alfamart", "hypermart",
        ) to "Belanja",
        listOf(
            "listrik", "pln", "pdam", "air", "internet", "indihome", "telkom", "tagihan",
            "iuran", "bpjs", "asuransi", "sewa", "rent", "cicilan", "kredit",
        ) to "Tagihan",
        listOf(
            "pulsa", "data", "telkomsel", "indosat", "im3", "xl", "axis", "tri", "smartfren",
            "kuota", "paket data",
        ) to "Pulsa & Data",
        listOf(
            "bioskop", "cinema", "netflix", "spotify", "youtube", "game", "steam", "hiburan",
            "konser", "tiket", "viu", "vidio", "disney",
        ) to "Hiburan",
        listOf(
            "apotek", "klinik", "rumah sakit", "hospital", "dokter", "obat", "kesehatan",
            "halodoc", "alodokter", "gym", "fitness",
        ) to "Kesehatan",
        listOf(
            "sekolah", "kuliah", "kursus", "pendidikan", "buku", "udemy", "coursera",
            "spp", "uang pangkal",
        ) to "Pendidikan",
        listOf("donasi", "zakat", "infaq", "sedekah", "charity") to "Donasi",
        listOf("transfer", "kirim uang", "remitansi") to "Transfer",
    )

    fun guess(text: String): String {
        val lower = text.lowercase()
        return rules.firstOrNull { (keywords, _) -> keywords.any { lower.contains(it) } }?.second
            ?: "Lainnya"
    }

    val defaults: List<Pair<String, String>> = listOf(
        "Makanan & Minuman" to "restaurant",
        "Transportasi" to "directions_car",
        "Belanja" to "shopping_bag",
        "Tagihan" to "receipt_long",
        "Pulsa & Data" to "smartphone",
        "Hiburan" to "movie",
        "Kesehatan" to "health_and_safety",
        "Pendidikan" to "school",
        "Donasi" to "volunteer_activism",
        "Transfer" to "swap_horiz",
        "Gaji" to "payments",
        "Lainnya" to "more_horiz",
    )
}
