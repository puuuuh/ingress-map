package com.puuuuh.ingressmap.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.puuuuh.ingressmap.model.Mod
import com.puuuuh.ingressmap.model.PortalData
import com.puuuuh.ingressmap.model.Resonator
import com.puuuuh.ingressmap.repository.IngressApiRepo
import com.puuuuh.ingressmap.repository.OnPortalExReadyCallback


class PortalInfo(context: Context) : ViewModel(), OnPortalExReadyCallback {
    private val ingressRepo = IngressApiRepo()

    private val _lvl = MutableLiveData<Int>()
    val lvl: LiveData<Int> = _lvl

    private val _energy = MutableLiveData<Int>()
    val energy: LiveData<Int> = _energy

    private val _pic = MutableLiveData<String>()
    val pic: LiveData<String> = _pic

    private val _name = MutableLiveData<String>()
    val name: LiveData<String> = _name

    private val _team = MutableLiveData<String>()
    val team: LiveData<String> = _team

    private val _owner = MutableLiveData<String>()
    val owner: LiveData<String> = _owner

    private val _mods = MutableLiveData<List<Mod>>()
    val mods: LiveData<List<Mod>> = _mods

    private val _resonators = MutableLiveData<List<Resonator>>()
    val resonators: LiveData<List<Resonator>> = _resonators


    private fun normalTeamName(alias: String): String {
        return when (alias) {
            "R" -> "Resistance"
            "E" -> "Enlightened"
            else -> "None"
        }
    }

    fun setEntity(guid: String, name: String, team: String, pic: String) {
        _pic.postValue(pic)
        _name.postValue(name)
        _team.postValue(normalTeamName(team))
        ingressRepo.getExtendedPortalData(guid, this)
    }

    override fun onPortalExReady(portal: PortalData) {
        _lvl.postValue(portal.lvl)
        _energy.postValue(portal.energy)
        _pic.postValue(portal.pic)
        _name.postValue(portal.name)
        _team.postValue(normalTeamName(portal.team))
        _owner.postValue(portal.owner)
        _mods.postValue(portal.mods)
        _resonators.postValue(portal.resonators)
    }


}
