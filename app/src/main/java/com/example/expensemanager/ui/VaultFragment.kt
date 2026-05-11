package com.example.expensemanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.expensemanager.R
import com.example.expensemanager.adapter.VaultAdapter
import com.example.expensemanager.databinding.FragmentVaultBinding
import com.example.expensemanager.util.PrefsManager
import com.example.expensemanager.viewmodel.AssetViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class VaultFragment : Fragment() {
    private var _binding: FragmentVaultBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AssetViewModel by viewModels()
    private lateinit var vaultAdapter: VaultAdapter
    private lateinit var prefs: PrefsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVaultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())
        viewModel.setHouseId(prefs.houseId)

        setupToolbar()
        setupRecyclerView()
        observeData()

        binding.fabAddVaultItem.setOnClickListener {
            findNavController().navigate(R.id.action_vault_to_addVaultItem)
        }

        binding.cardSnapReceipt.setOnClickListener {
            val action = VaultFragmentDirections.actionVaultToCamera(prefs.houseId ?: "", "")
            findNavController().navigate(action)
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
        vaultAdapter = VaultAdapter(
            onItemClick = { item ->
                // Handle item click (e.g., show details)
            },
            onDeleteClick = { item ->
                showDeleteConfirmation(item)
            }
        )
        binding.rvVaultItems.adapter = vaultAdapter
    }

    private fun showDeleteConfirmation(item: com.example.expensemanager.model.WarrantyItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete ${item.itemName}?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteWarrantyItem(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.warrantyItems.collect { items ->
                        vaultAdapter.submitList(items)
                        binding.emptyVaultState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                        binding.rvVaultItems.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                        
                        binding.tvTotalCount.text = "${items.size} total"
                        
                        val expiringCount = items.count { it.isExpiringSoon() }
                        binding.tvToolbarSubtitle.text = if (expiringCount > 0) {
                            "${items.size} items • $expiringCount expiring soon"
                        } else {
                            "${items.size} items"
                        }
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
