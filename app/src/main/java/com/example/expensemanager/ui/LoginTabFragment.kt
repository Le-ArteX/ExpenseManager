package com.example.expensemanager.ui

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.expensemanager.R
import com.example.expensemanager.databinding.FragmentLoginTabBinding
import com.example.expensemanager.repository.AuthRepository
import com.example.expensemanager.util.PrefsManager
import com.example.expensemanager.util.ValidationUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class LoginTabFragment : Fragment() {
    private var _binding: FragmentLoginTabBinding? = null
    private val binding get() = _binding!!
    private val authRepo = AuthRepository()
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    performGoogleLogin(idToken)
                } else {
                    Toast.makeText(requireContext(), "Google Sign-In failed: No ID Token", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                Toast.makeText(requireContext(), "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGoogleSignIn()

        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
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
                    handleLoginSuccess()
                }.onFailure {
                    Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnGoogleSignIn.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
    }

    private fun performGoogleLogin(idToken: String) {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = authRepo.loginWithGoogle(idToken)
            setLoading(false)
            result.onSuccess {
                handleLoginSuccess()
            }.onFailure {
                Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleLoginSuccess() {
        val prefs = PrefsManager(requireContext())
        if (prefs.isHouseSetup()) {
            findNavController().navigate(R.id.action_auth_to_dashboard)
        } else {
            findNavController().navigate(R.id.action_auth_to_houseSetup)
        }
    }

    private fun showForgotPasswordDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "Enter your registered email"
            setPadding(60, 40, 60, 40)
        }
        val container = FrameLayout(requireContext())
        container.addView(editText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reset Password")
            .setMessage("We will send a reset link to your email.")
            .setView(container)
            .setPositiveButton("Send") { _, _ ->
                val email = editText.text.toString().trim()
                if (ValidationUtils.isValidEmail(email)) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        authRepo.sendPasswordReset(email)
                        Toast.makeText(requireContext(), "Reset email sent!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Invalid email", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
        binding.btnGoogleSignIn.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
