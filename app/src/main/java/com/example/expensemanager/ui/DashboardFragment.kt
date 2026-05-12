package com.example.expensemanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.expensemanager.R
import com.example.expensemanager.adapter.BillAdapter
import com.example.expensemanager.databinding.FragmentDashboardBinding
import com.example.expensemanager.util.PrefsManager
import com.example.expensemanager.viewmodel.AssetViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        
        val currentHouseId = prefs.houseId
        if (currentHouseId.isEmpty()) {
            findNavController().navigate(R.id.action_dashboard_to_houseSetup)
            return
        }

        viewModel.setHouseId(currentHouseId)
        
        setupToolbar()
        setupRecyclerView()
        setupFilters()
        observeData()

        // Navigates to the Assets list page as requested
        binding.fabAdd.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_assets)
        }
    }

    private fun setupToolbar() {
        binding.tvToolbarTitle.text = prefs.houseName
        
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
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            findNavController().navigate(R.id.authFragment)
            return
        }

        billAdapter = BillAdapter(
            currentUserId = user.uid,
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
                    viewModel.house.collect { house ->
                        house?.let {
                            binding.tvToolbarTitle.text = it.name
                            prefs.houseName = it.name
                        }
                    }
                }
                launch {
                    viewModel.members.collect { members ->
                        val count = members.size
                        val date = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
                        binding.tvToolbarSubtitle.text = "$count members • $date"
                        
                        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
                        val currentMember = members.find { it.uid == currentUserUid }
                        binding.tvProfileInitials.text = currentMember?.displayName?.take(1)?.uppercase() ?: "U"
                    }
                }
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
                        binding.tvTotalMonthly.text = String.format("৳ %.0f", total)
                    }
                }
                launch {
                    viewModel.myTotalDue.collect { due ->
                        binding.tvDueAmount.text = String.format("৳ %.0f", due)
                    }
                }
                launch {
                    viewModel.myTotalPaid.collect { paid ->
                        binding.tvPaidAmount.text = String.format("৳ %.0f", paid)
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is AssetViewModel.UiState.Success -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.resetState()
                            }
                            is AssetViewModel.UiState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG)
                                    .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.red_error))
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
        super.onDestroyView()
        _binding = null
    }
}
