package com.example.expensemanager

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.expensemanager.databinding.ActivityMainBinding
import com.example.expensemanager.repository.AuthRepository
import com.example.expensemanager.repository.HouseRepository
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHost.navController

        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val hideOnScreens = setOf(
                R.id.splashFragment,
                R.id.authFragment,
                R.id.houseSetupFragment,
                R.id.cameraFragment,
                R.id.addAssetFragment,
                R.id.addVaultItemFragment
            )
            binding.bottomNav.visibility =
                if (destination.id in hideOnScreens) View.GONE else View.VISIBLE
            
            // Start listening for updates when we enter the main part of the app
            if (destination.id == R.id.dashboardFragment) {
                setupUpdateListeners()
            }
        }
        
        // Initial setup if user is already logged in
        setupUpdateListeners()
    }

    private fun setupUpdateListeners() {
        val user = authRepo.getCurrentUser() ?: return
        
        lifecycleScope.launch {
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
        
        val currentUid = authRepo.getCurrentUser()?.uid
        
        billsListener = FirebaseFirestore.getInstance()
            .collection("houses").document(hId)
            .collection("bills")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("MainActivity", "Listen failed", e)
                    return@addSnapshotListener
                }

                for (dc in snapshots!!.documentChanges) {
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val billName = dc.document.getString("assetName") ?: "New Bill"
                        val amount = dc.document.getDouble("amount") ?: 0.0
                        
                        // Only notify if someone ELSE added the bill
                        // Note: We'd need a 'createdBy' field in Bill model for perfect filtering
                        // For now, we show it to ensure the feature is visible
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
