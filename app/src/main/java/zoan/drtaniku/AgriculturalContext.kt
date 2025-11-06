package zoan.drtaniku

/**
 * Simple agricultural context data for Indonesian locations
 */
data class AgriculturalContext(
    val mainIndustry: String,
    val agriculturalProducts: List<String>,
    val incomeLevel: String,
    val growingSeason: String,
    val climateZone: String,
    val averageTemperature: Double,
    val annualRainfall: Double,
    val elevation: Double
) {
    /**
     * Get agricultural recommendations based on context
     */
    fun getRecommendations(): List<String> {
        return buildList {
            // Climate-based recommendations
            when {
                annualRainfall < 1000 -> add("💧 Pertimbangkan sistem irigasi karena curah hujan rendah")
                annualRainfall > 3000 -> add("🌾 Cocok untuk padi sawah dengan curah hujan tinggi")
                else -> add("☀️ Curah hujan normal untuk berbagai jenis tanaman")
            }

            // Elevation-based recommendations
            when {
                elevation < 200 -> add("🏠 Dataran rendah - cocok untuk palawija dan sayuran")
                elevation < 500 -> add("⛰️ Dataran menengah - cocok untuk perkebunan")
                else -> add("🏔️ Dataran tinggi - cocok untuk hortikultura tropis")
            }

            // Temperature-based recommendations
            when {
                averageTemperature < 20 -> add("🌡️ Suhu rendah - cocok untuk sayuran daun")
                averageTemperature > 28 -> add("🔥 Suhu tinggi - pastikan sistem drainase baik")
                else -> add("🌤️ Suhu ideal untuk tanaman tropis")
            }

            // Industry-specific recommendations
            when (mainIndustry.lowercase()) {
                "pertanian" -> add("🌱 Wilayah agraris - tingkatkan dengan teknologi pertanian presisi")
                "perkebunan" -> add("🌴 Fokus pada tanaman perkebunan bernilai tinggi")
                "perikanan" -> add("🐨 Integrasikan dengan akuakultur jika memungkinkan")
                "peternakan" -> add("🐄 Pertimbangkan integrasi ternak-tanaman")
            }
        }
    }
}