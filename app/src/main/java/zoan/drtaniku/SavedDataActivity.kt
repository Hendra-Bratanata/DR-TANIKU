package zoan.drtaniku

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * SavedDataActivity - Display all saved sensor data
 *
 * Features:
 * - List all saved data files
 * - View data content in dialog
 * - Delete saved data files
 * - Refresh list
 * - Search functionality
 */
class SavedDataActivity : AppCompatActivity() {

    private lateinit var dataSaveManager: DataSaveManager
    private lateinit var recyclerAllSaves: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var textTotalSaves: TextView
    private lateinit var toolbar: Toolbar
    private lateinit var allSavesAdapter: AllSavesAdapter
    private lateinit var fabRefresh: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_data)

        dataSaveManager = DataSaveManager(this)
        initializeViews()
        setupToolbar()
        setupRecyclerView()
        loadAllSavedData()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerAllSaves = findViewById(R.id.recycler_all_saves)
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        textTotalSaves = findViewById(R.id.text_total_saves)
        fabRefresh = findViewById(R.id.fab_refresh)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Saved Data"
        }

        fabRefresh.setOnClickListener {
            loadAllSavedData()
        }
    }

    private fun setupRecyclerView() {
        allSavesAdapter = AllSavesAdapter(
            context = this,
            saves = emptyList(),
            onViewClick = { savedData ->
                showDataDialog(savedData)
            },
            onDeleteClick = { savedData ->
                showDeleteDialog(savedData)
            }
        )

        recyclerAllSaves.apply {
            layoutManager = LinearLayoutManager(this@SavedDataActivity)
            adapter = allSavesAdapter
        }
    }

    private fun loadAllSavedData() {
        val allSaves = dataSaveManager.getAllSaves()
        allSavesAdapter.updateSaves(allSaves)

        // Update total count
        textTotalSaves.text = "Total: ${allSaves.size} save${if (allSaves.size != 1) "s" else ""}"

        if (allSaves.isEmpty()) {
            recyclerAllSaves.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        } else {
            recyclerAllSaves.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
        }
    }

    private fun showDataDialog(savedData: DataSaveManager.SavedDataInfo) {
        val dataContent = dataSaveManager.loadSavedData(savedData.filename)

        if (dataContent != null) {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_data_view, null)
            val textDataContent = dialogView.findViewById<TextView>(R.id.text_data_content)

            textDataContent.text = dataContent
            textDataContent.movementMethod = ScrollingMovementMethod()

            AlertDialog.Builder(this)
                .setTitle("Data: ${savedData.timestamp}")
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .setNegativeButton("Share") { _, _ ->
                    shareData(dataContent, savedData.filename)
                }
                .show()
        } else {
            Toast.makeText(this, "Failed to load data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteDialog(savedData: DataSaveManager.SavedDataInfo) {
        AlertDialog.Builder(this)
            .setTitle("Delete Data")
            .setMessage("Are you sure you want to delete this saved data?\n\n${savedData.timestamp}")
            .setPositiveButton("Delete") { _, _ ->
                if (dataSaveManager.deleteSavedData(savedData.filename)) {
                    Toast.makeText(this, "Data deleted successfully", Toast.LENGTH_SHORT).show()
                    loadAllSavedData()
                } else {
                    Toast.makeText(this, "Failed to delete data", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareData(dataContent: String, filename: String) {
        try {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, dataContent)
                putExtra(Intent.EXTRA_SUBJECT, "DR Taniku Sensor Data - $filename")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Sensor Data"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share data", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        loadAllSavedData() // Refresh data when activity resumes
    }
}

/**
 * AllSavesAdapter - RecyclerView adapter for displaying all saved data
 */
class AllSavesAdapter(
    private val context: android.content.Context,
    private var saves: List<DataSaveManager.SavedDataInfo>,
    private val onViewClick: (DataSaveManager.SavedDataInfo) -> Unit,
    private val onDeleteClick: (DataSaveManager.SavedDataInfo) -> Unit
) : RecyclerView.Adapter<AllSavesAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textTimestamp: TextView = itemView.findViewById(R.id.text_save_timestamp)
        val textFilename: TextView = itemView.findViewById(R.id.text_save_filename)
        val btnView: androidx.appcompat.widget.AppCompatButton = itemView.findViewById(R.id.btn_view_save)
        val btnDelete: androidx.appcompat.widget.AppCompatButton = itemView.findViewById(R.id.btn_delete_save)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_all_save, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val save = saves[position]

        holder.textTimestamp.text = save.timestamp
        holder.textFilename.text = save.filename

        holder.btnView.setOnClickListener {
            onViewClick(save)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(save)
        }

        // Set click listener for the entire item
        holder.itemView.setOnClickListener {
            onViewClick(save)
        }
    }

    override fun getItemCount(): Int = saves.size

    fun updateSaves(newSaves: List<DataSaveManager.SavedDataInfo>) {
        saves = newSaves
        notifyDataSetChanged()
    }
}