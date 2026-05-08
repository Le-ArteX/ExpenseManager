package com.example.expensemanager

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.expensemanager.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHost.navController

        // Connect bottom nav to navigation controller
        binding.bottomNav.setupWithNavController(navController)

        // Control bottom nav visibility
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val hideOnScreens = setOf(
                R.id.splashFragment,
                R.id.authFragment, // Hide only on the main Login/Register screen
                R.id.houseSetupFragment,
                R.id.cameraFragment,
                R.id.addAssetFragment,
                R.id.addVaultItemFragment
            )
            
            // Bottom nav will now be VISIBLE on otpFragment, forgotPasswordFragment, and resetPasswordFragment
            binding.bottomNav.visibility =
                if (destination.id in hideOnScreens) View.GONE else View.VISIBLE
        }
    }
}
