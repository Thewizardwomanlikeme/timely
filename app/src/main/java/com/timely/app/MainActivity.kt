package com.timely.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat

class MainActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository

    // Views
    private lateinit var chipGroupDays: ChipGroup
    private lateinit var tilStartTime: android.view.ViewGroup
    private lateinit var tilEndTime: android.view.ViewGroup
    private lateinit var tvStartTime: TextView
    private lateinit var tvEndTime: TextView
    private lateinit var seekBarInterval: SeekBar
    private lateinit var tvIntervalValue: TextView
    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvStatusDot: View

    // Notification permission launcher (Android 13+)
    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startTimelyService()
            } else {
                Toast.makeText(
                    this,
                    "Notification permission is needed to show the timer status bar.",
                    Toast.LENGTH_LONG
                ).show()
                // Still start — service will work even without the notification on older APIs
                startTimelyService()
            }
        }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = SettingsRepository(this)

        findViews()
        buildDayChips()
        wireListeners()
        loadSettings()
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        // Sync running state in case service was stopped from the notification
        refreshUi()
    }

    // -------------------------------------------------------------------------
    // View setup
    // -------------------------------------------------------------------------
    private fun findViews() {
        chipGroupDays   = findViewById(R.id.chipGroupDays)
        tilStartTime    = findViewById(R.id.tvStartTime)
        tilEndTime      = findViewById(R.id.tvEndTime)
        tvStartTime     = findViewById(R.id.tvStartTimeValue)
        tvEndTime       = findViewById(R.id.tvEndTimeValue)
        seekBarInterval = findViewById(R.id.seekBarInterval)
        tvIntervalValue = findViewById(R.id.tvIntervalValue)
        btnStart        = findViewById(R.id.btnStart)
        btnStop         = findViewById(R.id.btnStop)
        tvStatus        = findViewById(R.id.tvStatus)
        tvStatusDot     = findViewById(R.id.viewStatusDot)
    }

    private fun buildDayChips() {
        val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        chipGroupDays.removeAllViews()

        val selectedDays = settings.selectedDays
        for (i in 1..7) {
            val chip = Chip(this).apply {
                id          = i
                text        = dayLabels[i - 1]
                isCheckable = true
                isChecked   = i in selectedDays
                setTextColor(resources.getColorStateList(R.color.chip_text_color, theme))
                chipBackgroundColor = resources.getColorStateList(R.color.chip_bg_color, theme)
            }
            chipGroupDays.addView(chip)
        }

        // Attach listener after initial state is set to avoid spurious saves
        chipGroupDays.setOnCheckedStateChangeListener { _, checkedIds ->
            settings.selectedDays = checkedIds.toSet()
        }
    }

    private fun wireListeners() {
        // Time pickers — tap anywhere on the tile
        tilStartTime.setOnClickListener { showTimePicker(isStart = true) }
        tilEndTime.setOnClickListener   { showTimePicker(isStart = false) }

        // Interval seek bar (1–120 min; value = progress + 1)
        seekBarInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val mins = progress + 1
                if (fromUser) settings.intervalMinutes = mins
                updateIntervalLabel(mins)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        btnStart.setOnClickListener { requestPermissionThenStart() }
        btnStop.setOnClickListener  { stopTimelyService() }
    }

    private fun loadSettings() {
        updateTimeDisplays()
        seekBarInterval.progress = (settings.intervalMinutes - 1).coerceIn(0, 119)
        updateIntervalLabel(settings.intervalMinutes)
    }

    // -------------------------------------------------------------------------
    // Time picker
    // -------------------------------------------------------------------------
    private fun showTimePicker(isStart: Boolean) {
        val hour   = if (isStart) settings.startHour   else settings.endHour
        val minute = if (isStart) settings.startMinute else settings.endMinute
        val title  = if (isStart) "Start Time" else "End Time"

        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText(title)
            .build()

        picker.addOnPositiveButtonClickListener {
            if (isStart) {
                settings.startHour   = picker.hour
                settings.startMinute = picker.minute
            } else {
                settings.endHour   = picker.hour
                settings.endMinute = picker.minute
            }
            updateTimeDisplays()
        }

        picker.show(supportFragmentManager, if (isStart) "start_picker" else "end_picker")
    }

    // -------------------------------------------------------------------------
    // Service control
    // -------------------------------------------------------------------------
    private fun requestPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startTimelyService()
    }

    private fun startTimelyService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, TimerService::class.java)
        )
        settings.isRunning = true
        refreshUi()
    }

    private fun stopTimelyService() {
        stopService(Intent(this, TimerService::class.java))
        settings.isRunning = false
        refreshUi()
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------
    private fun refreshUi() {
        val running = settings.isRunning
        btnStart.isEnabled = !running
        btnStop.isEnabled  = running
        btnStart.alpha     = if (running) 0.5f else 1.0f
        btnStop.alpha      = if (running) 1.0f else 0.5f

        if (running) {
            tvStatus.text = "Active"
            tvStatus.setTextColor(getColor(R.color.colorActive))
            tvStatusDot.setBackgroundResource(R.drawable.bg_status_dot_active)
        } else {
            tvStatus.text = "Inactive"
            tvStatus.setTextColor(getColor(R.color.colorInactive))
            tvStatusDot.setBackgroundResource(R.drawable.bg_status_dot_inactive)
        }
    }

    private fun updateTimeDisplays() {
        tvStartTime.text = formatTime(settings.startHour, settings.startMinute)
        tvEndTime.text   = formatTime(settings.endHour,   settings.endMinute)
    }

    private fun updateIntervalLabel(mins: Int) {
        tvIntervalValue.text = if (mins == 1) "Every 1 minute" else "Every $mins minutes"
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val period = if (hour < 12) "AM" else "PM"
        val h = when {
            hour == 0  -> 12
            hour > 12  -> hour - 12
            else       -> hour
        }
        return "%d:%02d %s".format(h, minute, period)
    }
}
