package zoan.drtaniku

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zoan.drtaniku.adapter.SavedAnalysesAdapter
import zoan.drtaniku.database.AnalysisDatabaseHelper
import zoan.drtaniku.model.SavedAnalysis

class SavedAnalysesActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    // UI Components
    private lateinit var toolbar: Toolbar
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var recyclerSavedAnalyses: RecyclerView
    private lateinit var progressLoading: ProgressBar
    private lateinit var layoutEmptyState: View
    private lateinit var textTotalCount: TextView
    private lateinit var textLastUpdated: TextView
    private lateinit var btnGoToAnalyze: AppCompatButton
    private lateinit var textInputSearch: TextInputLayout
    private lateinit var editTextSearch: TextInputEditText
    private lateinit var btnSearch: AppCompatButton
    private lateinit var btnClearSearch: AppCompatButton

    // Database and Adapter
    private lateinit var analysisDatabaseHelper: AnalysisDatabaseHelper
    private lateinit var savedAnalysesAdapter: SavedAnalysesAdapter

    // Coroutines
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Data
    private var allAnalyses: List<SavedAnalysis> = emptyList()
    private var filteredAnalyses: List<SavedAnalysis> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_analyses)

        // Initialize database
        analysisDatabaseHelper = AnalysisDatabaseHelper(this)

        // Initialize UI components
        initializeUI()
        setupNavigation()
        setupRecyclerView()
        setupClickListeners()

        // Load data
        loadSavedAnalyses()
    }

    private fun initializeUI() {
        toolbar = findViewById(R.id.toolbar)
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        recyclerSavedAnalyses = findViewById(R.id.recycler_saved_analyses)
        progressLoading = findViewById(R.id.progress_loading)
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        textTotalCount = findViewById(R.id.text_total_count)
        textLastUpdated = findViewById(R.id.text_last_updated)
        btnGoToAnalyze = findViewById(R.id.btn_go_to_analyze)
        textInputSearch = findViewById(R.id.text_input_search)
        editTextSearch = findViewById(R.id.edit_text_search)
        btnSearch = findViewById(R.id.btn_search)
        btnClearSearch = findViewById(R.id.btn_clear_search)

        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)
    }

    private fun setupNavigation() {
        navView.setNavigationItemSelectedListener(this)
    }

    private fun setupRecyclerView() {
        savedAnalysesAdapter = SavedAnalysesAdapter(
            emptyList(),
            onViewClick = { analysis ->
                showAnalysisDetail(analysis)
            },
            onDeleteClick = { analysis ->
                showDeleteConfirmation(analysis)
            }
        )

        recyclerSavedAnalyses.apply {
            layoutManager = LinearLayoutManager(this@SavedAnalysesActivity)
            adapter = savedAnalysesAdapter
        }
    }

    private fun setupClickListeners() {
        btnSearch.setOnClickListener {
            val query = editTextSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                searchAnalyses(query)
            } else {
                showToast("❌ Masukkan kata kunci pencarian")
            }
        }

        btnClearSearch.setOnClickListener {
            editTextSearch.text?.clear()
            showAllAnalyses()
        }

        btnGoToAnalyze.setOnClickListener {
            // Go back to home activity
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loadSavedAnalyses() {
        activityScope.launch {
            try {
                showLoading(true)

                val analyses = withContext(Dispatchers.IO) {
                    analysisDatabaseHelper.getAllAnalyses()
                }

                allAnalyses = analyses
                filteredAnalyses = analyses

                updateUI()
                showLoading(false)

            } catch (e: Exception) {
                showLoading(false)
                showToast("❌ Error memuat data: ${e.message}")
            }
        }
    }

    private fun searchAnalyses(query: String) {
        activityScope.launch {
            try {
                showLoading(true)

                val searchResults = withContext(Dispatchers.IO) {
                    analysisDatabaseHelper.getAnalysesByPlantName(query)
                }

                filteredAnalyses = searchResults
                updateUI()
                showLoading(false)

            } catch (e: Exception) {
                showLoading(false)
                showToast("❌ Error pencarian: ${e.message}")
            }
        }
    }

    private fun showAllAnalyses() {
        filteredAnalyses = allAnalyses
        updateUI()
    }

    private fun updateUI() {
        savedAnalysesAdapter.updateAnalyses(filteredAnalyses)

        // Update statistics
        textTotalCount.text = "📊 Total: ${filteredAnalyses.size} analisa"

        if (filteredAnalyses.isNotEmpty()) {
            textLastUpdated.text = "Terakhir diperbarui: ${filteredAnalyses.first().getFormattedDate()}"
        } else {
            textLastUpdated.text = "Terakhir diperbarui: -"
        }

        // Show/hide empty state
        if (filteredAnalyses.isEmpty()) {
            recyclerSavedAnalyses.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        } else {
            recyclerSavedAnalyses.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
        }
    }

    private fun showAnalysisDetail(analysis: SavedAnalysis) {
        val detailMessage = buildString {
            append("🌱 **Tanaman:** ${analysis.plantName}\n\n")
            append("📅 **Waktu:** ${analysis.getFormattedTimestamp()}\n\n")
            if (analysis.location.isNotEmpty()) {
                append("📍 **Lokasi:** ${analysis.location}\n\n")
            }
            append("📊 **Parameter Sensor:**\n")
            append("• 🌡️ Suhu: ${"%.1f".format(analysis.temperature)}°C\n")
            append("• 💧 Kelembaban: ${"%.1f".format(analysis.humidity)}%\n")
            append("• ⚗️ pH: ${"%.1f".format(analysis.ph)}\n")
            append("• 🧪 Nitrogen: ${"%.1f".format(analysis.nitrogen)}\n")
            append("• 🧪 Fosfor: ${"%.1f".format(analysis.phosphorus)}\n")
            append("• 🧪 Kalium: ${"%.1f".format(analysis.potassium)}\n\n")
            append("📋 **Hasil Analisa:**\n")
            append("─────────────────────────────────\n\n")
            append(analysis.analysisResult)
        }

        AlertDialog.Builder(this)
            .setTitle("🔍 Detail Analisa Tanaman")
            .setMessage(detailMessage)
            .setPositiveButton("Tutup", null)
            .setNeutralButton("Bagikan") { _, _ ->
                shareAnalysisResult(analysis)
            }
            .show()
    }

    private fun shareAnalysisResult(analysis: SavedAnalysis) {
        val shareText = buildString {
            append("🌱 HASIL ANALISA TANAMAN - DR.TANIKU 🌱\n\n")
            append("Tanaman: ${analysis.plantName}\n")
            append("Waktu: ${analysis.getFormattedTimestamp()}\n")
            if (analysis.location.isNotEmpty()) {
                append("Lokasi: ${analysis.location}\n")
            }
            append("\n📊 Parameter Sensor:\n")
            append("• Suhu: ${"%.1f".format(analysis.temperature)}°C\n")
            append("• Kelembaban: ${"%.1f".format(analysis.humidity)}%\n")
            append("• pH: ${"%.1f".format(analysis.ph)}\n")
            append("• Nitrogen: ${"%.1f".format(analysis.nitrogen)}\n")
            append("• Fosfor: ${"%.1f".format(analysis.phosphorus)}\n")
            append("• Kalium: ${"%.1f".format(analysis.potassium)}\n")
            append("\n📋 Hasil Analisa:\n${analysis.analysisResult}\n\n")
            append("📱 DR.TANIKU - Agriculture Technology Solutions")
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Hasil Analisa Tanaman - ${analysis.plantName}")
        }

        startActivity(Intent.createChooser(shareIntent, "Bagikan Hasil Analisa"))
    }

    private fun showDeleteConfirmation(analysis: SavedAnalysis) {
        AlertDialog.Builder(this)
            .setTitle("🗑️ Hapus Analisa")
            .setMessage("Apakah Anda yakin ingin menghapus hasil analisa tanaman '${analysis.plantName}'?\n\nTindakan ini tidak dapat dibatalkan.")
            .setPositiveButton("Hapus") { _, _ ->
                deleteAnalysis(analysis)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteAnalysis(analysis: SavedAnalysis) {
        activityScope.launch {
            try {
                showLoading(true)

                val deletedRows = withContext(Dispatchers.IO) {
                    analysisDatabaseHelper.deleteAnalysis(analysis.id)
                }

                showLoading(false)

                if (deletedRows > 0) {
                    showToast("✅ Analisa berhasil dihapus")
                    // Reload data
                    loadSavedAnalyses()
                } else {
                    showToast("❌ Gagal menghapus analisa")
                }

            } catch (e: Exception) {
                showLoading(false)
                showToast("❌ Error menghapus analisa: ${e.message}")
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressLoading.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                val intent = Intent(this, HomeActivity::class.java)
                startActivity(intent)
                finish()
            }
            R.id.nav_saved_data -> {
                // Already here
                drawerLayout.closeDrawer(GravityCompat.START)
                return true
            }
            R.id.nav_search_data -> {
                val intent = Intent(this, SearchDataActivity::class.java)
                startActivity(intent)
                finish()
            }
            R.id.nav_location_details -> {
                val intent = Intent(this, LocationDetailsActivity::class.java)
                startActivity(intent)
                finish()
            }
            R.id.nav_logout -> {
                val intent = Intent(this, QRLoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}