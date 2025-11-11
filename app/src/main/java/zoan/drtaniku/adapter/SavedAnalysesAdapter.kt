package zoan.drtaniku.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import zoan.drtaniku.R
import zoan.drtaniku.model.SavedAnalysis

class SavedAnalysesAdapter(
    private var analyses: List<SavedAnalysis>,
    private val onViewClick: (SavedAnalysis) -> Unit,
    private val onDeleteClick: (SavedAnalysis) -> Unit
) : RecyclerView.Adapter<SavedAnalysesAdapter.SavedAnalysisViewHolder>() {

    inner class SavedAnalysisViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textPlantName: TextView = itemView.findViewById(R.id.text_plant_name)
        val textTimestamp: TextView = itemView.findViewById(R.id.text_timestamp)
        val textLocation: TextView = itemView.findViewById(R.id.text_location)
        val textTemperature: TextView = itemView.findViewById(R.id.text_temperature)
        val textHumidity: TextView = itemView.findViewById(R.id.text_humidity)
        val textPh: TextView = itemView.findViewById(R.id.text_ph)
        val textNitrogen: TextView = itemView.findViewById(R.id.text_nitrogen)
        val textPhosphorus: TextView = itemView.findViewById(R.id.text_phosphorus)
        val textPotassium: TextView = itemView.findViewById(R.id.text_potassium)
        val textAnalysisPreview: TextView = itemView.findViewById(R.id.text_analysis_preview)
        val btnViewDetails: ImageButton = itemView.findViewById(R.id.btn_view_details)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedAnalysisViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_analysis, parent, false)
        return SavedAnalysisViewHolder(view)
    }

    override fun onBindViewHolder(holder: SavedAnalysisViewHolder, position: Int) {
        val analysis = analyses[position]

        holder.textPlantName.text = "🌱 ${analysis.plantName}"
        holder.textTimestamp.text = "📅 ${analysis.getFormattedTimestamp()}"

        // Show location if available
        if (analysis.location.isNotEmpty()) {
            holder.textLocation.visibility = View.VISIBLE
            holder.textLocation.text = "📍 ${analysis.location}"
        } else {
            holder.textLocation.visibility = View.GONE
        }

        // Sensor data
        holder.textTemperature.text = "🌡️ ${"%.1f".format(analysis.temperature)}°C"
        holder.textHumidity.text = "💧 ${"%.1f".format(analysis.humidity)}%"
        holder.textPh.text = "⚗️ ${"%.1f".format(analysis.ph)}"
        holder.textNitrogen.text = "🧪 N: ${"%.1f".format(analysis.nitrogen)}"
        holder.textPhosphorus.text = "🧪 P: ${"%.1f".format(analysis.phosphorus)}"
        holder.textPotassium.text = "🧪 K: ${"%.1f".format(analysis.potassium)}"

        // Analysis preview (limit to first 150 characters)
        val previewText = if (analysis.analysisResult.length > 150) {
            "${analysis.analysisResult.take(150)}..."
        } else {
            analysis.analysisResult
        }
        holder.textAnalysisPreview.text = previewText

        // Click listeners
        holder.btnViewDetails.setOnClickListener {
            onViewClick(analysis)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(analysis)
        }
    }

    override fun getItemCount(): Int = analyses.size

    fun updateAnalyses(newAnalyses: List<SavedAnalysis>) {
        analyses = newAnalyses
        notifyDataSetChanged()
    }

    fun getAnalysisAtPosition(position: Int): SavedAnalysis {
        return analyses[position]
    }
}