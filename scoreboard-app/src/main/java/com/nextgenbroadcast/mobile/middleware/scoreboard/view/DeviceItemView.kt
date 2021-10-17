package com.nextgenbroadcast.mobile.middleware.scoreboard.view

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.nextgenbroadcast.mobile.middleware.scoreboard.R
import com.nextgenbroadcast.mobile.middleware.scoreboard.entities.ChartData
import com.nextgenbroadcast.mobile.middleware.scoreboard.entities.TDataPoint
import kotlinx.coroutines.flow.Flow

class DeviceItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    lateinit var title: TextView
    lateinit var lostLabel: TextView
    lateinit var phyChart: PhyChart
    lateinit var errorText: TextView
    var isChartSelected = false

    override fun onFinishInflate() {
        super.onFinishInflate()

        title = findViewById(R.id.device_name_view)
        lostLabel = findViewById(R.id.device_lost_label)
        phyChart = findViewById(R.id.device_phy_chart)
        errorText = findViewById(R.id.device_error)
    }

    fun observe(data: ChartData) = with(data) {
        phyChart.setDataSource(
            PhyChart.MultiDataSource(
                primaryDataSources,
                secondaryDataSources,
                primaryChartConfiguration,
                secondaryChartConfiguration
            )
        )
    }

    override fun setOnClickListener(listener: OnClickListener?) {
        super.setOnClickListener(listener)
        phyChart.setOnClickListener(listener)
    }
}
