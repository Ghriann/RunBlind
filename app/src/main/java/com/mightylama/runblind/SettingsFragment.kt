package com.mightylama.runblind

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.mapbox.mapboxsdk.maps.Style
import com.mightylama.runblind.databinding.FragmentSettingsBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.min

class SettingsFragment(val callback : SettingsFragmentCallback, private val circuitList : ArrayList<String>) : Fragment() {

    var settingsEnabled = false
        set(value) {
            field = value
            enableSettings(value)
        }

    var dotColor : ColorStateList? = null
        set(value) {
            field = value
            value?.let { binding?.gpsFixDot?.imageTintList = it }
        }

    private lateinit var settingsViewList : List<View>

    private lateinit var adapter: ArrayAdapter<String>


    interface SettingsFragmentCallback {
        fun setSetting(key : String, value : Int)
        suspend fun getCircuitPath(index: Int)
        fun setMapStyle(styleKey: String)
        fun calibrate()
    }

    private fun Boolean.toInt() = if (this) 1 else 0
    private fun Int.toBoolean() = this == 1


    private var binding : FragmentSettingsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        binding = FragmentSettingsBinding.inflate(inflater)

        binding?.apply {
            alarmChipGroup.setOnCheckedChangeListener { _, checkedId ->
                val i = when(checkedId) {
                    alarmChip2m.id -> 0
                    alarmChip3m.id -> 1
                    alarmChip5m.id -> 2
                    alarmChipOff.id -> 3
                    else -> 0
                }
                setSetting("alarm", i)
            }

            sourceChipGroup.setOnCheckedChangeListener { _, checkedId ->
                val i = when(checkedId) {
                    sourceChip2m.id -> 0
                    sourceChip3m.id -> 1
                    sourceChip5m.id -> 2
                    else -> 0
                }
                setSetting("source", i)
            }

            mapChipGroup.setOnCheckedChangeListener { _, checkedId ->
                val key = when(checkedId) {
                    mapChipVector.id -> Style.MAPBOX_STREETS
                    mapChipSatellite.id -> Style.SATELLITE
                    else -> Style.MAPBOX_STREETS
                }
                if (settingsEnabled) callback.setMapStyle(key)
            }

            hrtfChipGroup.setOnCheckedChangeListener { _, checkedId ->
                val i = when(checkedId) {
                    hrtfChip1.id -> 0
                    hrtfChip2.id -> 1
                    hrtfChip3.id -> 2
                    else -> 0
                }
                setSetting("hrtf", i)
            }

            calibrateChip.setOnClickListener {
                callback.calibrate()
            }


            headDiameterInput.setOnEditorActionListener { view : TextView, actionId: Int, event: KeyEvent? ->
                if ((actionId == EditorInfo.IME_ACTION_DONE) || ((event?.keyCode == KeyEvent.KEYCODE_ENTER) && (event.action == KeyEvent.ACTION_DOWN))) {
                    val text = headDiameterInput.text.toString()
                    setSetting("head_diameter", if (text.isEmpty()) 0 else text.toInt())
                    val manager = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    manager.hideSoftInputFromWindow(view.windowToken, 0)
                    binding?.headDiameterInputText?.requestFocus()
                    true
                }
                else {
                    false
                }
            }

            bellSwitch.setOnCheckedChangeListener { _, b ->
                setSetting("bell", b.toInt())
            }

            loopSwitch.setOnCheckedChangeListener { _, b ->
                setSetting("loop", b.toInt())
            }

            mainServiceSwitch.setOnCheckedChangeListener { _, b ->
                setSetting("main_service", b.toInt())
            }

            audioServiceSwitch.setOnCheckedChangeListener { _, b ->
                setSetting("audio_service", b.toInt())
            }

            tasServiceSwitch.setOnCheckedChangeListener { _, b ->
                setSetting("tas_service", b.toInt())
            }

            gnssServiceSwitch.setOnCheckedChangeListener { _, b ->
                setSetting("gnss_service", b.toInt())
            }

            imuServiceSwitch.setOnCheckedChangeListener { _, b ->
                setSetting("imu_service", b.toInt())
            }


            context?.let {
                adapter = ArrayAdapter<String>(it, android.R.layout.simple_spinner_item, circuitList)
                    .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                binding?.circuitSpinner?.adapter = adapter
            }
            binding?.circuitSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(p0: AdapterView<*>?) {}

                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    GlobalScope.launch {
                        callback.getCircuitPath(p2)
                    }
                }
            }
        }

        initializeViewList()
        settingsEnabled = false

        return binding?.root
    }


    private fun setSetting(key : String, value : Int) {
        if (settingsEnabled) callback.setSetting(key, value)
    }


    private fun enableSettings(b : Boolean) {
        binding?.apply {
            settingsViewList.forEach {
                it.isEnabled = b
            }
        }
    }

    private fun initializeViewList() {
        binding?.apply {
            settingsViewList = listOf(bellSwitch, loopSwitch, headDiameterInput, mainServiceSwitch, audioServiceSwitch, tasServiceSwitch, gnssServiceSwitch, imuServiceSwitch, calibrateChip) +
                    chipList(alarmChipGroup) +
                    chipList(sourceChipGroup) +
                    chipList(mapChipGroup) +
                    chipList(hrtfChipGroup)
        }
    }

    private fun chipList(group : ChipGroup) : List<View>{
        val list = ArrayList<View>()
        for (i in 0 until group.childCount) {
            list.add(group.getChildAt(i))
        }

        return list
    }

    fun updateSettings(serializedSettings : String) {
        val iter = serializedSettings.split(",").iterator()
        binding?.apply {
            bellSwitch.setWithNextBool(iter)
            loopSwitch.setWithNextBool(iter)
            alarmChipGroup.setWithNextInt(iter)
            sourceChipGroup.setWithNextInt(iter)
            mapChipGroup.setWithNextInt(iter)
            hrtfChipGroup.setWithNextInt(iter)
            headDiameterInput.setWithNextInt(iter)
            mainServiceSwitch.setWithNextBool(iter)
            audioServiceSwitch.setWithNextBool(iter)
            tasServiceSwitch.setWithNextBool(iter)
            gnssServiceSwitch.setWithNextBool(iter)
            imuServiceSwitch.setWithNextBool(iter)
        }
        settingsEnabled = true
    }

    private fun SwitchMaterial.setWithNextBool(iter : Iterator<String>) {
        isChecked = iter.next().toInt().toBoolean()
    }

    private fun ChipGroup.setWithNextInt(iter : Iterator<String>) {
        check(getChildAt(iter.next().toInt()).id)
    }

    private fun EditText.setWithNextInt(iter : Iterator<String>) {
        setText(iter.next())
    }


    fun notifyDataChanged() {
        adapter.notifyDataSetChanged()
    }

    fun selectSpinnerItem(lastSelectedCircuit : Int) : Int {
        var i = 0
        binding?.circuitSpinner?.apply {
            min(lastSelectedCircuit, adapter.count - 1)
                .also { setSelection(it) }
                .also { GlobalScope.launch { callback.getCircuitPath(it) } }
                .also { i = it }
        }
        return i
    }

}