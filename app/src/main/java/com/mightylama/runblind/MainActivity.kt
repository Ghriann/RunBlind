package com.mightylama.runblind

import android.Manifest
import android.animation.Animator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.mapbox.android.core.location.*
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.*
import com.mapbox.mapboxsdk.utils.BitmapUtils
import com.mapbox.mapboxsdk.utils.ColorUtils
import com.mightylama.runblind.databinding.ActivityMainBinding
import com.mightylama.runblind.databinding.DialogScannerBinding
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.network.sockets.*
import io.ktor.util.network.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ref.WeakReference


class MainActivity
    : FragmentActivity(),
    CircuitListFragment.CircuitListCallback, RecordFragment.RecordFragmentCallback, CompassFragment.CompassFragmentCallback, SettingsFragment.SettingsFragmentCallback {

    private val KEY_IP = "key_ip"
    private val KEY_MARKER = "key_marker"


    private val KEY_API_MAPBOX_DOWNLOAD = BuildConfig.MAPBOX_KEY_DOWNLOAD

    private lateinit var binding: ActivityMainBinding
    private val circuitList = ArrayList<String>()
    private var circuitListFragment: CircuitListFragment? = null
    private var recordFragment: RecordFragment? = null
    private var compassFragment: CompassFragment? = null
    private var settingsFragment: SettingsFragment? = null

    private var targetZoomForCenterCamera = -1
    private var lastPosition: LatLng? = null
    private var circuitPointList: List<LatLng>? = null
    override var circuitName: String? = null
    override var circuitIndex: Int? = null

    private lateinit var inputManager: InputMethodManager

    private var lineManager : LineManager? = null
    private var pathLine : Line? = null
    private var symbolManager : SymbolManager? = null
    private var symbol : Symbol? = null

    private var lastSelectedCircuit = 0
    private var isRecording = false
    private var mapbox : MapboxMap? = null
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            enableLocationComponent(mapbox?.style!!)
        } else {
            Toast.makeText(this, "Permission not granted, please try again", Toast.LENGTH_SHORT).show()
        }
    }
    private lateinit var locationEngine: LocationEngine
    var lastLocation: Location? = null
    private val callback = MainActivityLocationCallback(this)
    val DEFAULT_INTERVAL_IN_MILLISECONDS = 1000L
    val DEFAULT_MAX_WAIT_TIME = DEFAULT_INTERVAL_IN_MILLISECONDS * 5


    private var isPagerEnabled: Boolean = false
        set(value){
            field = value
            binding.apply {
                pager.isUserInputEnabled = value
                val tabStrip = tab.getChildAt(0).also { it.isEnabled = value } as ViewGroup
                tabStrip.forEach {
                    it.isEnabled = value
                }
            }
        }

    private var isSliderVisible: Boolean = false
        set(value) {
            if (field && !value ) {
                binding.slider.let {
                    it.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .setListener(object : Animator.AnimatorListener {
                            override fun onAnimationRepeat(p0: Animator?) {}

                            override fun onAnimationEnd(p0: Animator?) {
                                it.visibility = View.GONE
                            }

                            override fun onAnimationCancel(p0: Animator?) {}

                            override fun onAnimationStart(p0: Animator?) {}
                        })
                }
            }
            else if (!field && value) {
                binding.slider.let {
                    it.alpha = 0f
                    it.visibility = View.VISIBLE
                    it.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .setListener(null)
                }
            }
            field = value
        }


    private var baseUrl: String? = null
    private val httpClient = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 3000
        }
    }

    override var serverState = ServerState.Undefined
    private var scanningDialogBinding : DialogScannerBinding? = null
    private var scanningDialog : Dialog? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pingRunnable = object : Runnable {
        override fun run() {
            baseUrl?.let {
                GlobalScope.launch { getSpatialData() }
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.map.apply {
            x = width.toFloat()
        }

        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        binding.pager.let {
            it.adapter = MainFragmentStateAdapter(this)
            it.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {

                override fun onPageScrolled(
                    position: Int,
                    positionOffset: Float,
                    positionOffsetPixels: Int
                ) {
                    super.onPageScrolled(position, positionOffset, positionOffsetPixels)
                    binding.map.apply {
                        if (position == 0) {
                            x = width - positionOffsetPixels.toFloat()
                        } else if (position > 0)
                            x = 0F
                    }
                }

                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    inputManager.hideSoftInputFromWindow(binding.root.windowToken, 0)

                    when (position) {
                        0 -> {
                            if (serverState == ServerState.Connected) GlobalScope.launch {
                                getCircuitList()
                            }
                            isSliderVisible = false
                        }
                        1 -> {
                            targetZoomForCenterCamera = -1
                            updatePath(circuitPointList)
                            fitBounds()
                            isSliderVisible = false
                        }
                        2 -> {
                            removePath()
                            targetZoomForCenterCamera = 17
                            lastPosition?.let { setView(it) }
                            isSliderVisible = false

                        }
                        3 -> {
                            removePath()
                            targetZoomForCenterCamera = 17
                            lastPosition?.let { setView(it) }
                            isSliderVisible = true
                        }
                    }
                }
            })
        }

        TabLayoutMediator(binding.tab, binding.pager) { tab, position ->
            when(position) {
                0 -> tab.setIcon(R.drawable.ic_baseline_settings)
                1 -> tab.text = "Guide"
                2 -> tab.text = "Record"
                3 -> tab.text = "Compass"
            }
        }.attach()

        binding.volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {}

            override fun onStartTrackingTouch(p0: SeekBar?) {}

            override fun onStopTrackingTouch(p0: SeekBar?) {
                p0?.let {
                    GlobalScope.launch { setVolume(it.progress) }
                }
            }
        })

        binding.slider.apply {
            setOnStartTrackingListener { pos ->
                var degree = (pos * 360).toInt()
                if (degree == 360) degree = 0
                compassFragment?.displayOrientationHint(degree)
            }

            setOnStopTrackingListener { pos ->
                var degree = (pos * 360).toInt()
                if (degree == 360) degree = 0
                setSetting("compass", degree)
                compassFragment?.hideOrientationHint()
            }

            setOnSliderMovedListener {
                var degree = (it * 360).toInt()
                if (degree == 360) degree = 0
                else if (degree < 0) degree += 360
                compassFragment?.displayOrientationHint(degree)
            }
        }

        createScanningDialog()

        configureMap(savedInstanceState)
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(pingRunnable)
        binding.map.onPause()
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(pingRunnable)
        binding.map.onResume()
    }

    override fun onStart() {
        super.onStart()
        binding.map.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.map.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.map.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.map.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.map.onDestroy()
    }


    class MainFragmentStateAdapter(private var mainActivity: MainActivity): FragmentStateAdapter(
        mainActivity
    ) {
        override fun getItemCount(): Int {
            return 4
        }

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> SettingsFragment(
                    mainActivity,
                    mainActivity.circuitList
                ).also { mainActivity.settingsFragment = it }
                1 -> CircuitListFragment(mainActivity).also {
                    mainActivity.circuitListFragment = it
                }
                2 -> RecordFragment(mainActivity).also { mainActivity.recordFragment = it }
                3 -> CompassFragment(mainActivity).also { mainActivity.compassFragment = it }
                else -> Fragment()
            }
        }
    }

    private fun populateDummyCircuits() {
        updateCircuitList(listOf("Circuit 1", "Circuit 2", "Circuit 3"))
    }


    private suspend fun getSpatialData()
    {
        try {
            val response = httpClient.get<String>(baseUrl + "get_spatial_data")

            runOnUiThread {
                if (serverState != ServerState.Connected)
                    onServerConnected()

                val spatialData = SpatialData()
                response.split(",")
                    .also {
                        it.subList(0, 3).map { it.toInt() }
                            .let { spatialData.apply {
                                yaw = it[0]
                                pitch = it[1]
                                roll = it[2]
                            }}
                    }
                    .also {
                        it.subList(3, 5).map { it.toDouble() }
                            .let { spatialData.position.apply {
                                latitude = it[0]
                                longitude = it[1]
                            }}
                    }
                    .also{
                        settingsFragment?.dotColor = getColorStateList(if (it[5] == "0") R.color.dotColorDisconnected else R.color.dotColorConnected)
                    }

                    lastPosition = spatialData.position
                        .also {
                            if (isRecording) { addPointToPath(it) }
                        }

                    updatePosition(spatialData.position, spatialData.yaw)
                    updateDebugData(spatialData)
            }
        }

        catch (exception: Exception) {
            runOnUiThread {
                if (serverState != ServerState.Disconnected)
                    onServerDisconnected()

                when (exception) {
                    is UnresolvedAddressException -> Toast.makeText(
                        this,
                        "Please enter a valid IP",
                        Toast.LENGTH_SHORT
                    ).show()
                    is ConnectTimeoutException -> "" //TODO eventually
                }
            }
        }
    }

    private fun onServerDisconnected()
    {
        serverState = ServerState.Disconnected
        binding.dot.imageTintList = getColorStateList(R.color.dotColorDisconnected)
        onWaiting()
    }

    private fun onWaiting()
    {
        circuitListFragment?.onCircuitWaiting()
        recordFragment?.onRecordWaiting()
        compassFragment?.onCompassWaiting()
        settingsFragment?.settingsEnabled = false
        binding.volumeSlider.isEnabled = false
    }

    private fun onServerConnected()
    {
        serverState = ServerState.Connected
        binding.dot.imageTintList = getColorStateList(R.color.dotColorConnected)
        circuitListFragment?.onCircuitStopped()
        recordFragment?.onRecordStopped()
        compassFragment?.onCompassStopped()

        GlobalScope.launch {
            getVolume()
            getSettings()
            getCircuitList()
            getCircuitPath(0)
        }
    }



    private suspend fun getCircuitList() {
        getFromServer("get_circuit_list")?.let {
            runOnUiThread {
                updateCircuitList(it)
                lastSelectedCircuit = settingsFragment?.selectSpinnerItem(lastSelectedCircuit) ?: 0
            }
        }
    }

    private suspend fun getVolume(){
        val volume = getFromServer("get_volume")
        volume?.let {
            runOnUiThread { updateVolume(volume.toInt()) }
        }
    }

    private suspend fun setVolume(volume: Int){
        getFromServer("set_volume/$volume")
    }

    override suspend fun startCircuit(circuitIndex: Int) {
        getFromServer("start_circuit/$circuitIndex")
        runOnUiThread {
            targetZoomForCenterCamera = 20
            lastPosition?.let { setView(it) }
            isPagerEnabled = false
            circuitListFragment?.onCircuitStarted()
        }
    }

    override suspend fun stopCircuit() {
        getFromServer("stop_circuit")
        runOnUiThread {
            targetZoomForCenterCamera = -1
            fitBounds()
            isPagerEnabled = true
            circuitListFragment?.onCircuitStopped()
        }
    }

    override suspend fun startRecording(namePath: String) {
        getFromServer("start_recording/$namePath")
        runOnUiThread {
            removePath()
            isRecording = true
            isPagerEnabled = false
            recordFragment?.onRecordStarted()
        }
    }

    override suspend fun stopRecording() {
        isRecording = false
        getFromServer("stop_recording")
        runOnUiThread {
            removePath()
            isPagerEnabled = true
            recordFragment?.onRecordStopped()
        }
    }


    override suspend fun getCircuitPath(index: Int) {
        lastSelectedCircuit = index
        val response = getFromServer("get_circuit_path/$index")
        response?.let {
            if (binding.pager.currentItem == 0) runOnUiThread {
                circuitPointList = it.removePrefix("[").removeSuffix("]").split("],[").map { deserializePoint(
                    it
                ) }
                    .also { updatePath(it) }
                fitBounds()
                circuitName = circuitList[index]
                    .also { circuitListFragment?.updateCircuitName(it) }
                circuitIndex = index
            }
        }
    }

    override suspend fun startCompass() {
        getFromServer("start_compass")
        runOnUiThread {
            isPagerEnabled = false
            compassFragment?.onCompassStarted()
        }
    }

    override suspend fun stopCompass() {
        getFromServer("stop_compass")
        runOnUiThread {
            isPagerEnabled = true
            compassFragment?.onCompassStopped()
        }
    }

    override fun calibrate() {

        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PermissionChecker.PERMISSION_GRANTED) {
            GlobalScope.launch {
                val response = getFromServer("calibrate/${lastLocation?.latitude ?: "null"}/${lastLocation?.longitude ?: "null"}")
                runOnUiThread {
                    Toast.makeText(baseContext, response, Toast.LENGTH_SHORT).show()
                }
            }
        }
        else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private class MainActivityLocationCallback(activity: MainActivity?) : LocationEngineCallback<LocationEngineResult> {

        val activityWeakReference: WeakReference<MainActivity?> = WeakReference(activity)

        override fun onSuccess(result: LocationEngineResult) {
            activityWeakReference.get()?.let { activity ->
                result.lastLocation?.let { activity.lastLocation = it }
            }
        }

        override fun onFailure(exception: java.lang.Exception) {
            activityWeakReference.get()?.let {
                //TODO ?
            }
        }
    }


    override fun updateCompassOrientation(degree: Int) {
        binding.slider.setPosition(degree / 360.0)
    }

    private suspend fun getFromServer(path: String) : String?{
        return try {
            httpClient.get(baseUrl + path)
        } catch (exception: Exception) {
            runOnUiThread { Toast.makeText(this, exception.toString(), Toast.LENGTH_SHORT).show() }
            null
        }
    }


    private fun updateDebugData(spatialData: SpatialData) {

        binding.apply {

            { textView: TextView, data: Any ->
                textView.text = data.toString()
            }
                .also { it(yawCount, spatialData.yaw) }
                .also { it(pitchCount, spatialData.pitch) }
                .also { it(rollCount, spatialData.roll) }
                .also { it(latCount, spatialData.position.latitude) }
                .also { it(lonCount, spatialData.position.longitude) }
        }
    }

    private fun updateVolume(volume: Int) {
        binding.volumeSlider.apply {
            progress = volume
            isEnabled = true
        }
    }

    private fun updateCircuitList(serializedNameList: String) {
        updateCircuitList(serializedNameList.split(","))
    }

    private fun updateCircuitList(nameList: List<String>) {
        circuitList.apply {
            clear()
            addAll(nameList)
        }
        runOnUiThread { settingsFragment?.notifyDataChanged() }
    }


    enum class ServerState{
        Connected, Disconnected, Undefined
    }






    // ##  FOR MAP  ##

    private fun configureMap(savedInstanceState: Bundle?) {

        binding.map.apply {
            onCreate(savedInstanceState)
            getMapAsync {
                mapbox = it.also {
                    it.setStyle(Style.MAPBOX_STREETS) { style: Style ->

                        it.uiSettings.isRotateGesturesEnabled = false

                        BitmapUtils.getBitmapFromDrawable(
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.marker
                            )
                        )
                            ?.let { style.addImage(KEY_MARKER, it) }

                        lineManager = LineManager(this, it, style)
                        pathLine = lineManager?.create(
                            LineOptions()
                                .withLatLngs(MutableList(0) { LatLng() })
                                .withLineWidth(5F)
                                .withLineColor(ColorUtils.colorToRgbaString(getColor(R.color.circuitPathColor)))
                        )
                        symbolManager = SymbolManager(this, it, style)
                        symbolManager?.apply {
                            iconIgnorePlacement = true
                            iconAllowOverlap = true
                        }
                        symbol = symbolManager?.create(
                            SymbolOptions()
                                .withLatLng(LatLng())
                                .withIconOpacity(0F)
                                .withIconImage(KEY_MARKER)
                                .withIconSize(0.8F)
                                .withIconOffset(arrayOf(0f, -9f))
                        )

                        enableLocationComponent(style)
                    }
                }
            }
        }
    }

    override fun setMapStyle(styleKey: String) {
        mapbox?.let {
            it.setStyle(styleKey) { style ->
                BitmapUtils.getBitmapFromDrawable(
                    ContextCompat.getDrawable(
                        baseContext,
                        R.drawable.marker
                    )
                )
                    ?.let { style.addImage(KEY_MARKER, it) }
            }
        }
    }

    private fun enableLocationComponent(loadedMapStyle: Style) {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {

            // Get an instance of the component
            mapbox?.locationComponent?.apply {

                // Set the LocationComponent activation options
                val locationComponentActivationOptions =
                    LocationComponentActivationOptions.builder(this@MainActivity, loadedMapStyle)
                        .useDefaultLocationEngine(false)
                        .build()

                // Activate with the LocationComponentActivationOptions object
                activateLocationComponent(locationComponentActivationOptions)
                initLocationEngine()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun initLocationEngine() {
        locationEngine = LocationEngineProvider.getBestLocationEngine(this)

        val request = LocationEngineRequest.Builder(DEFAULT_INTERVAL_IN_MILLISECONDS)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .setMaxWaitTime(DEFAULT_MAX_WAIT_TIME).build()

        locationEngine.requestLocationUpdates(request, callback, mainLooper);
        locationEngine.getLastLocation(callback);
    }



    private fun setView(point: LatLng, zoom: Int) {
        mapbox?.easeCamera(CameraUpdateFactory.newLatLngZoom(point, zoom.toDouble()))
    }

    private fun setView(point: LatLng) {
        mapbox?.easeCamera(
            CameraUpdateFactory.newLatLngZoom(
                point,
                targetZoomForCenterCamera.toDouble()
            )
        )
    }


        private fun updatePosition(point: LatLng, yaw: Int, immediatePan: Boolean = false) {
        symbol?. apply {
            iconOpacity = 1F
            latLng = point
            iconRotate = -yaw.toFloat()
        }
        symbolManager?.update(symbol)

        if (targetZoomForCenterCamera != -1) {
            mapbox?.easeCamera(
                CameraUpdateFactory.newLatLngZoom(
                    point,
                    targetZoomForCenterCamera.toDouble()
                )
            )
        }
    }


    private fun updatePath(pointList: List<LatLng>?) {
        pathLine?.apply {
            pointList?.let { latLngs = it }
            lineManager?.update(this)
        }
    }


    private fun removePath() {
        pathLine?.apply {
            latLngs = ArrayList()
            lineManager?.update(this) }
    }

    private fun addPointToPath(point: LatLng) {
        pathLine?.apply {
            latLngs = latLngs.toMutableList().also { it.add(point) }
            lineManager?.update(this)
        }
    }

    private fun fitBounds() {
        pathLine?.latLngs?.let {
            if (it.isNotEmpty()) {
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.Builder().includes(it).build(),
                    100
                ).let { cam ->
                    mapbox?.let {
                        if (it.cameraPosition.zoom > 5F)
                            it.easeCamera(cam, 500)
                        else
                            it.moveCamera(cam)
                    }
                }
            }
        }
    }


    private fun deserializePoint(serializedPoint: String) : LatLng {
        val point = LatLng()
        serializedPoint.removePrefix("[").removeSuffix("]").split(",").let {
            if (it.size == 2) {
                point.latitude = it[0].toDouble()
                point.longitude = it[1].toDouble()
            }
        }
        return point
    }


    class SpatialData(
        var yaw: Int = 0,
        var pitch: Int = 0,
        var roll: Int = 0,
        var position: LatLng = LatLng()
    )



    // ## FOR SETTINGS ##

    private fun getSettings() {
        GlobalScope.launch {
            val response = getFromServer("get_settings")
            response?.let { runOnUiThread { settingsFragment?.updateSettings(it) }}
        }
    }

    override fun setSetting(key: String, value: Int) {
         GlobalScope.launch {
             val response = getFromServer("set_setting/$key/$value")
             if (response?.isNotEmpty() == true) {
                 runOnUiThread {
                     Toast.makeText(baseContext, response, Toast.LENGTH_SHORT).show()
                 }
             }
         }
    }



    // ##FOR DEVICE DISCOVERY

    private fun createScanningDialog() {
        scanningDialogBinding = DialogScannerBinding.inflate(layoutInflater).also {
            scanningDialog = AlertDialog.Builder(this)
                .setView(it.root)
                .setCancelable(false)
                .create().also { it.show() }
            it.reload.setOnClickListener { scanForDevices() }
        }
        scanForDevices()
    }


    private fun scanForDevices() {
        scanningDialogBinding?.apply {
            text.text = "Scanning network ..."
            progress.visibility = View.VISIBLE
            reload.visibility = View.GONE
        }

        GlobalScope.launch {
            delay(5000)
            runOnUiThread { scanningDialogBinding?.let { onScanningFailed() } }
        }

        getConnectedDevices().forEach {
            GlobalScope.launch { ping(it) }
        }
    }


    private fun getConnectedDevices() : List<String> {
        val iter = File("/proc/net/arp").readLines().iterator()
        val ipList = ArrayList<String>()

        iter.next()
        while(iter.hasNext()) {
            ipList.add(iter.next().split(" ")[0])
        }
        return ipList
    }

    private fun onScanningFailed() {
        scanningDialogBinding?.apply {
            text.text = "No device found. Try again?"
            progress.visibility = View.GONE
            reload.visibility = View.VISIBLE
        }
    }

    private suspend fun ping(address: String) {
        try {
            httpClient.get<String>("http://$address:5000/get_spatial_data")
            runOnUiThread { onServerFound(address) }
        }
        catch (exception: Exception) {}
    }

    private fun onServerFound(address: String) {
        scanningDialog?.dismiss()
        scanningDialog = null
        scanningDialogBinding = null
        Toast.makeText(this, "Connected to $address", Toast.LENGTH_LONG).show()
        baseUrl = "http://$address:5000/"
    }
}