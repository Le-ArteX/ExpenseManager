package com.example.expensemanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.expensemanager.R
import com.example.expensemanager.adapter.AssetAdapter
import com.example.expensemanager.databinding.FragmentAssetsBinding
import com.example.expensemanager.util.PrefsManager
import com.example.expensemanager.viewmodel.AssetViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class AssetsFragment : Fragment() {
    private var _binding: FragmentAssetsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AssetViewModel by viewModels()
    private lateinit var assetAdapter: AssetAdapter
    private lateinit var prefs: PrefsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAssetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())
        viewModel.setHouseId(prefs.houseId)

        setupToolbar()
        setupRecyclerView()
        observeData()

        binding.fabAddAsset.setOnClickListener {
            findNavController().navigate(R.id.action_assets_to_addAsset)
        }
    }

    private fun setupToolbar() {
        binding.profileContainer.setOnClickListener { view ->
            showProfileMenu(view)
        }
    }

    private fun showProfileMenu(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.dashboard_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    logout()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        prefs.clear()
        findNavController().navigate(R.id.authFragment)
    }

    private fun setupRecyclerView() {
        assetAdapter = AssetAdapter(
            onItemClick = { asset ->
                // Handle asset click
            }
        )
        binding.rvAssets.adapter = assetAdapter
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.assets.collect { assets ->
                        assetAdapter.submitList(assets)
                        binding.emptyAssetsState.visibility = if (assets.isEmpty()) View.VISIBLE else View.GONE
                        binding.rvAssets.visibility = if (assets.isEmpty()) View.GONE else View.VISIBLE
                        binding.tvToolbarSubtitle.text = "${assets.size} active shared assets"
                    }
                }
                launch {
                    viewModel.members.collect { members ->
                        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
                        val currentMember = members.find { it.uid == currentUserUid }
                        binding.tvProfileInitials.text = currentMember?.displayName?.take(1)?.uppercase() ?: "U"
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
