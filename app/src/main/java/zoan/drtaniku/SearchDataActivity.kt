package zoan.drtaniku

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

/**
 * SearchDataActivity - Search Indonesian administrative data from CSV
 *
 * Features:
 * - Search by Kabupaten and Desa/Kelurahan
 * - Display Kode Desa results
 * - Load data from assets CSV file
 * - Real-time search functionality
 */
class SearchDataActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SearchDataActivity"
    }

    // UI Components
    private lateinit var inputKabupaten: com.google.android.material.textfield.TextInputEditText
    private lateinit var inputDesa: com.google.android.material.textfield.TextInputEditText
    private lateinit var btnSearch: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var textNoResults: TextView
    private lateinit var textResultCount: TextView

    // Data
    private lateinit var searchAdapter: SearchResultAdapter
    private val searchResults = mutableListOf<DesaData>()
    private var allDesaData = mutableListOf<DesaData>()
    private var isDataLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_data)

        setupToolbar()
        initializeViews()
        setupRecyclerView()
        setupSearchListeners()
        loadCSVData()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Pencarian Data Wilayah"
        }
    }

    private fun initializeViews() {
        inputKabupaten = findViewById(R.id.input_kabupaten)
        inputDesa = findViewById(R.id.input_desa)
        btnSearch = findViewById(R.id.btn_search)
        progressBar = findViewById(R.id.progress_bar)
        recyclerView = findViewById(R.id.recycler_search_results)
        textNoResults = findViewById(R.id.text_no_results)
        textResultCount = findViewById(R.id.text_result_count)
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchResultAdapter(searchResults)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = searchAdapter
    }

    private fun setupSearchListeners() {
        // TextWatcher untuk desa input
        val desaTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                updateSearchButtonState()
            }
        }

        // TextWatcher khusus untuk kabupaten input dengan normalisasi real-time
        val kabupatenTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString()?.trim() ?: ""

                // Tampilkan normalized format di log untuk debugging
                if (input.isNotEmpty()) {
                    val normalized = normalizeKabupatenInput(input)
                    Log.d(TAG, "🔄 Kabupaten Input Normalization: '$input' -> '$normalized'")

                    // Update hint untuk menunjukkan format yang dinormalisasi
                    inputKabupaten.hint = "Format: $normalized"
                } else {
                    inputKabupaten.hint = "Contoh: Kab. Bandung"
                }

                updateSearchButtonState()
            }
        }

        inputKabupaten.addTextChangedListener(kabupatenTextWatcher)
        inputDesa.addTextChangedListener(desaTextWatcher)

        btnSearch.setOnClickListener {
            performSearch()
        }
    }

    private fun updateSearchButtonState() {
        val kabupaten = inputKabupaten.text?.toString()?.trim() ?: ""
        val desa = inputDesa.text?.toString()?.trim() ?: ""
        btnSearch.isEnabled = kabupaten.isNotEmpty() && desa.isNotEmpty()
    }

    private fun loadCSVData() {
        Log.d(TAG, "=== LOAD CSV DATA STARTED ===")
        val startTime = System.currentTimeMillis()
        showLoading(true)

        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "📁 Opening CSV file from assets...")
                val inputStream = assets.open("base.csv")
                val reader = BufferedReader(InputStreamReader(inputStream))

                val desaList = mutableListOf<DesaData>()
                var lineNumber = 0
                var totalLines = 0
                var provinsiLines = 0
                var desaLines = 0
                var kabupatenLines = 0
                var kecamatanLines = 0
                var otherLines = 0

                Log.d(TAG, "📖 Starting to read CSV lines...")

                reader.forEachLine { line ->
                    totalLines++
                    lineNumber++
                    try {
                        val parts = line.split(",", limit = 2)
                        if (parts.size == 2) {
                            val kode = parts[0].trim()
                            val nama = parts[1].trim()

                            // Debug: Log the first few lines to understand the format
                            if (lineNumber <= 5) {
                                Log.d(TAG, "🔍 Line $lineNumber - Raw: '$line'")
                                Log.d(TAG, "   Kode: '$kode' (length: ${kode.length})")
                                Log.d(TAG, "   Nama: '$nama'")
                            }

                            // Count all digits in kode to determine administrative level
                            val digitsInKode = kode.replace(".", "").length

                            when (digitsInKode) {
                                2 -> {
                                    // Provinsi (2 digits)
                                    provinsiLines++
                                    Log.v(TAG, "🏛️ Provinsi: $kode - $nama")
                                }
                                4 -> {
                                    // Kabupaten/Kota (4 digits)
                                    kabupatenLines++
                                    desaList.add(DesaData(kode, nama)) // Add kabupaten to list for map building
                                    if (kabupatenLines <= 5) { // Log first 5 kabupaten for debugging
                                        Log.d(TAG, "🏛️ Kabupaten #$kabupatenLines: $kode - $nama")
                                    }
                                }
                                6 -> {
                                    // Kecamatan (6 digits)
                                    kecamatanLines++
                                    Log.v(TAG, "🏘️ Kecamatan: $kode - $nama")
                                }
                                10 -> {
                                    // Desa/Kelurahan (10 digits)
                                    desaLines++
                                    desaList.add(DesaData(kode, nama))
                                    if (desaLines <= 5) { // Log first 5 desa for debugging
                                        Log.d(TAG, "🏡 Desa #$desaLines: $kode - $nama")
                                    }
                                }
                                else -> {
                                    otherLines++
                                    Log.v(TAG, "❓ Other ($kode - digits: $digitsInKode): $nama")
                                }
                            }
                        } else {
                            Log.w(TAG, "⚠️ Invalid CSV format at line $lineNumber: $line")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "❌ Error parsing line $lineNumber: $line", e)
                    }

                    // Progress logging every 10000 lines
                    if (totalLines % 10000 == 0) {
                        Log.d(TAG, "📊 Processed $totalLines lines so far...")
                    }
                }

                reader.close()
                inputStream.close()

                val readingTime = System.currentTimeMillis() - startTime

                Log.d(TAG, "📊 CSV Reading Summary:")
                Log.d(TAG, "   Total lines read: $totalLines")
                Log.d(TAG, "   Provinsi (2 digit): $provinsiLines")
                Log.d(TAG, "   Kabupaten/Kota (4 digit): $kabupatenLines")
                Log.d(TAG, "   Kecamatan (6 digit): $kecamatanLines")
                Log.d(TAG, "   Desa/Kelurahan (10 digit): $desaLines")
                Log.d(TAG, "   Other formats: $otherLines")
                Log.d(TAG, "   Desa entries collected: ${desaList.size}")
                Log.d(TAG, "   Reading time: ${readingTime}ms")

                // Sort data for better search performance
                val sortStartTime = System.currentTimeMillis()
                desaList.sortBy { it.nama.lowercase(Locale.getDefault()) }
                val sortTime = System.currentTimeMillis() - sortStartTime

                Log.d(TAG, "📋 Data sorting completed:")
                Log.d(TAG, "   Sorting time: ${sortTime}ms")
                Log.d(TAG, "   Sorted desa entries: ${desaList.size}")

                // Sample first and last desa for verification
                if (desaList.isNotEmpty()) {
                    Log.d(TAG, "📝 First desa: ${desaList.first().kode} - ${desaList.first().nama}")
                    Log.d(TAG, "📝 Last desa: ${desaList.last().kode} - ${desaList.last().nama}")
                }

                val totalTime = System.currentTimeMillis() - startTime

                withContext(Dispatchers.Main) {
                    allDesaData.clear()
                    allDesaData.addAll(desaList)
                    isDataLoaded = true
                    showLoading(false)

                    showToast("Data wilayah berhasil dimuat: ${desaList.size} desa/kelurahan")

                    Log.d(TAG, "✅ CSV Data Loading Completed:")
                    Log.d(TAG, "   Total processing time: ${totalTime}ms")
                    Log.d(TAG, "   Desa data loaded: ${desaList.size}")
                    Log.d(TAG, "   allDesaData size: ${allDesaData.size}")
                    Log.d(TAG, "   isDataLoaded: $isDataLoaded")
                    Log.d(TAG, "=== LOAD CSV DATA COMPLETED ===")
                }

            } catch (e: Exception) {
                val errorTime = System.currentTimeMillis() - startTime
                Log.e(TAG, "❌ Error loading CSV data after ${errorTime}ms", e)
                Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Error message: ${e.message}")
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                    showLoading(false)
                    showToast("Gagal memuat data wilayah: ${e.message}")
                }
            }
        }
    }

    /**
     * Normalisasi input kabupaten untuk mengenali berbagai variasi penulisan
     * Contoh: "kabupaten" -> "kab.", "kab" -> "kab.", dll
     */
    private fun normalizeKabupatenInput(input: String): String {
        val normalized = input.lowercase(Locale.getDefault()).trim()

        return when {
            // Variasi penulisan "kabupaten"
            normalized.startsWith("kabupaten") -> "kab. ${normalized.substringAfter("kabupaten").trim()}"
            normalized.startsWith("kab") && !normalized.startsWith("kab.") -> "kab. ${normalized.substringAfter("kab").trim()}"
            normalized.startsWith("kab.") -> normalized

            // Variasi penulisan "kota"
            normalized.startsWith("kota") -> when {
                normalized.startsWith("kota ") -> "kota ${normalized.substringAfter("kota ").trim()}"
                else -> "kota $normalized"
            }

            // Jika sudah sesuai format, kembalikan as-is
            normalized.startsWith("kab.") || normalized.startsWith("kota ") -> normalized

            // Default: tambahkan "kab." di depan
            else -> "kab. $normalized"
        }
    }

    private fun performSearch() {
        Log.d(TAG, "=== PERFORM SEARCH STARTED ===")
        val startTime = System.currentTimeMillis()

        val kabupatenInput = inputKabupaten.text?.toString()?.trim() ?: ""
        val desaInput = inputDesa.text?.toString()?.trim() ?: ""

        // Normalisasi input kabupaten
        val kabupaten = normalizeKabupatenInput(kabupatenInput)
        val desa = desaInput.lowercase(Locale.getDefault())

        Log.d(TAG, "📝 Search Parameters:")
        Log.d(TAG, "   Kabupaten Original: '$kabupatenInput'")
        Log.d(TAG, "   Kabupaten Normalized: '$kabupaten'")
        Log.d(TAG, "   Desa Input: '$desaInput'")
        Log.d(TAG, "   Desa (lowercase): '$desa'")

        if (kabupaten.isEmpty() || desa.isEmpty()) {
            Log.w(TAG, "❌ Search validation failed - empty inputs")
            showToast("Mohon isi kedua kolom pencarian")
            return
        }

        Log.d(TAG, "✅ Input validation passed")
        showLoading(true)

        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "📊 Starting search process...")

                // Create a snapshot of data to avoid concurrent modification
                val snapshotStartTime = System.currentTimeMillis()
                val snapshotData = allDesaData.toList()
                val snapshotTime = System.currentTimeMillis() - snapshotStartTime

                Log.d(TAG, "📋 Data Snapshot Created:")
                Log.d(TAG, "   Total desa data: ${allDesaData.size}")
                Log.d(TAG, "   Snapshot size: ${snapshotData.size}")
                Log.d(TAG, "   Snapshot time: ${snapshotTime}ms")

                // Pre-build kabupaten lookup map for efficiency
                val mapStartTime = System.currentTimeMillis()
                val kabupatenMap = snapshotData
                    .filter { it.kode.replace(".", "").length == 4 }
                    .associate { it.kode to it.nama.lowercase(Locale.getDefault()) }
                val mapTime = System.currentTimeMillis() - mapStartTime

                Log.d(TAG, "🗺️ Kabupaten Map Built:")
                Log.d(TAG, "   Total kabupaten entries: ${kabupatenMap.size}")
                Log.d(TAG, "   Map build time: ${mapTime}ms")

                // Log kabupaten samples for debugging
                val kabupatenSamples = kabupatenMap.entries.take(5)
                kabupatenSamples.forEach { (kode, nama) ->
                    Log.d(TAG, "   Kabupaten Sample: $kode -> $nama")
                }

                // Search logic: cari kabupaten dulu, kemudian filter desa berdasarkan kode kabupaten
                Log.d(TAG, "🔍 Starting search with kabupaten-first algorithm...")
                var kabupatenMatchesCount = 0

                // Step 1: Find all kabupaten that match the search term
                Log.d(TAG, "📍 Step 1: Finding matching kabupaten...")
                val matchingKabupaten = mutableListOf<Pair<String, String>>() // kode -> nama
                kabupatenMap.forEach { (kode, nama) ->
                    if (nama.contains(kabupaten)) {
                        matchingKabupaten.add(Pair(kode, nama))
                        kabupatenMatchesCount++
                        Log.d(TAG, "   ✅ Found Kabupaten: $kode - $nama")
                    }
                }

                Log.d(TAG, "   Total matching kabupaten: $kabupatenMatchesCount")

                // Step 2: Filter desa berdasarkan kode awal kabupaten yang ditemukan
                Log.d(TAG, "📍 Step 2: Filtering desa by kabupaten code...")
                var desaFilteredCount = 0
                var finalMatchesCount = 0

                val results: List<DesaData> = if (matchingKabupaten.isNotEmpty()) {
                    // Only search desa that belong to matching kabupaten
                    val kabupatenCodes = matchingKabupaten.map { it.first }.toSet()

                    snapshotData.filter { desaData ->
                        // Check if desa belongs to any matching kabupaten (desa should start with kabupaten code)
                        val belongsToMatchingKabupaten = kabupatenCodes.any { kodeKabupaten ->
                            desaData.kode.startsWith(kodeKabupaten)
                        }

                        if (belongsToMatchingKabupaten) {
                            desaFilteredCount++
                        }

                        // Only apply desa name filter if desa input is provided
                        val namaDesa = desaData.nama.lowercase(Locale.getDefault())
                        val matchesDesa = namaDesa.contains(desa)

                        val finalMatch = belongsToMatchingKabupaten && matchesDesa
                        if (finalMatch) {
                            finalMatchesCount++
                            if (finalMatchesCount <= 5) { // Log first 5 matches for debugging
                                Log.d(TAG, "✅ Found Match #$finalMatchesCount:")
                                Log.d(TAG, "   Kode: ${desaData.kode}")
                                Log.d(TAG, "   Nama: ${desaData.nama}")
                                Log.d(TAG, "   Belongs to Kabupaten: ${desaData.kode} (exists in matches: ${kabupatenCodes.any { desaData.kode.startsWith(it) }})")
                                Log.d(TAG, "   Matches Desa: $matchesDesa")
                            }
                        }

                        finalMatch
                    }
                } else {
                    emptyList()
                }

                val searchTime = System.currentTimeMillis() - startTime
                val filterTime = System.currentTimeMillis() - (startTime + snapshotTime + mapTime)

                Log.d(TAG, "📈 Search Results Summary:")
                Log.d(TAG, "   Total Desa Items Processed: ${snapshotData.size}")
                Log.d(TAG, "   Matching Kabupaten Found: $kabupatenMatchesCount")
                Log.d(TAG, "   Desa Filtered by Kabupaten: $desaFilteredCount")
                Log.d(TAG, "   Final Matches: $finalMatchesCount")
                Log.d(TAG, "   Filtering Time: ${filterTime}ms")
                Log.d(TAG, "   Total Search Time: ${searchTime}ms")

                // Log some non-matching examples for debugging
                if (finalMatchesCount == 0) {
                    Log.d(TAG, "❌ No matches found - Debugging non-matches:")
                    snapshotData.take(5).forEach { desaData ->
                        val namaDesa = desaData.nama.lowercase(Locale.getDefault())
                        val kodeKabupaten = desaData.kode
                        val namaKabupaten = kabupatenMap[kodeKabupaten] ?: ""

                        Log.d(TAG, "   Non-Match: ${desaData.kode} - ${desaData.nama}")
                        Log.d(TAG, "       Kabupaten: $namaKabupaten (search: '$kabupaten', match: ${namaKabupaten.contains(kabupaten)})")
                        Log.d(TAG, "       Desa: $namaDesa (search: '$desa', match: ${namaDesa.contains(desa)})")
                    }
                }

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "🎯 Updating UI with ${results.size} results")
                    showSearchResults(results)
                    showLoading(false)

                    Log.d(TAG, "=== PERFORM SEARCH COMPLETED ===")
                    Log.d(TAG, "Total execution time: ${System.currentTimeMillis() - startTime}ms")
                    Log.d(TAG, "Results shown: ${results.size}")
                }

            } catch (e: Exception) {
                val errorTime = System.currentTimeMillis() - startTime
                Log.e(TAG, "❌ Error during search after ${errorTime}ms", e)
                Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Error message: ${e.message}")
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                    showLoading(false)
                    showToast("Error pencarian: ${e.message}")
                }
            }
        }
    }

    private fun showSearchResults(results: List<DesaData>) {
        searchResults.clear()
        searchResults.addAll(results)
        searchAdapter.notifyDataSetChanged()

        // Update UI
        if (results.isEmpty()) {
            recyclerView.visibility = View.GONE
            textNoResults.visibility = View.VISIBLE
            textResultCount.text = "Hasil: 0 ditemukan"
        } else {
            recyclerView.visibility = View.VISIBLE
            textNoResults.visibility = View.GONE
            textResultCount.text = "Hasil: ${results.size} ditemukan"

            // Log first few results for debugging
            results.take(3).forEach { desa ->
                Log.d(TAG, "Result: ${desa.kode} - ${desa.nama}")
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSearch.isEnabled = !show
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

/**
 * Data class for Desa/Kelurahan
 */
data class DesaData(
    val kode: String,
    val nama: String
) {
    fun getDisplayText(): String {
        return "$kode - $nama"
    }
}

/**
 * RecyclerView adapter for search results
 */
class SearchResultAdapter(
    private val searchResults: List<DesaData>
) : RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textKode: TextView = itemView.findViewById(R.id.text_kode)
        val textNama: TextView = itemView.findViewById(R.id.text_nama)
        val cardResult: CardView = itemView.findViewById(R.id.card_result)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val desa = searchResults[position]

        holder.textKode.text = desa.kode
        holder.textNama.text = desa.nama

        holder.cardResult.setOnClickListener {
            // Copy kode to clipboard
            val clipboard = android.content.ClipData.newPlainText("Kode Desa", desa.kode)
            val clipboardManager = holder.itemView.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboardManager.setPrimaryClip(clipboard)

            android.widget.Toast.makeText(
                holder.itemView.context,
                "Kode Desa ${desa.kode} disalin!",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun getItemCount(): Int = searchResults.size
}