package zoan.drtaniku

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import zoan.drtaniku.utils.SessionManager

class SplashActivity : AppCompatActivity() {

    private val SPLASH_TIME_OUT: Long = 4000 // 4 seconds untuk loading progress
    private val PROGRESS_UPDATE_INTERVAL: Long = 50 // Update setiap 50ms
    private var currentProgress = 0

    private lateinit var progressBar: ProgressBar
    private lateinit var percentageText: TextView
    private lateinit var statusText: TextView
    private val handler = Handler(Looper.getMainLooper())

    // Loading steps untuk simulasi proses yang realistis
    private val loadingSteps = arrayOf(
        10 to "Inisialisasi aplikasi...",
        25 to "Memuat database sensor...",
        40 to "Mengkonfigurasi sensor...",
        55 to "Memuat data cuaca...",
        70 to "Menyiapkan antarmuka...",
        85 to "Mengoptimalkan performa...",
        100 to "Aplikasi siap digunakan!"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Check persistent session
        if (checkPersistentSession()) {
            // Already logged in, go directly to home
            navigateToHome()
            return
        }

        // Inisialisasi views
        initViews()

        // Setup animations
        setupAnimations()

        // Start loading progress
        startLoadingProgress()
    }

    private fun checkPersistentSession(): Boolean {
        return try {
            // Check if user is logged in
            val isLoggedIn = SessionManager.isLoggedIn(this)
            android.util.Log.d("SplashActivity", "Is logged in: $isLoggedIn")

            if (isLoggedIn) {
                // Check if session is expired
                if (SessionManager.autoLogoutIfExpired(this)) {
                    // Session expired, show message and continue with normal flow
                    Toast.makeText(this, "Session expired, please login again", Toast.LENGTH_LONG).show()
                    android.util.Log.d("SplashActivity", "Session expired, going to login")
                    return false
                } else {
                    // Valid session exists
                    android.util.Log.d("SplashActivity", "Valid session exists, going to home")
                    return true
                }
            }
            android.util.Log.d("SplashActivity", "No session found, going to login")
            false
        } catch (e: Exception) {
            // Error checking session, continue with normal flow
            android.util.Log.e("SplashActivity", "Error checking session: ${e.message}")
            false
        }
    }

    private fun initViews() {
        progressBar = findViewById(R.id.loading_progress)
        percentageText = findViewById(R.id.loading_percentage)
        statusText = findViewById(R.id.loading_status)

        // Set initial progress
        progressBar.progress = 0
        percentageText.text = "0%"
        statusText.text = "Memulai aplikasi..."
    }

    private fun startLoadingProgress() {
        val progressRunnable = object : Runnable {
            override fun run() {
                if (currentProgress <= 100) {
                    // Update progress
                    updateProgress(currentProgress)

                    // Increment progress
                    currentProgress += 1

                    // Schedule next update
                    handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
                } else {
                    // Loading complete, navigate to HomeActivity
                    navigateToHome()
                }
            }
        }

        // Start progress updates
        handler.post(progressRunnable)
    }

    private fun updateProgress(progress: Int) {
        // Update progress bar
        progressBar.progress = progress

        // Update percentage text
        percentageText.text = "$progress%"

        // Update status text based on loading steps
        for (i in loadingSteps.indices) {
            if (progress >= loadingSteps[i].first &&
                (i == loadingSteps.size - 1 || progress < loadingSteps[i + 1].first)) {
                statusText.text = loadingSteps[i].second
                break
            }
        }

        // Add animation effect when reaching milestones
        when (progress) {
            25, 50, 75, 100 -> {
                // Add a subtle pulse effect to percentage text
                val pulseAnimation = AlphaAnimation(0.7f, 1.0f)
                pulseAnimation.duration = 200
                pulseAnimation.interpolator = AccelerateDecelerateInterpolator()
                pulseAnimation.fillAfter = true
                percentageText.startAnimation(pulseAnimation)
            }
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)

        // Pass device info from session if available
        val deviceInfo = SessionManager.getDeviceInfo(this)
        deviceInfo?.let {
            intent.putExtra("device_imei", it.IMEI)
            intent.putExtra("device_location", it.Lokasi)
            intent.putExtra("device_address", it.Alamat)
            intent.putExtra("device_status", it.Status)
        }

        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun setupAnimations() {
        // DR Taniku logo animation
        val logoContainer = findViewById<ImageView>(R.id.agriculture_icon)
        val appTitle = findViewById<TextView>(R.id.app_title)
        val appTagline = findViewById<TextView>(R.id.app_tagline)

        // Enhanced fade in and scale up animation for DR Taniku logo
        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.duration = 1200
        fadeIn.fillAfter = true
        fadeIn.interpolator = AccelerateDecelerateInterpolator()

        val scaleUp = ScaleAnimation(
            0.3f, 1.0f,  // Start smaller for more dramatic effect
            0.3f, 1.0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        scaleUp.duration = 1200
        scaleUp.fillAfter = true
        scaleUp.interpolator = AccelerateDecelerateInterpolator()

        val logoAnimationSet = AnimationSet(true)
        logoAnimationSet.addAnimation(fadeIn)
        logoAnimationSet.addAnimation(scaleUp)
        logoContainer.startAnimation(logoAnimationSet)

        // Title animation (slightly delayed to let logo shine)
        val titleFadeIn = AlphaAnimation(0f, 1f)
        titleFadeIn.duration = 1000
        titleFadeIn.fillAfter = true
        titleFadeIn.startOffset = 1000
        titleFadeIn.interpolator = AccelerateDecelerateInterpolator()
        appTitle.startAnimation(titleFadeIn)

        // Tagline animation
        val taglineFadeIn = AlphaAnimation(0f, 1f)
        taglineFadeIn.duration = 800
        taglineFadeIn.fillAfter = true
        taglineFadeIn.startOffset = 1400
        taglineFadeIn.interpolator = AccelerateDecelerateInterpolator()
        appTagline.startAnimation(taglineFadeIn)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup handler to prevent memory leaks
        handler.removeCallbacksAndMessages(null)
    }
}