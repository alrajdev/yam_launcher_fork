package eu.ottop.yamlauncher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.marginLeft
import androidx.recyclerview.widget.RecyclerView
import eu.ottop.yamlauncher.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.lang.reflect.Method


class MainActivity : AppCompatActivity(), AppMenuAdapter.OnItemClickListener, AppMenuAdapter.OnShortcutListener, AppMenuAdapter.OnItemLongClickListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var gestureDetector: GestureDetector
    private lateinit var shortcutGestureDetector: GestureDetector
    private lateinit var launcherApps: LauncherApps
    private lateinit var installedApps: List<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: EditText
    private lateinit var adapter: AppMenuAdapter
    private var job: Job? = null
    val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE)
    val phoneIntent = Intent(Intent.ACTION_DIAL)
    private lateinit var batteryReceiver: BatteryReceiver

    private var appActionMenu = AppActionMenu()
    private val sharedPreferenceManager = SharedPreferenceManager()
    private val appUtils = AppUtils()
    private val appMenuLinearLayoutManager = AppMenuLinearLayoutManager(this@MainActivity)
    private val appMenuEdgeFactory = AppMenuEdgeFactory(this@MainActivity)
    private val animations = Animations()

    private val swipeThreshold = 100
    private val swipeVelocityThreshold = 100

    private lateinit var clock: TextClock
    private var clockMargin = 0
    private lateinit var constraintLayout: ConstraintLayout

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(null)

        launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

        gestureDetector = GestureDetector(this, GestureListener())
        shortcutGestureDetector = GestureDetector(this, TextGestureListener())

        clock = findViewById(R.id.text_clock)

        clockMargin = clock.marginLeft

        constraintLayout = findViewById(R.id.clock_layout)

        setupApps()

        val dateText = findViewById<TextClock>(R.id.text_date)

        batteryReceiver = BatteryReceiver.register(this, dateText)

        binding.homeView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            super.onTouchEvent(event)
            true // Return true if the touch event is handled
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                backToHome()
            }
        })
    }

    override fun onNewIntent(intent: Intent?) {
        backToHome()
        super.onNewIntent(intent)

    }

    override fun onStop() {
        super.onStop()
        job?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        unregisterReceiver(batteryReceiver)
    }

    override fun onStart() {
        super.onStart()
        startTask()

        // Keyboard is sometimes open when going back to the app, so close it.
        closeKeyboard()
    }

    override fun onResume() {
        super.onResume()
        setClockAlignment()
    }

    open inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        @SuppressLint("WrongConstant")
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 != null) {
                val deltaY = e2.y - e1.y
                val deltaX = e2.x - e1.x

                // Detect swipe up
                if (deltaY < -swipeThreshold && abs(velocityY) > swipeVelocityThreshold) {
                    openAppMenuActivity()
                }

                // Detect swipe down
                else if (deltaY > swipeThreshold && abs(velocityY) > swipeVelocityThreshold) {
                    val statusBarService = getSystemService(Context.STATUS_BAR_SERVICE)
                    val statusBarManager: Class<*> = Class.forName("android.app.StatusBarManager")
                    val expandMethod: Method = statusBarManager.getMethod("expandNotificationsPanel")
                    expandMethod.invoke(statusBarService)
                }

                // Detect swipe left
                else if (deltaX < -swipeThreshold && abs(velocityX) > swipeVelocityThreshold){
                    startActivity(cameraIntent)
                }

                // Detect swipe right
                else if (deltaX > -swipeThreshold && abs(velocityX) > swipeVelocityThreshold) {
                    startActivity(phoneIntent)
                }
            }
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            super.onLongPress(e)
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }

    }

    inner class TextGestureListener : GestureListener() {
        override fun onLongPress(e: MotionEvent) {

        }
    }

    private fun setupApps() {
            handleListItems()
        CoroutineScope(Dispatchers.Default).launch {
            installedApps = appUtils.getInstalledApps(this@MainActivity)
            val newApps = installedApps.toMutableList()

            setupRecyclerView(newApps)

            searchView = findViewById(R.id.searchView)
            setupSearch()
        }



    }

    private fun handleListItems() {
        for (i in arrayOf(R.id.app1, R.id.app2, R.id.app3, R.id.app4, R.id.app5, R.id.app6, R.id.app7, R.id.app8)) {

            val textView = findViewById<TextView>(i)

            unselectedSetup(textView)

            val savedView = sharedPreferenceManager.getShortcut(this, textView)

            if (savedView?.get(1) != "e") {
                selectedSetup(textView, savedView)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun unselectedSetup(textView: TextView) {
        textView.setOnTouchListener {_, event ->
            shortcutGestureDetector.onTouchEvent(event)
            super.onTouchEvent(event)
        }

        textView.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(resources, R.drawable.ic_empty, null),null,null,null)

        textView.compoundDrawablePadding = 0
        unselectedListeners(textView)
    }

    private fun selectedSetup(textView: TextView, savedView: List<String>?) {
        if (savedView?.get(1) != "0") {
            textView.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(resources, R.drawable.ic_work_app, null),null,null,null)
        }
        else {
            textView.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(resources, R.drawable.ic_empty, null),null,null,null)
        }
        textView.text = savedView?.get(2)
        selectedListeners(textView, savedView)
    }

    private fun unselectedListeners(textView: TextView) {
        textView.setOnClickListener {
            Toast.makeText(this, "Long click to select an app", Toast.LENGTH_SHORT).show()
        }
        textView.setOnLongClickListener {
            adapter.menuMode = "shortcut"
            adapter.shortcutTextView = textView
            toAppMenu()

            return@setOnLongClickListener true
        }
    }

    private fun selectedListeners(textView: TextView, savedView: List<String>?) {
        textView.setOnClickListener {
            val mainActivity = launcherApps.getActivityList(savedView?.get(0).toString(), launcherApps.profiles[savedView?.get(1)!!.toInt()]).firstOrNull()
            if (mainActivity != null) {
                launcherApps.startMainActivity(mainActivity.componentName,  launcherApps.profiles[savedView[1].toInt()], null, null)
            } else {
                Toast.makeText(this, "Cannot launch app", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun setupRecyclerView(newApps: MutableList<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>) {
        adapter = AppMenuAdapter(this@MainActivity, newApps, this@MainActivity, this@MainActivity, this@MainActivity)
        appMenuLinearLayoutManager.stackFromEnd = true
        recyclerView = findViewById(R.id.recycler_view)
        withContext(Dispatchers.Main) {
            recyclerView.layoutManager = appMenuLinearLayoutManager
            recyclerView.edgeEffectFactory = appMenuEdgeFactory
            recyclerView.adapter = adapter
            recyclerView.scrollToPosition(0)
        }

        setupRecyclerListener()
    }

    private fun setupRecyclerListener() {
        recyclerView.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    appMenuLinearLayoutManager.setScrollInfo()
                }
            }
        })
    }

    private fun setupSearch() {
        recyclerView.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->

            if (bottom - top > oldBottom - oldTop) {

                searchView.clearFocus()

                if (searchView.text.isNullOrEmpty()) {
                    startTask()
                }
            }
            else if (bottom - top < oldBottom - oldTop) {
                job?.cancel()
            }
        }

        searchView.addTextChangedListener(object :
            TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                CoroutineScope(Dispatchers.Default).launch {
                    filterItems(searchView.text.toString())
                }
            }
        })
    }

    private suspend fun filterItems(query: String?) {

            val cleanQuery = query?.clean()
            val newFilteredApps = mutableListOf<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>()
            val updatedApps = appUtils.getInstalledApps(this@MainActivity)

            getFilteredApps(cleanQuery, newFilteredApps, updatedApps)

            applySearch(newFilteredApps)

    }

    private suspend fun getFilteredApps(cleanQuery: String?, newFilteredApps: MutableList<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>, updatedApps: List<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>) {
        if (cleanQuery.isNullOrEmpty()) {
            refreshAppMenu()
            newFilteredApps.addAll(installedApps)
        } else {
            updatedApps.forEach {
                val cleanItemText = sharedPreferenceManager.getAppName(this@MainActivity, it.first.applicationInfo.packageName, it.second.second, packageManager.getApplicationLabel(it.first.applicationInfo)).toString().clean()
                if (cleanItemText.contains(cleanQuery, ignoreCase = true)) {
                    newFilteredApps.add(it)
                }
            }
        }
    }

    private suspend fun applySearch(newFilteredApps: MutableList<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>) {
        if (!listsEqual(installedApps, newFilteredApps)) {
            withContext(Dispatchers.Main) {
                updateMenu(newFilteredApps)
            }
            installedApps = newFilteredApps
        }
    }

    private fun String.clean(): String {
        return this.replace("[^a-zA-Z0-9]".toRegex(), "")
    }

    private fun startTask() {
        job?.cancel()
        job = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                refreshAppMenu()
                delay(5000)
            }
        }
    }

    fun openAppMenuActivity() {
        adapter.menuMode = "app"
        binding.menutitle.visibility = View.GONE
        toAppMenu()
    }
    
    fun backToHome() {
        closeKeyboard()
        animations.showHome(binding)
        animations.backgroundOut(this@MainActivity)
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            try {
                binding.menutitle.visibility = View.VISIBLE
                searchView.setText("")
            }
            catch (_: UninitializedPropertyAccessException) {

            }
        }, 100)
        handler.postDelayed({
            CoroutineScope(Dispatchers.Default).launch {
                refreshAppMenu()

            try {
                withContext(Dispatchers.Main) {
                    recyclerView.scrollToPosition(0)
                }
            }
            catch (_: UninitializedPropertyAccessException) {

            }
        }}, 150)

    }

    private fun toAppMenu() {
        animations.showApps(binding)
        animations.backgroundIn(this@MainActivity)
    }

    override fun onItemClick(appInfo: LauncherActivityInfo, userHandle: UserHandle) {
        val mainActivity = launcherApps.getActivityList(appInfo.applicationInfo.packageName, userHandle).firstOrNull()
        if (mainActivity != null) {
            launcherApps.startMainActivity(mainActivity.componentName, userHandle, null, null)
        } else {
            Toast.makeText(this, "Cannot launch app", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onShortcut(
        appInfo: LauncherActivityInfo,
        userHandle: UserHandle,
        textView: TextView,
        userProfile: Int,
        shortcutView: TextView
    ) {
        if (userProfile != 0) {
            shortcutView.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(resources, R.drawable.ic_work_app, null),null,null,null)
        }
        else {
            shortcutView.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(resources, R.drawable.ic_empty, null),null,null,null)
        }
        shortcutView.text = textView.text.toString()
        shortcutView.setOnClickListener {
            val mainActivity = launcherApps.getActivityList(appInfo.applicationInfo.packageName, userHandle).firstOrNull()
            if (mainActivity != null) {
                launcherApps.startMainActivity(mainActivity.componentName,  userHandle, null, null)
            } else {
                Toast.makeText(this, "Cannot launch app", Toast.LENGTH_SHORT).show()
            }
        }
        sharedPreferenceManager.setShortcut(this, shortcutView, appInfo.applicationInfo.packageName, userProfile)
        backToHome()
    }


    override fun onItemLongClick(
        appInfo: LauncherActivityInfo,
        userHandle: UserHandle,
        userProfile: Int,
        textView: TextView,
        actionMenuLayout: LinearLayout,
        editView: LinearLayout,
        position: Int
    ) {
        textView.visibility = View.INVISIBLE
        animations.fadeViewIn(actionMenuLayout)
        val mainActivity =
            launcherApps.getActivityList(appInfo.applicationInfo.packageName, userHandle)
                .firstOrNull()
        appActionMenu.setActionListeners(
            this@MainActivity,
            binding,
            textView,
            editView,
            actionMenuLayout,
            searchView,
            appInfo.applicationInfo,
            userHandle,
            userProfile,
            launcherApps,
            mainActivity,
            position
        )
    }

    suspend fun refreshAppMenu() {
            try {
                val updatedApps = appUtils.getInstalledApps(this@MainActivity)
                println("update running")
                if (!listsEqual(installedApps, updatedApps)) {
                    withContext(Dispatchers.Main) {
                        updateMenu(updatedApps)
                    }
                    installedApps = updatedApps
                }
            }
            catch (_: UninitializedPropertyAccessException) {
            }

        }

    private fun closeKeyboard() {
        val imm =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }



    @SuppressLint("NotifyDataSetChanged")
    private fun updateMenu(updatedApps : List<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>) {
        adapter.updateApps(updatedApps)
        println("moved")
    }

    private fun listsEqual(list1: List<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>, list2: List<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>): Boolean {
        if (list1.size != list2.size) return false

        for (i in list1.indices) {
            if (list1[i].first.componentName != list2[i].first.componentName || list1[i].second.first != list2[i].second.first) {
                return false
            }
        }

        return true
    }

    private fun setClockAlignment() {
        val clockAlignment = sharedPreferenceManager.getClockAlignment(this@MainActivity)

        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)
        println(clockAlignment)

        /*
        0 = left
        1 = center
        2 = right
        */

        if (clockAlignment == 2) {
            constraintSet.clear(clock.id, ConstraintSet.START)
        }
        else if (clockAlignment == 0) {
            constraintSet.clear(clock.id, ConstraintSet.END)
        }

        if (clockAlignment == 1 || clockAlignment == 0) {
            constraintSet.connect(
                clock.id,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START,
                clockMargin
            )
        }

        if (clockAlignment != 0) {
            constraintSet.connect(
                clock.id,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END,
                clockMargin
            )
        }

        constraintSet.applyTo(constraintLayout)
    }

    fun isJobActive(): Boolean {
        return if (job != null) {
            job!!.isActive
        } else {
            false
        }
    }
}