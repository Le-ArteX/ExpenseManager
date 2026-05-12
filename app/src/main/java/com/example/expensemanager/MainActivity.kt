package com.example.expensemanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.expensemanager.databinding.ActivityMainBinding
import com.example.expensemanager.repository.AuthRepository
import com.example.expensemanager.util.NotificationHelper
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    
    private val authRepo = AuthRepository()
    private var billsListener: ListenerRegistration? = null
    private var houseId: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission denied. You won't receive bill reminders.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        askNotificationPermission()

        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHost.navController

        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val hideOnScreens = setOf(
                R.id.splashFragment,
                R.id.authFragment,
                R.id.forgotPasswordFragment,
                R.id.otpFragment,
                R.id.resetPasswordFragment,
                R.id.houseSetupFragment,
                R.id.cameraFragment,
                R.id.addVaultItemFragment
            )
            binding.bottomNav.visibility =
                if (destination.id in hideOnScreens) View.GONE else View.VISIBLE
            
            if (destination.id == R.id.dashboardFragment) {
                setupUpdateListeners()
            }
        }
        
        setupUpdateListeners()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupUpdateListeners() {
        val user = authRepo.getCurrentUser() ?: return
        
        lifecycleScope.launch {
            // Ensure FCM Token is up to date for notifications
            authRepo.syncFcmToken()

            FirebaseFirestore.getInstance().collection("members")
                .document(user.uid)
                .get()
                .addOnSuccessListener { doc ->
                    val newHouseId = doc.getString("houseId")
                    if (newHouseId != null && newHouseId != houseId) {
                        houseId = newHouseId
                        startListeningForBills(newHouseId)
                    }
                }
        }
    }

    private fun startListeningForBills(hId: String) {
        billsListener?.remove()
        
        billsListener = FirebaseFirestore.getInstance()
            .collection("houses").document(hId)
            .collection("bills")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("MainActivity", "Listen failed", e)
                    return@addSnapshotListener
                }

                if (snapshots == null) return@addSnapshotListener

                for (dc in snapshots.documentChanges) {
                    if (dc.type == DocumentChange.Type.ADDED) {
                        if (!snapshots.metadata.hasPendingWrites() && snapshots.metadata.isFromCache) continue

                        val billName = dc.document.getString("assetName") ?: "New Bill"
                        val amount = dc.document.getDouble("amount") ?: 0.0
                        
                        NotificationHelper.showNotification(
                            this,
                            "New Bill Added",
                            "$billName of amount ৳$amount has been added to your house."
                        )
                    }
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        billsListener?.remove()
    }
}
