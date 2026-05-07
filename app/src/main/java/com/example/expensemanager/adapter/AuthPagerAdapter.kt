package com.example.expensemanager.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.expensemanager.ui.LoginTabFragment
import com.example.expensemanager.ui.RegisterTabFragment

class AuthPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> LoginTabFragment()
            else -> RegisterTabFragment()
        }
    }
}
