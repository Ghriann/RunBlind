package com.mightylama.runblind

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebViewClient
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import com.mightylama.runblind.databinding.ActivityMainBinding
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.network.sockets.ConnectTimeoutException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Exception
import java.lang.ref.WeakReference

class MainActivity
    : FragmentActivity(),
    CircuitListFragment.CircuitListCallback, RecordFragment.RecordFragmentCallback, CompassFragment.CompassFragmentCallback {

    private val KEY_IP = "key_ip"

    private lateinit var binding: ActivityMainBinding
    private val circuitList = ArrayList<String>()
    private var circuitListFragment: CircuitListFragment? = null
    private var recordFragment: RecordFragment? = null
    private var compassFragment: CompassFragment? = null

    private var serializedCircuitPath: String? = null

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


    private var baseUrl: String? = null
    private val httpClient = HttpClient()
    override var serverState = ServerState.Undefined

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pingRunnable = object : Runnable {
        override fun run() {
            baseUrl?.let {
                GlobalScope.launch { getSpatialData() }
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pager.let {
            it.adapter = MainFragmentStateAdapter(this)
            it.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    when (position) {
                        0 -> serializedCircuitPath?.let{ updatePath(it,true) }
                        else -> removePath()
                    }
                }
            })
        }

        TabLayoutMediator(binding.tab, binding.pager) { tab, position ->
            when(position) {
                0 -> tab.text = "Guiding"
                1 -> tab.text = "Recording"
                2 -> tab.text = "Compass"
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

        binding.dot.setOnClickListener {
        }

        showIpDialog()
        configureMap()
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(pingRunnable)
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(pingRunnable)
    }

    class MainFragmentStateAdapter(private var mainActivity: MainActivity): FragmentStateAdapter(mainActivity) {
        override fun getItemCount(): Int {
            return 3
        }

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> CircuitListFragment(mainActivity, mainActivity.circuitList).also { mainActivity.circuitListFragment = it }
                1 -> RecordFragment(mainActivity).also { mainActivity.recordFragment = it }
                2 -> CompassFragment(mainActivity).also { mainActivity.compassFragment = it }
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

                response.split(",")
                    .also { updateOrientation(it.subList(0, 6)) }
                    //TODO .also { it.last() ... }
            }
        }

        catch (exception: Exception) {
            runOnUiThread {
                if (serverState != ServerState.Disconnected)
                    onServerDisconnected()

                when (exception) {
                    is UnresolvedAddressException -> Toast.makeText(this, "Please enter a valid IP", Toast.LENGTH_SHORT).show()
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
            getCircuitList()
            getCircuitPath(0)
        }
    }



    private suspend fun getCircuitList() {
        val serializedList = getFromServer("get_circuit_list")
        serializedList?.let {
            runOnUiThread { updateCircuitList(it) }
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
            isPagerEnabled = false
            circuitListFragment?.onCircuitStarted()
        }
    }

    override suspend fun stopCircuit() {
        getFromServer("stop_circuit")
        runOnUiThread {
            isPagerEnabled = true
            circuitListFragment?.onCircuitStopped()
        }
    }

    override suspend fun startRecording(namePath: String) {
        getFromServer("start_recording/$namePath")
        runOnUiThread {
            isPagerEnabled = false
            recordFragment?.onRecordStarted()
        }
    }

    override suspend fun stopRecording() {
        getFromServer("stop_recording")
        runOnUiThread {
            isPagerEnabled = true
            recordFragment?.onRecordStopped()
        }
    }

    override suspend fun getCircuitPath(index: Int) {
        //STUB
        serializedCircuitPath = "[[48.71260000000001,2.2198499999999997],[48.71255529279227,2.219848419149478],[48.71251058952412,2.2198477703301425],[48.71246605900902,2.2198467827221315],[48.71242185604427,2.219844234333372],[48.71237783426477,2.219839952332632],[48.71233355747356,2.21983477357685],[48.712288577726014,2.219829575853843],[48.71224277812628,2.2198255798117277],[48.7121973050073,2.2198253143982947],[48.712153605963145,2.2198316205712096],[48.712113072152825,2.2198471696018336],[48.71207611565604,2.219871688914258],[48.712042319449885,2.2199024130273606],[48.71201124079934,2.219936499030135],[48.71198272524917,2.2199718967746493],[48.71195739701474,2.2200086974398188],[48.71193601047922,2.220047350163044],[48.71191918336163,2.220088219481427],[48.71190635788111,2.2201309422527413],[48.71189637687627,2.2201747842953017],[48.711888085299854,2.2202190124049186],[48.711880842572,2.220263187135737],[48.711874869606596,2.2203073609506108],[48.711870469454105,2.2203516332119766],[48.71186789848132,2.2203960849103357],[48.711867140072954,2.220440689606577],[48.7118680786813,2.220485381927644],[48.71187060458739,2.2205300946033377],[48.71187486782705,2.2205746801300634],[48.71188137289755,2.2206188815176566],[48.71189064977974,2.220662433904569],[48.711903103213295,2.2207051195023233],[48.711918528666914,2.220746949522726],[48.71193653660459,2.2207880047133286],[48.711956765740354,2.2208283361316536],[48.71197951589314,2.2208673006967476],[48.71200575380743,2.220903555077687],[48.71203647559513,2.2209357250627186],[48.71207220871631,2.2209626861203065],[48.71211191827616,2.220984146083262],[48.71215424420625,2.220999986024791],[48.71219787948815,2.2210101789270915],[48.7122421433185,2.2210157826881742],[48.71228676346504,2.2210185630543546],[48.71233147511922,2.221020295551196],[48.71237609709448,2.2210222662173407],[48.7124206335546,2.221024676130137],[48.712465113554295,2.221027580666123],[48.71250956386129,2.221031031944612],[48.7125539891594,2.221035050632007],[48.71259838176697,2.221039639783146],[48.712642733988595,2.2210448003432495],[48.712687050868716,2.2210503335705645],[48.71273136116149,2.221055669092769],[48.712775696206286,2.2210601960157077],[48.71282009091527,2.2210633042085193],[48.712864603961975,2.2210643886167736],[48.71290930371674,2.2210628462576896],[48.71295424770382,2.221058070128703],[48.71299885766952,2.221049217858215],[48.71304157939305,2.2210350857653713],[48.71308077579878,2.2210144394959888],[48.71311518323218,2.2209865827838198],[48.713145008888546,2.220952938810447],[48.71317082205394,2.220915452518802],[48.71319317909645,2.2208760125941778],[48.71321241071585,2.220835524943317],[48.71322865568458,2.2207940596351743],[48.71324204679545,2.2207516602068194],[48.71325278392567,2.2207084178959433],[48.7132612424418,2.220664548722499],[48.713267826052146,2.220620288858998],[48.71327291805932,2.2205758635585635],[48.7132766649905,2.2205313713726453],[48.713279060823965,2.2204868292216924],[48.713280096759924,2.2204422526888394],[48.71327970971264,2.220397661386278],[48.7132777142961,2.220353084003226],[48.71327390839162,2.2203085504707856],[48.713268070893456,2.220264115147955],[48.71325979606726,2.220220069928839],[48.71324857422732,2.2201768404467996],[48.713233897792755,2.220134851896944],[48.71321554222367,2.2200943619142524],[48.71319378072164,2.220055333471113],[48.71316893703375,2.2200176996169594],[48.71314124204832,2.219981620333983],[48.71311040468182,2.219948531222942],[48.71307595160693,2.21992031326044],[48.71303745049017,2.2198987743287324],[48.71299537522931,2.21988410369075],[48.71295108375017,2.2198749116472714],[48.71290597060066,2.2198697428816634],[48.71286111069601,2.2198671619517247],[48.71281656480135,2.2198657964740476],[48.71277219301014,2.219864286542781],[48.712727858996146,2.21986136593747],[48.71268347967523,2.2198571615914457],[48.71263901278046,2.2198528684799794],[48.71260000000001,2.2198499999999997]]"
        runOnUiThread { updatePath(serializedCircuitPath!!,true) }
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


    private suspend fun getFromServer(path: String) : String?{
        return try {
            httpClient.get(baseUrl + path)
        } catch (exception: Exception) {
            runOnUiThread { Toast.makeText(this, exception.toString(), Toast.LENGTH_SHORT).show() }
            null
        }
    }


    private fun updateOrientation(spatialDataList: List<String>) {
        binding.apply {
            yawCount.text = spatialDataList[0]
            pitchCount.text = spatialDataList[1]
            rollCount.text = spatialDataList[2]
            latCount.text = spatialDataList[3]
            lonCount.text = spatialDataList[4]
        }
    }

    private fun updateOrientation(serializedSpatialData: String) {
         updateOrientation(serializedSpatialData.split(","))
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
        runOnUiThread { circuitListFragment?.notifyDataChanged() }
    }

    private fun showIpDialog() {
        val editText = TextInputEditText(this)
        editText.setText(getPreferences(Context.MODE_PRIVATE).getString(KEY_IP, ""), TextView.BufferType.EDITABLE)
        AlertDialog.Builder(this)
            .setMessage("Please enter server IP")
            .setView(editText)
            .setCancelable(false)
            .setPositiveButton("OK") { _: DialogInterface, _: Int ->
                val address = editText.text.toString()
                baseUrl = "http://$address:5000/"
                getPreferences(Context.MODE_PRIVATE).edit().putString(KEY_IP, address).apply()
            }
            .create()
            .show()
    }

    enum class ServerState{
        Connected, Disconnected, Undefined
    }






    // FOR MAP

    private fun configureMap() {
        binding.webView.apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            loadUrl("file:///android_asset/leaflet.html")
        }
    }


    private fun updatePosition(point: Array<Double>) {
        updatePosition(serializePoint(point))
    }

    private fun updatePosition(serializedPoint: String) {
        runJavascript("updatePosition($serializedPoint)")
    }

    private fun updatePath(pointList: List<Array<Double>>, instantPan: Boolean) {
        updatePath(serializePointList(pointList), instantPan)
    }

    private fun updatePath(serializedPointList: String, instantPan: Boolean) {
        runJavascript("updatePath($serializedPointList,$instantPan)")
    }

    private fun removePath() {
        updatePath("null",true)
    }

    private fun tryFitBounds() {
        runJavascript("tryFitBounds()")
    }


    private fun runJavascript(command: String) {
        runOnUiThread {
            binding.webView.evaluateJavascript(command) {}
        }
    }

    private fun serializePoint(point: Array<Double>): String {
        return "[" + point[0] + "," + point[1] + "]"
    }

    private fun serializePointList(pointList: List<Array<Double>>): String {
        var serialized = "["
        pointList.forEach {
            serialized += "[" + it[0] + "," + it[1] + "],"
        }
        return serialized.dropLast(1) + "]"
    }
}