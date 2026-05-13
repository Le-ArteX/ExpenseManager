package com.example.expensemanager.ui

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.expensemanager.R
import com.example.expensemanager.databinding.FragmentLoginTabBinding
import com.example.expensemanager.repository.AuthRepository
import com.example.expensemanager.repository.HouseRepository
import com.example.expensemanager.util.PrefsManager
import com.example.expensemanager.util.ValidationUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

class LoginTabFragment : Fragment() {
    private var _binding: FragmentLoginTabBinding? = null
    private val binding get() = _binding!!
    private val authRepo = AuthRepository()
    private val houseRepo = HouseRepository()
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("LoginTab", "Google result code: ${result.resultCode}")
        
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                performGoogleLogin(idToken)
            } else {
                setLoading(false)
                Log.e("LoginTab", "Google Sign-In: ID Token is null")
                Toast.makeText(requireContext(), "Google Sign-In: No ID Token", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            setLoading(false)

            Log.e("LoginTab", "Google Sign-In failed: Status Code ${e.statusCode}, Message: ${e.message}")
            val errorMsg = when (e.statusCode) {
                10 -> "Configuration Error (Code 10). Ensure SHA-1 is added in Firebase Console."
                7 -> "Network Error. Check your connection."
                12500 -> "Sign-in internal error. Try clearing Google Play Services cache."
                else -> "Sign-in failed (Code: ${e.statusCode})"
            }
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
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
                if (result.isSuccess) {
                    handleLoginSuccess()
                } else {
                    setLoading(false)
                    Toast.makeText(requireContext(), result.exceptionOrNull()?.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnGoogleSignIn.setOnClickListener {
            Log.d("LoginTab", "Continue with Google clicked")
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun setupGoogleSignIn() {
        try {

            val webClientId = getString(R.string.default_web_client_id)
            Log.d("LoginTab", "Initializing Google with Client ID: $webClientId")
            
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
        } catch (e: Exception) {
            Log.e("LoginTab", "Google setup failed: ${e.message}")
        }
    }

    private fun performGoogleLogin(idToken: String) {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = authRepo.loginWithGoogle(idToken)
            if (result.isSuccess) {
                handleLoginSuccess()
            } else {
                setLoading(false)
                Log.e("LoginTab", "Firebase login failed: ${result.exceptionOrNull()?.message}")
                Toast.makeText(requireContext(), result.exceptionOrNull()?.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleLoginSuccess() {
        val user = authRepo.getCurrentUser() ?: return
        val prefs = PrefsManager(requireContext())
        
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = authRepo.getMemberProfile(user.uid)
            
            if (profile != null && profile.houseId.isNotEmpty()) {
                val house = houseRepo.getHouse(profile.houseId)
                setLoading(false)
                
                prefs.houseId = profile.houseId
                prefs.userId = user.uid
                house?.let { prefs.houseName = it.name }
                
                findNavController().navigate(R.id.action_auth_to_dashboard)
            } else {
                setLoading(false)
                prefs.userId = user.uid
                findNavController().navigate(R.id.action_auth_to_houseSetup)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        if (_binding == null) return
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
        binding.btnGoogleSignIn.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
