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
            val isValid = authRepo.verifyOtp(args.email, otp)
            if (isValid) {
                if (args.type == "FORGOT_PASSWORD") {
                    // Trigger official Firebase reset link after OTP is verified
                    val result = authRepo.resetPassword(args.email)
                    setLoading(false)
                    if (result.isSuccess) {
                        Toast.makeText(requireContext(), "Verified! Check your email for the final reset link.", Toast.LENGTH_LONG).show()
                        findNavController().popBackStack(R.id.authFragment, false)
                    } else {
                        Toast.makeText(requireContext(), "Error sending reset link: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    performRegistration()
                }
            } else {
                setLoading(false)
                Toast.makeText(requireContext(), "Invalid or expired OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resendOtp() {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = authRepo.sendOtp(args.email)
            setLoading(false)
            result.onSuccess {
                Toast.makeText(requireContext(), "A new OTP has been sent!", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(requireContext(), it.message ?: "Failed to resend", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun performRegistration() {
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
        binding.tvResendOtp.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
