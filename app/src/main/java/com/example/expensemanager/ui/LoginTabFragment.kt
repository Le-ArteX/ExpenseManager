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
import com.example.expensemanager.databinding.FragmentLoginTabBinding
import com.example.expensemanager.repository.AuthRepository
import com.example.expensemanager.util.PrefsManager
import com.example.expensemanager.util.ValidationUtils
import kotlinx.coroutines.launch

class LoginTabFragment : Fragment() {
    private var _binding: FragmentLoginTabBinding? = null
    private val binding get() = _binding!!
    private val authRepo = AuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvForgotPassword.setOnClickListener {
            findNavController().navigate(R.id.action_auth_to_forgotPassword)
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            if (!ValidationUtils.isValidEmail(email)) {
                binding.emailLayout.error = "Invalid email"
                return@setOnClickListener
            }
            binding.emailLayout.error = null

            if (pass.isEmpty()) {
                binding.passwordLayout.error = "Enter password"
                return@setOnClickListener
            }
            binding.passwordLayout.error = null

            setLoading(true)
            viewLifecycleOwner.lifecycleScope.launch {
                val result = authRepo.loginWithEmail(email, pass)
                setLoading(false)
                result.onSuccess {
                    val prefs = PrefsManager(requireContext())
                    if (prefs.isHouseSetup()) {
                        findNavController().navigate(R.id.action_auth_to_dashboard)
                    } else {
                        findNavController().navigate(R.id.action_auth_to_houseSetup)
                    }
                }.onFailure {
                    Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
