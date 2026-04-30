package com.example.expensemanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.example.expensemanager.R
import com.example.expensemanager.adapter.BillAdapter
import com.example.expensemanager.databinding.FragmentDashboardBinding
import com.example.expensemanager.util.PrefsManager
import com.example.expensemanager.viewmodel.AssetViewModel
import kotlinx.coroutines.launch


private val adapter: Any

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AssetViewModel by viewModels()
    private lateinit var billAdapter: BillAdapter
    private lateinit var prefs: PrefsManager
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())
        // Set houseId in ViewModel — triggers data loading
        viewModel.setHouseId(prefs.houseId)
        setupToolbar()
        setupRecyclerView()
        setupFilters()
        observeData()
        binding.fabAdd.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_addAsset)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.title = prefs.houseName
    }

    private fun setupRecyclerView() {
        billAdapter = BillAdapter(
            currentUserId = com.google.firebase.auth.ktx.auth.currentUser?.uid ?: "",
            onPayClick = { bill -> viewModel.markPaid(bill.id) }
        )
        binding.rvBills.adapter = billAdapter
    }

    private fun setupFilters() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chipUtility -> "UTILITY"
                R.id.chipStreaming -> "SUBSCRIPTION"
                R.id.chipMaintenance -> "MAINTENANCE"
                else -> "ALL"
            }
            viewModel.setFilter(filter)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.filteredBills.collect { bills ->
                        billAdapter.submitList(bills)
                        binding.emptyState.visibility =
                            if (bills.isEmpty()) View.VISIBLE else View.GONE
                        binding.rvBills.visibility =
                            if (bills.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    viewModel.totalMonthly.collect { total ->
                        binding.tvTotalMonthly.text = " %.0f".format(total)
                    }
                }
                launch {
                    viewModel.myTotalDue.collect { due ->
                        binding.tvDueAmount.text = " %.0f".format(due)
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is AssetViewModel.UiState.Success -> {
                                Snackbar.make(
                                    binding.root, state.message,
                                    Snackbar.LENGTH_LONG
                                ).show()
                                viewModel.resetState()
                            }

                            is AssetViewModel.UiState.Error -> {
                                Snackbar.make(
                                    binding.root, state.message,
                                    Snackbar.LENGTH_LONG
                                )
                                    .setBackgroundTint(
                                        ContextCompat.getColor(
                                            requireContext(), R.color.red_error
                                        )
                                    )
                                    .show()
                                viewModel.resetState()
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }
}
