package com.puuuuh.ingressmap.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.puuuuh.ingressmap.R
import com.puuuuh.ingressmap.repository.Portal
import com.puuuuh.ingressmap.viewmodel.PortalInfo
import com.puuuuh.ingressmap.viewmodel.ViewmodelFactory
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.portal_info_fragment.*


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

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this,
            ViewmodelFactory(context!!)
        ).get(PortalInfo::class.java)

        viewModel.owner.observe(viewLifecycleOwner, Observer {
            ownerName.text = it
        })

        viewModel.team.observe(viewLifecycleOwner, Observer {
            teamName.text = it
        })

        viewModel.pic.observe(viewLifecycleOwner, Observer {
            Picasso.get().load(it).into(imageView)
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

        viewModel.setEntity(arguments!!.getString("guid", ""),
            arguments!!.getString("name", ""),
            arguments!!.getString("team", ""),
            arguments!!.getString("pic", ""))
    }
}
