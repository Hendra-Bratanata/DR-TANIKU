package zoan.drtaniku.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import zoan.drtaniku.R
import zoan.drtaniku.model.SavedAnalysis

class RecentAnalysesAdapter(
    private var analyses: List<SavedAnalysis>,
    private val onViewClick: (SavedAnalysis) -> Unit
) : RecyclerView.Adapter<RecentAnalysesAdapter.RecentAnalysisViewHolder>() {

    inner class RecentAnalysisViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textPlantName: TextView = itemView.findViewById(R.id.text_plant_name)
        val textTimestamp: TextView = itemView.findViewById(R.id.text_timestamp)
        val textPreview: TextView = itemView.findViewById(R.id.text_analysis_preview)
        val textSensorSummary: TextView = itemView.findViewById(R.id.text_sensor_summary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentAnalysisViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_analysis, parent, false)
        return RecentAnalysisViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecentAnalysisViewHolder, position: Int) {
        val analysis = analyses[position]

        holder.textPlantName.text = "🌱 ${analysis.plantName}"
        holder.textTimestamp.text = "📅 ${analysis.getFormattedDate()}"

        // Sensor summary
        val sensorSummary = "🌡️ ${"%.1f".format(analysis.temperature)}°C | " +
                           "💧 ${"%.1f".format(analysis.humidity)}% | " +
                           "⚗️ pH ${"%.1f".format(analysis.ph)}"
        holder.textSensorSummary.text = sensorSummary

        // Analysis preview (limit to first 100 characters)
        val previewText = if (analysis.analysisResult.length > 100) {
            "${analysis.analysisResult.take(100)}..."
        } else {
            analysis.analysisResult
        }
        holder.textPreview.text = previewText

        // Click listener
        holder.itemView.setOnClickListener {
            onViewClick(analysis)
        }
    }

    override fun getItemCount(): Int = analyses.size

    fun updateAnalyses(newAnalyses: List<SavedAnalysis>) {
        analyses = newAnalyses
        notifyDataSetChanged()
    }
}