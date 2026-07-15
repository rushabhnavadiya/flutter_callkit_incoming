//  Modification by Signify in this file are under the following license:
//
//  Copyright 2024, Signify Holding
//  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//  The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.hiennv.flutter_callkit_incoming

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.DrawableCompat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import android.view.ViewGroup.MarginLayoutParams
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log

class CallkitIncomingActivity : AppCompatActivity() {

    companion object {

        private const val ACTION_ENDED_CALL_INCOMING =
            "com.hiennv.flutter_callkit_incoming.ACTION_ENDED_CALL_INCOMING"

        fun getIntent(context: Context, data: Bundle) =
            Intent(CallkitConstants.ACTION_CALL_INCOMING).apply {
                setClassName(context.packageName, "com.hiennv.flutter_callkit_incoming.CallkitIncomingActivity")
                action = "${context.packageName}.${CallkitConstants.ACTION_CALL_INCOMING}"
                putExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA, data)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                `package` = context.packageName
            }

        fun getIntentEnded(context: Context, isAccepted: Boolean): Intent {
            val intent = Intent("${context.packageName}.${ACTION_ENDED_CALL_INCOMING}")
            intent.putExtra("ACCEPTED", isAccepted)
            intent.setPackage(context.packageName)
            intent.setClassName(
                context.packageName,
                "com.hiennv.flutter_callkit_incoming.CallkitIncomingActivity"
            )
            return intent
        }
    }

    inner class EndedCallkitIncomingBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!isFinishing) {
                val isAccepted = intent.getBooleanExtra("ACCEPTED", false)
                if (isAccepted) {
                    finishDelayed()
                } else {
                    finishTask()
                }
            }
        }
    }

    private var endedCallkitIncomingBroadcastReceiver = EndedCallkitIncomingBroadcastReceiver()

    private lateinit var tvGroupAvatar: TextView
    private lateinit var tvGroupName: TextView
    private lateinit var tvMainMessage: TextView
    private lateinit var tvSubMessage: TextView
    private lateinit var tvPriorityLabel: TextView
    private lateinit var tvSenderFooter: TextView
    private lateinit var btnViewDetails: MaterialButton
    private lateinit var btnIgnore: MaterialButton
    private lateinit var ringIconWrap: FrameLayout
    private lateinit var pulseRingOuter: View
    private lateinit var pulseRingMiddle: View
    private lateinit var pulseRingInner: View
    private lateinit var pulseAnimatorSet: AnimatorSet
    private var outerAnimatorSet: AnimatorSet? = null
    private lateinit var bgGlow: View
    private var bgGlowAnimator: ObjectAnimator? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.canUseFullScreenIntent()) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    flags = FLAG_ACTIVITY_NEW_TASK
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                })
            }
        }
        requestedOrientation = if (!Utils.isTablet(this@CallkitIncomingActivity)) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setTurnScreenOn(true)
            setShowWhenLocked(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        transparentStatusAndNavigation()
        setContentView(R.layout.activity_callkit_incoming)
        initView()
        incomingData(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                endedCallkitIncomingBroadcastReceiver,
                IntentFilter("${packageName}.${ACTION_ENDED_CALL_INCOMING}"),
                Context.RECEIVER_EXPORTED,
            )
        } else {
            registerReceiver(
                endedCallkitIncomingBroadcastReceiver,
                IntentFilter("${packageName}.${ACTION_ENDED_CALL_INCOMING}")
            )
        }
        FlutterCallkitIncomingPlugin.getInstance()?.getCallkitSoundPlayerManager()?.keepRingingOnFullScreen();
    }

    private fun wakeLockRequest(duration: Long) {

        val pm = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Callkit:PowerManager"
        )
        wakeLock.acquire(duration)
    }

    private fun transparentStatusAndNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            setWindowFlag(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                        or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, true
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setWindowFlag(
                (WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                        or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION), false
            )
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    private fun setWindowFlag(bits: Int, on: Boolean) {
        val win: Window = window
        val winParams: WindowManager.LayoutParams = win.attributes
        if (on) {
            winParams.flags = winParams.flags or bits
        } else {
            winParams.flags = winParams.flags and bits.inv()
        }
        win.attributes = winParams
    }


    private fun incomingData(intent: Intent) {
        val data = intent.extras?.getBundle(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA)
        if (data == null) finish()

        val isShowFullLockedScreen =
            data?.getBoolean(CallkitConstants.EXTRA_CALLKIT_IS_SHOW_FULL_LOCKED_SCREEN, true)
        if (isShowFullLockedScreen == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            }
        }

        val groupName = data?.getString(CallkitConstants.EXTRA_CALLKIT_NAME_CALLER, "Group") ?: "Group"
        val mainMessage = data?.getString(CallkitConstants.EXTRA_CALLKIT_HANDLE, "ALERT") ?: "ALERT"
        
        val extra = data?.getSerializable(CallkitConstants.EXTRA_CALLKIT_EXTRA) as? Map<*, *>
        
        var groupInitials = extra?.get("groupInitials") as? String ?: ""
        if (groupInitials.isEmpty()) {
            groupInitials = groupName.take(2).uppercase()
        }
        
        var subMessage = extra?.get("extraSubMessage") as? String ?: ""
        if (subMessage.isEmpty()) {
            subMessage = "Tap View Details to see more."
        }
        
        var priorityLabel = extra?.get("extraPriorityLabel") as? String ?: ""
        if (priorityLabel.isEmpty()) {
            priorityLabel = "CRITICAL"
        }
        
        var senderFooter = extra?.get("extraSenderFooter") as? String ?: ""
        if (senderFooter.isEmpty()) {
            senderFooter = "Sent by Tapp"
        }

        tvGroupAvatar.text = groupInitials
        tvGroupName.text = groupName
        tvMainMessage.text = mainMessage
        tvSubMessage.text = subMessage
        tvPriorityLabel.text = priorityLabel

        // Formatting sender footer with SpannableStringBuilder to match mockup exactly
        val prefix = "Sent by "
        if (senderFooter.startsWith(prefix)) {
            val name = senderFooter.substring(prefix.length)
            val spannable = android.text.SpannableStringBuilder()
                .append(prefix, android.text.style.ForegroundColorSpan(Color.parseColor("#6E6E6E")), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                .append(name, android.text.style.ForegroundColorSpan(Color.parseColor("#FFFFFF")), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            spannable.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                prefix.length,
                spannable.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            tvSenderFooter.text = spannable
        } else {
            tvSenderFooter.text = senderFooter
        }

        val duration = data?.getLong(CallkitConstants.EXTRA_CALLKIT_DURATION, 0L) ?: 0L
        wakeLockRequest(duration)

        finishTimeout(data, duration)
    }

    private fun finishTimeout(data: Bundle?, duration: Long) {
        val currentSystemTime = System.currentTimeMillis()
        val timeStartCall =
            data?.getLong(CallkitNotificationManager.EXTRA_TIME_START_CALL, currentSystemTime)
                ?: currentSystemTime

        val timeOut = duration - abs(currentSystemTime - timeStartCall)
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                val intent = CallkitIncomingBroadcastReceiver.getIntentTimeout(this@CallkitIncomingActivity, data)
                sendBroadcast(intent)
                finishTask()
            }
        }, timeOut)
    }

    private fun initView() {
        tvGroupAvatar = findViewById(R.id.tv_group_avatar)
        tvGroupName = findViewById(R.id.tv_group_name)
        tvMainMessage = findViewById(R.id.tv_main_message)
        tvSubMessage = findViewById(R.id.tv_sub_message)
        tvPriorityLabel = findViewById(R.id.tv_priority_label)
        tvSenderFooter = findViewById(R.id.tv_sender_footer)
        btnViewDetails = findViewById(R.id.btn_view_details)
        btnIgnore = findViewById(R.id.btn_ignore)
        ringIconWrap = findViewById(R.id.ring_icon_wrap)
        pulseRingOuter = findViewById(R.id.pulse_ring_outer)
        pulseRingMiddle = findViewById(R.id.pulse_ring_middle)
        pulseRingInner = findViewById(R.id.pulse_ring_inner)
        bgGlow = findViewById(R.id.bg_glow)

        startRingPulseAnimation()
        startBackgroundGlowAnimation()

        btnViewDetails.setOnClickListener {
            onAcceptClick()
        }
        btnIgnore.setOnClickListener {
            onDeclineClick()
        }
        val btnClose: ImageButton = findViewById(R.id.btn_close)
        btnClose.setOnClickListener {
            onDeclineClick()
        }
    }

    private fun startBackgroundGlowAnimation() {
        bgGlowAnimator = ObjectAnimator.ofFloat(bgGlow, "alpha", 0.5f, 1.0f).apply {
            duration = 2000 // Slow ambient breathe
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }
        bgGlowAnimator?.start()
    }

    private fun startRingPulseAnimation() {
        // Middle ring animators (scale and fade)
        val middleScaleX = ObjectAnimator.ofFloat(pulseRingMiddle, "scaleX", 1.0f, 1.8f)
        val middleScaleY = ObjectAnimator.ofFloat(pulseRingMiddle, "scaleY", 1.0f, 1.8f)
        val middleAlpha = ObjectAnimator.ofFloat(pulseRingMiddle, "alpha", 1.0f, 0.0f)
        
        pulseAnimatorSet = AnimatorSet().apply {
            playTogether(middleScaleX, middleScaleY, middleAlpha)
            duration = 1200
            interpolator = LinearInterpolator()
        }
        pulseAnimatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (!isFinishing) pulseAnimatorSet.start()
            }
        })
        pulseAnimatorSet.start()

        // Outer ring animators (scale and fade) started with delay
        val outerScaleX = ObjectAnimator.ofFloat(pulseRingOuter, "scaleX", 1.0f, 1.8f)
        val outerScaleY = ObjectAnimator.ofFloat(pulseRingOuter, "scaleY", 1.0f, 1.8f)
        val outerAlpha = ObjectAnimator.ofFloat(pulseRingOuter, "alpha", 1.0f, 0.0f)
        
        val outerAnim = AnimatorSet().apply {
            playTogether(outerScaleX, outerScaleY, outerAlpha)
            duration = 1200
            interpolator = LinearInterpolator()
        }
        outerAnim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (!isFinishing) outerAnim.start()
            }
        })

        // Start the second wave 600ms after the first
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                outerAnim.start()
            }
        }, 600)

        this.outerAnimatorSet = outerAnim
    }


    private fun onAcceptClick() {
        // Log.d("CallkitIncomingActivity", "[CALLKIT] 📱 onAcceptClick")
        val data = intent.extras?.getBundle(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA)


        CallkitNotificationService.startServiceWithAction(
            this@CallkitIncomingActivity,
            CallkitConstants.ACTION_CALL_ACCEPT,
            data
        )


        val acceptIntent =
            TransparentActivity.getIntent(this, CallkitConstants.ACTION_CALL_ACCEPT, data)
        startActivity(acceptIntent)

        dismissKeyguard()
        finish()
    }

    private fun dismissKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }

    private fun onDeclineClick() {
        // Log.d("CallkitIncomingActivity", "[CALLKIT] 📱 onDeclineClick")
        val data = intent.extras?.getBundle(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA)

        val intent =
            CallkitIncomingBroadcastReceiver.getIntentDecline(this@CallkitIncomingActivity, data)
        sendBroadcast(intent)
        finishTask()
    }

    private fun finishDelayed() {
        Handler(Looper.getMainLooper()).postDelayed({
            finishTask()
        }, 1000)
    }

    private fun finishTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        if (::pulseAnimatorSet.isInitialized) {
            pulseAnimatorSet.cancel()
        }
        outerAnimatorSet?.cancel()
        bgGlowAnimator?.cancel()
        unregisterReceiver(endedCallkitIncomingBroadcastReceiver)
        super.onDestroy()
    }

    // Start Signify modification
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val soundPlayerManager = FlutterCallkitIncomingPlugin.getInstance()?.getCallkitSoundPlayerManager()
            if (soundPlayerManager?.isPlaying == true) {
                soundPlayerManager.stop()
                return true 
            }
        }
        return super.onKeyDown(keyCode, event)
    }
    // End Signify modification

    override fun onBackPressed() {}
}
