package com.example.expensemanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        setupRecyclerView()
        observeData()

        binding.fabAddVaultItem.setOnClickListener {
            findNavController().navigate(R.id.action_vault_to_addVaultItem)
        }
    }

    private fun setupRecyclerView() {
        vaultAdapter = VaultAdapter(
            onItemClick = { item ->
                // Handle item click - e.g., show details or receipt
            }
        )
        binding.rvVaultItems.adapter = vaultAdapter
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.warrantyItems.collect { items ->
                    vaultAdapter.submitList(items)
                    binding.emptyVaultState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvVaultItems.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
