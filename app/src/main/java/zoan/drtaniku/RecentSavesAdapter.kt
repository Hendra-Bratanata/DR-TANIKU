package zoan.drtaniku

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.RecyclerView

/**
 * RecentSavesAdapter - RecyclerView adapter for displaying recent saved data
 *
 * Features:
 * - Display up to 10 recent saved data items
 * - Each item shows timestamp and filename
 * - View button to show data details
 * - Click listener for item interactions
 */
class RecentSavesAdapter(
    private val context: Context,
    private var saves: List<DataSaveManager.SavedDataInfo>,
    private val onViewClick: (DataSaveManager.SavedDataInfo) -> Unit
) : RecyclerView.Adapter<RecentSavesAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textTimestamp: TextView = itemView.findViewById(R.id.text_save_timestamp)
        val textFilename: TextView = itemView.findViewById(R.id.text_save_filename)
        val btnView: AppCompatButton = itemView.findViewById(R.id.btn_view_save)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_save, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val save = saves[position]

        holder.textTimestamp.text = save.timestamp
        holder.textFilename.text = save.filename

        holder.btnView.setOnClickListener {
            onViewClick(save)
        }

        // Set click listener for the entire item
        holder.itemView.setOnClickListener {
            onViewClick(save)
        }
    }

    override fun getItemCount(): Int = saves.size

    /**
     * Update the list of saves
     */
    fun updateSaves(newSaves: List<DataSaveManager.SavedDataInfo>) {
        saves = newSaves
        notifyDataSetChanged()
    }

    /**
     * Get the current list of saves
     */
    fun getSaves(): List<DataSaveManager.SavedDataInfo> = saves
}