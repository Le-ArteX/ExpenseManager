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
import com.example.expensemanager.databinding.FragmentRegisterTabBinding
import com.example.expensemanager.repository.AuthRepository
import com.example.expensemanager.util.ValidationUtils
import kotlinx.coroutines.launch

class RegisterTabFragment : Fragment() {
    private var _binding: FragmentRegisterTabBinding? = null
    private val binding get() = _binding!!
    private val authRepo = AuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()
            val confirmPass = binding.etConfirmPassword.text.toString().trim()

            if (!ValidationUtils.isValidName(name)) {
                binding.nameLayout.error = "Name too short"
                return@setOnClickListener
            }
            binding.nameLayout.error = null

            if (!ValidationUtils.isValidEmail(email)) {
                binding.emailLayout.error = "Invalid email"
                return@setOnClickListener
            }
            binding.emailLayout.error = null

            if (!ValidationUtils.isValidPassword(pass)) {
                binding.passwordLayout.error = "Min 6 characters"
                return@setOnClickListener
            }
            binding.passwordLayout.error = null

            if (pass != confirmPass) {
                binding.confirmPasswordLayout.error = "Passwords don't match"
                return@setOnClickListener
            }
            binding.confirmPasswordLayout.error = null

            setLoading(true)
            viewLifecycleOwner.lifecycleScope.launch {
                val result = authRepo.registerWithEmail(email, pass, name)
                setLoading(false)
                result.onSuccess {
                    findNavController().navigate(R.id.action_auth_to_houseSetup)
                }.onFailure {
                    Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
