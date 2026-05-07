package com.example.expensemanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.expensemanager.databinding.FragmentForgotPasswordBinding
import com.example.expensemanager.repository.AuthRepository
import com.example.expensemanager.util.ValidationUtils
import kotlinx.coroutines.launch

class ForgotPasswordFragment : Fragment() {
    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!
    private val authRepo = AuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        binding.btnResetPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()

            if (!ValidationUtils.isValidEmail(email)) {
                binding.emailLayout.error = "Invalid email"
                return@setOnClickListener
            }
            binding.emailLayout.error = null

            setLoading(true)
            viewLifecycleOwner.lifecycleScope.launch {
                val result = authRepo.sendPasswordReset(email)
                setLoading(false)
                result.onSuccess {
                    Toast.makeText(requireContext(), "Reset link sent to your email", Toast.LENGTH_LONG).show()
                    findNavController().popBackStack()
                }.onFailure {
                    Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnResetPassword.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
