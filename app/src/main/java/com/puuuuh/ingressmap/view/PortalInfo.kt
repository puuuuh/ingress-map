package com.puuuuh.ingressmap.view

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.view.*
import android.widget.ProgressBar
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.puuuuh.ingressmap.R
import com.puuuuh.ingressmap.repository.Portal
import com.puuuuh.ingressmap.viewmodel.PortalInfo
import com.puuuuh.ingressmap.viewmodel.ViewmodelFactory
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.portal_info_fragment.*


val lvlToEnergy = intArrayOf(1000, 1500, 2000, 2500, 3000, 4000, 5000, 6000)
val lvlToColor = intArrayOf(
    Color.rgb(0xFE, 0xCE, 0x5A),
    Color.rgb(0xFF, 0xA6, 0x30),
    Color.rgb(0xFF, 0x73, 0x15),
    Color.rgb(0xE4, 0x00, 0x00),
    Color.rgb(0xFD, 0x29, 0x92),
    Color.rgb(0xEB, 0x26, 0xCD),
    Color.rgb(0xC1, 0x24, 0xE0),
    Color.rgb(0x96, 0x27, 0xF4)
)

class PortalInfo : DialogFragment() {

    companion object {
        fun newInstance(e: Portal) = PortalInfo()
            .apply {
            arguments = Bundle().apply {
                putString("pic", e.pic)
                putString("name", e.name)
                putString("team", e.team)
                putString("guid", e.guid)
            }
        }
    }

    private lateinit var viewModel: PortalInfo

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.portal_info_fragment, container, false)
    }

    private fun setProgressBarColor(p: ProgressBar, c: Int) {
        p.progressTintList = ColorStateList.valueOf(c)
    }

    override fun onResume() {
        super.onResume()
        val window: Window? = dialog!!.window
        val size = Point()
        val display: Display = window?.windowManager?.defaultDisplay!!
        display.getSize(size)
        window.setLayout((size.x * 0.85).toInt(), (size.y * 0.85).toInt())
        window.setGravity(Gravity.CENTER)

        val s =
            if (display.rotation == Surface.ROTATION_90 || display.rotation == Surface.ROTATION_270) {
                size.y
            } else {
                size.x
            }
        imageView.layoutParams.height = (s * 0.70).toInt()
        imageView.layoutParams.width = (s * 0.70).toInt()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(
            this,
            ViewmodelFactory(requireContext())
        ).get(PortalInfo::class.java)

        viewModel.owner.observe(viewLifecycleOwner, Observer {
            ownerName.text = it
        })

        viewModel.team.observe(viewLifecycleOwner, Observer {
            teamName.text = it
        })

        viewModel.pic.observe(viewLifecycleOwner, Observer {
            if (it.isNotEmpty()) {
                Picasso.get().load(it).into(imageView)
            }
        })

        viewModel.name.observe(viewLifecycleOwner, Observer {
            portalName.text = it
        })

        viewModel.lvl.observe(viewLifecycleOwner, Observer {
            levelView.text = it.toString()
        })

        viewModel.energy.observe(viewLifecycleOwner, Observer {
            energyView.text = it.toString()
        })

        // TODO: change to listview or something like that asap
        viewModel.resonators.observe(viewLifecycleOwner, Observer {
            it.getOrNull(0)?.let { data ->
                portalOwner1.text = data.owner
                portal1.max = lvlToEnergy[data.level - 1]
                portal1.progress = data.energy
                setProgressBarColor(portal1, lvlToColor[data.level - 1])
            }
            it.getOrNull(1)?.let { data ->
                portalOwner2.text = data.owner
                portal2.max = lvlToEnergy[data.level - 1]
                portal2.progress = data.energy
                setProgressBarColor(portal2, lvlToColor[data.level - 1])
            }
            it.getOrNull(2)?.let { data ->
                portalOwner3.text = data.owner
                portal3.max = lvlToEnergy[data.level - 1]
                portal3.progress = data.energy
                setProgressBarColor(portal3, lvlToColor[data.level - 1])
            }
            it.getOrNull(3)?.let { data ->
                portalOwner4.text = data.owner
                portal4.max = lvlToEnergy[data.level - 1]
                portal4.progress = data.energy
                setProgressBarColor(portal4, lvlToColor[data.level - 1])
            }
            it.getOrNull(4)?.let { data ->
                portalOwner5.text = data.owner
                portal5.max = lvlToEnergy[data.level - 1]
                portal5.progress = data.energy
                setProgressBarColor(portal5, lvlToColor[data.level - 1])
            }
            it.getOrNull(5)?.let { data ->
                portalOwner6.text = data.owner
                portal6.max = lvlToEnergy[data.level - 1]
                portal6.progress = data.energy
                setProgressBarColor(portal6, lvlToColor[data.level - 1])
            }
            it.getOrNull(6)?.let { data ->
                portalOwner7.text = data.owner
                portal7.max = lvlToEnergy[data.level - 1]
                portal7.progress = data.energy
                setProgressBarColor(portal7, lvlToColor[data.level - 1])
            }
            it.getOrNull(7)?.let { data ->
                portalOwner8.text = data.owner
                portal8.max = lvlToEnergy[data.level - 1]
                portal8.progress = data.energy
                setProgressBarColor(portal8, lvlToColor[data.level - 1])
            }
        })

        viewModel.setEntity(
            requireArguments().getString("guid", ""),
            requireArguments().getString("name", ""),
            requireArguments().getString("team", ""),
            requireArguments().getString("pic", "")
        )
    }
}
