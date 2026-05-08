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
import com.example.expensemanager.databinding.FragmentOtpBinding
import com.example.expensemanager.repository.AuthRepository
import kotlinx.coroutines.launch

class OtpFragment : Fragment() {
    private var _binding: FragmentOtpBinding? = null
    private val binding get() = _binding!!
    private val args: OtpFragmentArgs by navArgs()
    private val authRepo = AuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOtpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvOtpDescription.text = "Please enter the 6-digit code sent to ${args.email}"

        binding.btnVerify.setOnClickListener {
            val otp = binding.etOtp.text.toString().trim()
            if (otp.length != 6) {
                binding.otpLayout.error = "Enter 6 digits"
                return@setOnClickListener
            }
            binding.otpLayout.error = null

            verifyOtp(otp)
        }

        binding.tvResendOtp.setOnClickListener {
            resendOtp()
        }
    }

    private fun verifyOtp(otp: String) {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            // Note: In a real app, this would verify with a backend or Firestore
            // For this implementation, we simulate verification
            val isValid = authRepo.verifyOtp(args.email, otp)
            setLoading(false)

            if (isValid) {
                if (args.type == "FORGOT_PASSWORD") {
                    val action = OtpFragmentDirections.actionOtpToResetPassword(args.email)
                    findNavController().navigate(action)
                } else {
                    // REGISTER flow - complete registration
                    performRegistration()
                }
            } else {
                Toast.makeText(requireContext(), "Invalid or expired OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resendOtp() {
        viewLifecycleOwner.lifecycleScope.launch {
            authRepo.sendOtp(args.email)
            Toast.makeText(requireContext(), "OTP resent!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performRegistration() {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = authRepo.registerWithEmail(args.email, args.password, args.name)
            setLoading(false)
            result.onSuccess {
                findNavController().navigate(R.id.action_otp_to_houseSetup)
            }.onFailure {
                Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnVerify.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
