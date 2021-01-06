package com.mightylama.runblind

import android.R
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.mightylama.runblind.databinding.FragmentSettingsBinding

class SettingsFragment(val callback : SettingsFragmentCallback) : Fragment() {

    private var settingsEnabled = false
        set(value) {
            field = value
            enableSettings(value)
        }

    private lateinit var settingsViewList : List<View>

    interface SettingsFragmentCallback {
        fun getSettings()
        fun setSetting(key : String, value : Int)
    }

    fun Boolean.toInt() = if (this) 1 else 0
    fun Int.toBoolean() = this == 1


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
                val i = when(checkedId) {
                    mapChipVector.id -> 0
                    mapChipSatellite.id -> 1
                    else -> 0
                }
                setSetting("map", i)
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

            gnssChipGroup.setOnCheckedChangeListener { _, checkedId ->
                val i = when(checkedId) {
                    gnssChipUbx.id -> 0
                    gnssChipNmea.id -> 1
                    else -> 0
                }
                setSetting("gnss", i)
            }


            headDiameterInput.setOnEditorActionListener { view : TextView, actionId: Int, event: KeyEvent? ->
                if ((actionId == EditorInfo.IME_ACTION_DONE) || ((event?.keyCode == KeyEvent.KEYCODE_ENTER) && (event.action == KeyEvent.ACTION_DOWN))) {
                    val text = headDiameterInput.text.toString()
                    setSetting("head_diameter", if (text.isEmpty()) 0 else text.toInt())
                    val manager = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    manager.hideSoftInputFromWindow(view.windowToken, 0)
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
                ArrayAdapter<String>(it, R.layout.simple_spinner_item, listOf("Circuit1", "Circuit2", "Circuit3"))
                    .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }.let {
                        binding?.circuitSpinner?.adapter = it
                    }
            }
        }

        initializeViewList()
        settingsEnabled = false

        callback.getSettings()

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
            settingsViewList = listOf(bellSwitch, loopSwitch, headDiameterInput, mainServiceSwitch, audioServiceSwitch, tasServiceSwitch, gnssServiceSwitch, imuServiceSwitch) +
                    chipList(alarmChipGroup) +
                    chipList(sourceChipGroup) +
                    chipList(mapChipGroup) +
                    chipList(hrtfChipGroup) +
                    chipList(gnssChipGroup)
        }
    }

    private fun chipList(group : ChipGroup) : List<View>{
        val list = ArrayList<View>()
        for (i in 0 until group.childCount) {
            list.add(group.getChildAt(i))
        }

        return list
    }

    public fun updateSettings(serializedSettings : String) {
        val iter = serializedSettings.split("/").iterator()
        binding?.apply {
            bellSwitch.setWithNextBool(iter)
            loopSwitch.setWithNextBool(iter)
            alarmChipGroup.setWithNextInt(iter)
            sourceChipGroup.setWithNextInt(iter)
            mapChipGroup.setWithNextInt(iter)
            hrtfChipGroup.setWithNextInt(iter)
            headDiameterInput.setWithNextInt(iter)
            gnssChipGroup.setWithNextInt(iter)
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

}