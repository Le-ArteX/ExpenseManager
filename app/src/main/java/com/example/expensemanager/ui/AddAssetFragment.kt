package com.example.expensemanager.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.expensemanager.R
import com.example.expensemanager.databinding.FragmentAddAssetBinding
import com.example.expensemanager.util.PrefsManager
import com.example.expensemanager.viewmodel.AssetViewModel
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class AddAssetFragment : Fragment() {
    private var _binding: FragmentAddAssetBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AssetViewModel by viewModels()
    private lateinit var prefs: PrefsManager

    private var selectedCategory = "UTILITY"
    private val selectedMemberIds = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddAssetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())
        viewModel.setHouseId(prefs.houseId)

        setupToolbar()
        setupCategorySelection()
        setupMembersList()
        setupAmountCalculations()
        observeUiState()

        binding.btnSubmit.setOnClickListener { validateAndSubmit() }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setupCategorySelection() {
        val cards = mapOf(
            "SUBSCRIPTION" to binding.catSubscription,
            "UTILITY" to binding.catUtility,
            "MAINTENANCE" to binding.catMaintenance,
            "OTHER" to binding.catOther
        )

        cards.forEach { (cat, card) ->
            card.setOnClickListener {
                selectedCategory = cat
                updateCategoryUI(cards, cat)
            }
        }
        updateCategoryUI(cards, selectedCategory) // Initial state
    }

    private fun updateCategoryUI(cards: Map<String, MaterialCardView>, selected: String) {
        val activeColor = ContextCompat.getColor(requireContext(), R.color.teal_primary)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.gray_border)

        cards.forEach { (cat, card) ->
            if (cat == selected) {
                card.strokeColor = activeColor
                card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.teal_light))
            } else {
                card.strokeColor = inactiveColor
                card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
            }
        }
    }

    private fun setupMembersList() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.members.collect { memberList ->
                    binding.membersList.removeAllViews()
                    selectedMemberIds.clear()

                    memberList.forEach { member ->
                        val checkBox = CheckBox(requireContext()).apply {
                            text = member.displayName
                            isChecked = true // Default to all selected
                            buttonTintList = ColorStateList.valueOf(
                                ContextCompat.getColor(context, R.color.teal_primary)
                            )
                            setOnCheckedChangeListener { _, isChecked ->
                                if (isChecked) selectedMemberIds.add(member.uid)
                                else selectedMemberIds.remove(member.uid)
                                calculatePerPerson()
                            }
                        }
                        selectedMemberIds.add(member.uid)
                        binding.membersList.addView(checkBox)
                    }
                    calculatePerPerson()
                }
            }
        }
    }

    private fun setupAmountCalculations() {
        binding.etAmount.doAfterTextChanged { calculatePerPerson() }
    }

    private fun calculatePerPerson() {
        val total = binding.etAmount.text.toString().toDoubleOrNull() ?: 0.0
        val count = selectedMemberIds.size
        if (count > 0 && total > 0) {
            val perPerson = total / count
            binding.tvPerPersonHint.text = "Split among $count people: BDT %.2f each".format(perPerson)
        } else {
            binding.tvPerPersonHint.text = "Select members below to see per-person cost"
        }
    }

    private fun validateAndSubmit() {
        val name = binding.etName.text.toString().trim()
        val amount = binding.etAmount.text.toString().toDoubleOrNull() ?: 0.0
        val dueDay = binding.etDueDay.text.toString().toIntOrNull() ?: 0

        if (name.isEmpty()) {
            binding.nameLayout.error = "Enter asset name"
            return
        }
        binding.nameLayout.error = null

        if (amount <= 0) {
            binding.amountLayout.error = "Invalid amount"
            return
        }
        binding.amountLayout.error = null

        if (dueDay !in 1..28) {
            binding.dueDayLayout.error = "Use 1-28"
            return
        }
        binding.dueDayLayout.error = null

        if (selectedMemberIds.isEmpty()) {
            Toast.makeText(requireContext(), "Select at least one member", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.addAsset(
            name, selectedCategory, amount, dueDay,
            selectedMemberIds.toList(), binding.etNotes.text.toString()
        )
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
