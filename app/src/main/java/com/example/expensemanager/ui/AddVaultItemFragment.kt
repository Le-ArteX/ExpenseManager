package com.example.expensemanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.expensemanager.databinding.FragmentAddVaultItemBinding
import com.example.expensemanager.model.WarrantyItem
import com.example.expensemanager.util.PrefsManager
import com.example.expensemanager.viewmodel.AssetViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddVaultItemFragment : Fragment() {
    private var _binding: FragmentAddVaultItemBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AssetViewModel by viewModels()
    private lateinit var prefs: PrefsManager

    private var purchaseDate: Date? = null
    private var expiryDate: Date? = null
    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddVaultItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())
        viewModel.setHouseId(prefs.houseId)

        setupToolbar()
        setupDatePickers()
        observeUiState()

        binding.btnSubmit.setOnClickListener { validateAndSubmit() }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setupDatePickers() {
        binding.btnPurchaseDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Purchase Date")
                .setSelection(purchaseDate?.time ?: MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            picker.addOnPositiveButtonClickListener { selection ->
                purchaseDate = Date(selection)
                binding.btnPurchaseDate.text = "Purchased: ${dateFormatter.format(purchaseDate!!)}"
            }
            picker.show(childFragmentManager, "purchase_picker")
        }

        binding.btnExpiryDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Expiry Date")
                .setSelection(expiryDate?.time ?: MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            picker.addOnPositiveButtonClickListener { selection ->
                expiryDate = Date(selection)
                binding.btnExpiryDate.text = "Expires: ${dateFormatter.format(expiryDate!!)}"
            }
            picker.show(childFragmentManager, "expiry_picker")
        }
    }

    private fun validateAndSubmit() {
        val name = binding.etItemName.text.toString().trim()
        val brand = binding.etBrand.text.toString().trim()
        val price = binding.etPrice.text.toString().toDoubleOrNull() ?: 0.0

        if (name.isEmpty()) {
            binding.nameLayout.error = "Enter item name"
            return
        }
        binding.nameLayout.error = null

        if (purchaseDate == null) {
            Toast.makeText(requireContext(), "Select purchase date", Toast.LENGTH_SHORT).show()
            return
        }

        if (expiryDate == null) {
            Toast.makeText(requireContext(), "Select expiry date", Toast.LENGTH_SHORT).show()
            return
        }

        val item = WarrantyItem(
            itemName = name,
            brand = brand,
            purchasePrice = price,
            purchaseDate = Timestamp(purchaseDate!!),
            warrantyExpiryDate = Timestamp(expiryDate!!),
            houseId = prefs.houseId
        )

        viewModel.addWarrantyItem(item, null) // Photo support can be added later via CameraFragment
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is AssetViewModel.UiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.btnSubmit.isEnabled = false
                    }
                    is AssetViewModel.UiState.Success -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                    is AssetViewModel.UiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnSubmit.isEnabled = true
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnSubmit.isEnabled = true
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
