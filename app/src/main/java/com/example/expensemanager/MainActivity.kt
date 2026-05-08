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
        // The IDs in bottom_nav_menu.xml match the fragment IDs in nav_graph.xml
        binding.bottomNav.setupWithNavController(navController)

        // Hide bottom nav on specific screens (auth, setup, and detailed forms)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val hideOnScreens = setOf(
                R.id.splashFragment,
                R.id.authFragment,
                R.id.forgotPasswordFragment,
                R.id.houseSetupFragment,
                R.id.cameraFragment,
                R.id.addAssetFragment,
                R.id.addVaultItemFragment
            )
            binding.bottomNav.visibility =
                if (destination.id in hideOnScreens) View.GONE else View.VISIBLE
        }
    }
}
