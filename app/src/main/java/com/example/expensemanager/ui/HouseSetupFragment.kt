package com.example.expensemanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.expensemanager.R
import com.example.expensemanager.databinding.FragmentHouseSetupBinding
import com.example.expensemanager.repository.HouseRepository
import com.example.expensemanager.util.PrefsManager
import kotlinx.coroutines.launch

class HouseSetupFragment : Fragment() {
    private var _binding: FragmentHouseSetupBinding? = null
    private val binding get() = _binding!!
    private val houseRepo = HouseRepository()
    private lateinit var prefs: PrefsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHouseSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())

        binding.btnCreateHouse.setOnClickListener {
            val name = binding.etHouseName.text.toString().trim()
            val address = binding.etAddress.text.toString().trim()

            if (name.isEmpty()) {
                binding.houseNameLayout.error = "Name required"
                return@setOnClickListener
            }

            setLoading(true)
            viewLifecycleOwner.lifecycleScope.launch {
                val result = houseRepo.createHouse(name, address)
                setLoading(false)
                result.onSuccess { house ->
                    prefs.houseId = house.id
                    prefs.houseName = house.name
                    findNavController().navigate(R.id.action_houseSetup_to_dashboard)
                }.onFailure {
                    Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnJoinHouse.setOnClickListener {
            val code = binding.etInviteCode.text.toString().trim()
            if (code.length < 6) {
                binding.inviteCodeLayout.error = "Invalid code"
                return@setOnClickListener
            }

            setLoading(true)
            viewLifecycleOwner.lifecycleScope.launch {
                val result = houseRepo.joinHouse(code)
                setLoading(false)
                result.onSuccess { house ->
                    prefs.houseId = house.id
                    prefs.houseName = house.name
                    findNavController().navigate(R.id.action_houseSetup_to_dashboard)
                }.onFailure {
                    Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnCreateHouse.isEnabled = !isLoading
        binding.btnJoinHouse.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
