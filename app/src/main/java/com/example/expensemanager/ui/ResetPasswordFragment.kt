package com.example.expensemanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.expensemanager.R
import com.example.expensemanager.databinding.FragmentResetPasswordBinding
import com.example.expensemanager.repository.AuthRepository
import com.example.expensemanager.util.ValidationUtils
import kotlinx.coroutines.launch

class ResetPasswordFragment : Fragment() {
    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!
    private val args: ResetPasswordFragmentArgs by navArgs()
    private val authRepo = AuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResetPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnUpdatePassword.setOnClickListener {
            val newPass = binding.etNewPassword.text.toString().trim()
            val confirmPass = binding.etConfirmPassword.text.toString().trim()

            val passValidation = ValidationUtils.validatePassword(newPass)
            if (!passValidation.isSuccess) {
                binding.newPasswordLayout.error = passValidation.errorMessage
                return@setOnClickListener
            }
            binding.newPasswordLayout.error = null

            if (newPass != confirmPass) {
                binding.confirmPasswordLayout.error = "Passwords do not match"
                return@setOnClickListener
            }
            binding.confirmPasswordLayout.error = null

            updatePassword(newPass)
        }
    }

    private fun updatePassword(newPass: String) {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = authRepo.resetPassword(args.email,
                        newPass)
            setLoading(false)
            result.onSuccess {
                Toast.makeText(requireContext(), "Password updated successfully! Please login.", Toast.LENGTH_LONG).show()
                findNavController().navigate(R.id.action_resetPassword_to_auth)
            }.onFailure {
                Toast.makeText(requireContext(), it.message ?: "Update failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnUpdatePassword.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
