package eu.ottop.yamlauncher

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserHandle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.ottop.yamlauncher.databinding.ActivityAppMenuBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


class AppMenuActivity : AppCompatActivity(), AppMenuAdapter.OnItemClickListener, AppMenuAdapter.OnItemLongClickListener {

        private lateinit var binding: ActivityAppMenuBinding
        private lateinit var recyclerView: RecyclerView
        private lateinit var searchView: EditText
        private lateinit var adapter: AppMenuAdapter
        private lateinit var filteredApps: MutableList<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>
        private lateinit var installedApps: List<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>
        private lateinit var job: Job
        private var appActionMenu = AppActionMenu()
        private lateinit var launcherApps: LauncherApps

    private val sharedPreferenceManager = SharedPreferenceManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(null)
        launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        searchView = findViewById(R.id.searchView)

        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        installedApps = getInstalledApps()
        filteredApps = mutableListOf()
        adapter = AppMenuAdapter(installedApps, this, this)
        recyclerView.adapter = adapter

        setupSearch()
    }

    override fun onItemClick(appInfo: LauncherActivityInfo, userHandle: UserHandle) {
        val mainActivity = launcherApps.getActivityList(appInfo.applicationInfo.packageName, userHandle).firstOrNull()
        if (mainActivity != null) {
            launcherApps.startMainActivity(mainActivity.componentName, userHandle, null, null)
        } else {
            // Handle the case when launch intent is null (e.g., app cannot be launched)
            Toast.makeText(this, "Cannot launch app", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onItemLongClick(
        appInfo: LauncherActivityInfo,
        userHandle: UserHandle,
        userProfile: Int,
        textView: TextView,
        actionMenuLayout: LinearLayout,
        editView: LinearLayout
    ) {
        // Handle the long click action here, for example, show additional options or information about the app
        textView.visibility = View.INVISIBLE
        actionMenuLayout.visibility = View.VISIBLE
        val mainActivity = launcherApps.getActivityList(appInfo.applicationInfo.packageName, userHandle).firstOrNull()
        appActionMenu.setActionListeners(this@AppMenuActivity, CoroutineScope(Dispatchers.Main), binding, textView, editView, actionMenuLayout, searchView, appInfo.applicationInfo, userHandle, userProfile, launcherApps, mainActivity)

    }

    private fun setupSearch() {
        binding.root.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top > oldBottom - oldTop) {
                searchView.clearFocus()
            }
        }

        searchView.addTextChangedListener(object :
            TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterItems(searchView.text.toString())
            }

            override fun afterTextChanged(s: Editable?) {
            }

        })
    }

    private fun filterItems(query: String?) {
        val cleanQuery = query?.replace("[^a-zA-Z0-9]".toRegex(), "")
        filteredApps.clear()

        if (cleanQuery.isNullOrEmpty()) {
            filteredApps.addAll(installedApps)
        }

        else {
            installedApps.forEach {
                val cleanItemText = it.first.applicationInfo.loadLabel(packageManager).replace("[^a-zA-Z0-9]".toRegex(), "")
                if (cleanItemText.contains(cleanQuery, ignoreCase=true)) {
                    filteredApps.add(it)
                }
            }
        }

        adapter.updateApps(filteredApps)

    }

    private fun getInstalledApps(): List<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>> {
        val allApps = mutableListOf<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>()
        val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        for (i in launcherApps.profiles.indices) {
            launcherApps.getActivityList(null, launcherApps.profiles[i]).forEach { app ->
                if (!sharedPreferenceManager.isAppHidden(this@AppMenuActivity, app.applicationInfo.packageName, i)) {
                    allApps.add(Pair(app, Pair(launcherApps.profiles[i], i)))
                }
            }
        }
        return allApps.sortedBy {
            it.first.applicationInfo.loadLabel(packageManager).toString().lowercase()
        }
    }

    override fun onStop() {
        super.onStop()
        job.cancel()

    }

    override fun onStart() {
        super.onStart()
        startTask()
    }

    private fun startTask() {
        job = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                if (!listsEqual(installedApps, getInstalledApps())) {
                    installedApps = getInstalledApps()
                    withContext(Dispatchers.Main) {
                        adapter.updateApps(installedApps)
                    }
                }
                delay(5000)
            }
        }
    }

    fun manualRefreshApps() {
        CoroutineScope(Dispatchers.IO).launch {
            installedApps = getInstalledApps()
            withContext(Dispatchers.Main) {
                adapter.updateApps(installedApps)
            }
        }
    }

    private fun listsEqual(
        list1: List<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>,
        list2: List<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>
    ): Boolean {
        if (list1.size != list2.size) return false

        for (i in list1.indices) {
            if (list1[i].first.componentName != list2[i].first.componentName || list1[i].second.first != list2[i].second.first) {
                return false
            }
        }

        return true
    }

}



/*
    private lateinit var binding: ActivityAppMenuBinding
    private lateinit var searchView: SearchView
    private lateinit var container: LinearLayout
    private lateinit var shownApps: List<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>
    private var checkApps: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAppMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(null)
        shownApps = listOf()
        searchView = findViewById(R.id.searchView)
        container = findViewById(R.id.container)

        // Set a listener on the search view
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Filter items based on the search query
                filterItems(newText)
                return true
            }

        })

        binding.root.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top > oldBottom - oldTop) {
                searchView.clearFocus()
            }
        }

    }

    private fun filterItems(query: String?) {
        val cleanQuery = query?.replace("[^a-zA-Z0-9]".toRegex(), "")

        for (i in 0 until container.childCount) {
            val view = container.getChildAt(i)

            if (view is TextView) {
                val itemText = view.text.toString()
                val cleanItemText = itemText.replace("[^a-zA-Z0-9]".toRegex(), "")

                if (cleanItemText.contains(cleanQuery ?: "", ignoreCase = true)) {
                    view.visibility = View.VISIBLE
                } else {
                    view.visibility = View.GONE
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        checkApps?.cancel()
    }

    override fun onStart() {
        super.onStart()
        startTask()
    }

    private fun startTask() {
        checkApps = lifecycleScope.launch {
            while (true) {
                if (!listsEqual(shownApps, getInstalledApps())) {
                    shownApps = getInstalledApps()
                    runOnUiThread {
                        refreshAppMenu()
                    }

                }
                delay(1000)
            }
        }
    }

    private fun listsEqual(
        list1: List<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>,
        list2: List<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>
    ): Boolean {
        if (list1.size != list2.size) return false

        for (i in list1.indices) {
            if (list1[i].first.componentName != list2[i].first.componentName || list1[i].second.first != list2[i].second.first) {
                return false
            }
        }

        return true
    }

    private fun refreshAppMenu() {
        deleteAppMenuContents()
        createAppMenu()
    }

    private fun deleteAppMenuContents(): Boolean {
        binding.container.removeAllViewsInLayout()
        return true
    }

    private fun createAppMenu(): Boolean {
        val apps = getInstalledApps()
        apps.forEach { appInfo ->
            createAppText(appInfo.first, appInfo.second.first, appInfo.second.second)
        }
        return true
    }

    private fun getInstalledApps(): List<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>> {
        val allApps = mutableListOf<Pair<LauncherActivityInfo, Pair<UserHandle, Int>>>()
        val launcherApps = this.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        for (i in launcherApps.profiles.indices) {
            launcherApps.getActivityList(null, launcherApps.profiles[i]).forEach { app ->
                if (!isAppHidden(app.activityInfo.applicationInfo.packageName, i)) {
                    allApps.add(Pair(app, Pair(launcherApps.profiles[i], i)))
                }
            }
        }
        return allApps.sortedBy {
            it.first.applicationInfo.loadLabel(packageManager).toString().lowercase()
        }
    }

    private fun createAppText(
        appInfo: LauncherActivityInfo,
        userHandle: UserHandle,
        workProfile: Int
    ): Boolean {
        val appInfo = appInfo.activityInfo.applicationInfo

        val textView = TextView(this)
        val editLayout = LinearLayout(this)

        val editText = EditText(this)
        editText.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val imageView = ImageView(this)
        imageView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val mainActivity = launcherApps.getActivityList(appInfo.packageName, userHandle).firstOrNull()

        editLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        editLayout.orientation = LinearLayout.HORIZONTAL;

        setupTextView(textView, editText, appInfo, workProfile)

        setupTextListeners(textView, editLayout, appInfo, userHandle, workProfile, launcherApps, mainActivity)

        editLayout.addView(editText);
        editLayout.addView(imageView);

        editLayout.visibility = View.GONE

        binding.container.addView(textView)
        binding.container.addView(editLayout)

        return true
    }

    private fun setupTextView(textView: TextView, editText: EditText, appInfo: ApplicationInfo, workProfile: Int) {
        val states = arrayOf(
            intArrayOf(-android.R.attr.state_pressed),
            intArrayOf(android.R.attr.state_pressed)
        )

        val colors = intArrayOf(
            Color.parseColor("#f3f3f3"),   // Default text color
            Color.parseColor("#c3c3c3") // Text color when pressed
        )

        with(textView) {
            textSize = 28f
            setPadding(0, 10, 0, 80)
            isClickable = true
            focusable = View.FOCUSABLE
            gravity = Gravity.START

            text = getAppName(appInfo.packageName, workProfile, appInfo.loadLabel(packageManager))
            if (workProfile != 0) {text = "*" + text}
            setTextColor(ColorStateList(states, colors))
        }
        with(editText) {
            id = R.id.app_name
            textSize = 28f
            setPadding(0, 10, 0, 80)
            isClickable = true
            focusable = View.FOCUSABLE
            gravity = Gravity.CENTER_VERTICAL
            isElegantTextHeight = false
            isFocusable = true
            isClickable = true
            includeFontPadding = true
            isSingleLine = true
            setTextColor(ColorStateList(states, colors))
            background = null
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        editText.setText(textView.text)
    }

    private fun setupTextListeners(
        textView: TextView,
        editLayout: LinearLayout,
        appInfo: ApplicationInfo,
        userHandle: UserHandle,
        workProfile: Int,
        launcherApps: LauncherApps,
        mainActivity: LauncherActivityInfo?
    ) {
        textView.setOnLongClickListener {
            appActionMenu(textView, editLayout, appInfo, userHandle, workProfile, launcherApps, mainActivity)
        }

        textView.setOnClickListener {
            if (mainActivity != null) {
                launcherApps.startMainActivity(mainActivity.componentName, userHandle, null, null)
            } else {
                Toast.makeText(
                    this,
                    "Unable to launch ${appInfo.loadLabel(packageManager)}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun appActionMenu(
        textView: TextView,
        editLayout: LinearLayout,
        appInfo: ApplicationInfo,
        userHandle: UserHandle,
        workProfile: Int,
        launcherApps: LauncherApps,
        mainActivity: LauncherActivityInfo?
    ): Boolean {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.app_action_menu, null)

        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = true

        popupWindow.animationStyle = android.R.style.Animation_Translucent

        if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            popupView.findViewById<TextView>(R.id.uninstall).visibility = View.GONE
        }

        textView.visibility = View.INVISIBLE

        popupWindow.showAsDropDown(textView, 0, -textView.height)
        var editing = false
        popupWindow.setOnDismissListener {
            if (!editing) {textView.visibility = View.VISIBLE}
        }

        popupView.findViewById<TextView>(R.id.info).setOnClickListener {
            if (mainActivity != null) {
                launcherApps.startAppDetailsActivity(
                    mainActivity.componentName,
                    userHandle,
                    null,
                    null
                )
            }

            popupWindow.dismiss()
        }

        popupView.findViewById<TextView>(R.id.uninstall).setOnClickListener {
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = Uri.parse("package:${appInfo.packageName}")
            intent.putExtra(Intent.EXTRA_USER, userHandle)
            startActivity(intent)

            popupWindow.dismiss()
        }

        popupView.findViewById<TextView>(R.id.rename).setOnClickListener {
            textView.visibility = View.GONE
            editLayout.visibility = View.VISIBLE
            editing = true
            popupWindow.dismiss()
            val editText = editLayout.findViewById<EditText>(R.id.app_name)

            editText.requestFocus()

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                binding.appList.scrollToDescendant(textView)
            }, 100)

            binding.root.addOnLayoutChangeListener {
                    _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if (bottom - top > oldBottom - oldTop) {
                    editing = false
                    editLayout.clearFocus()

                    editLayout.visibility = View.GONE
                    textView.visibility = View.VISIBLE
            }
            }
            editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(editText.windowToken, 0)
                    setAppName(appInfo.packageName, workProfile, editText.text.toString())
                    refreshAppMenu()

                    return@setOnEditorActionListener true
                }
                false
            }
        }

        popupView.findViewById<TextView>(R.id.hide).setOnClickListener {
            setAppHidden(appInfo.packageName, workProfile, true)
            refreshAppMenu()

            popupWindow.dismiss()
        }

        return true // Indicate that the long click event is consumed}
    }

    private fun setAppHidden(packageName: String, profile: Int, hidden: Boolean) {
        // Get the shared preferences editor
        val editor = getSharedPreferences("hidden_apps", MODE_PRIVATE).edit()
        val key = "$packageName-$profile"
        editor.putBoolean(key, hidden)
        editor.apply()
    }

    private fun isAppHidden(packageName: String, profile: Int): Boolean {
        // Get the shared preferences object
        val sharedPref = getSharedPreferences("hidden_apps", MODE_PRIVATE)
        val key = "$packageName-$profile"
        return sharedPref.getBoolean(key, false) // Default to false (visible)
    }

    private fun setAppVisible(packageName: String, profile: Int) {
        // Get the shared preferences editor
        val editor = getSharedPreferences("hidden_apps", MODE_PRIVATE).edit()
        val key = "$packageName-$profile"
        editor.remove(key)
        editor.apply()
    }

    private fun setAppName(packageName: String, profile: Int, newName: String) {
        val editor = getSharedPreferences("renamed_apps", MODE_PRIVATE).edit()
        val key = "$packageName-$profile"
        editor.putString(key, newName)
        editor.apply()
    }

    private fun getAppName(packageName: String, profile: Int, appName: CharSequence): CharSequence? {
        val sharedPreferences = getSharedPreferences("renamed_apps", MODE_PRIVATE)
        val key = "$packageName-$profile"
        return sharedPreferences.getString(key, appName.toString())
    }
}*/