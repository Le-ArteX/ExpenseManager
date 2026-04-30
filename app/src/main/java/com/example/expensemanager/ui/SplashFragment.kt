package com.example.expensemanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.expensemanager.R
import com.example.expensemanager.databinding.FragmentSplashBinding
import com.example.expensemanager.repository.AuthRepository
import com.example.expensemanager.util.PrefsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
class SplashFragment : Fragment() {
    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!
    private val authRepo = AuthRepository()
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            delay(1800)  // Show splash for 1.8 seconds
            if (authRepo.isLoggedIn()) {
                val prefs = PrefsManager(requireContext())
                if (prefs.isHouseSetup()) {
                    findNavController().navigate(R.id.action_splash_to_dashboard)
                } else {
                    findNavController().navigate(R.id.action_splash_to_auth)
                }
            } else {
                findNavController().navigate(R.id.action_splash_to_auth)
            }
        }
    }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}