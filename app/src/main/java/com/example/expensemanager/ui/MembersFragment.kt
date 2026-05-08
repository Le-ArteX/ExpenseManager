package com.example.expensemanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.expensemanager.R
import com.example.expensemanager.adapter.MemberAdapter
import com.example.expensemanager.databinding.FragmentMembersBinding
import com.example.expensemanager.util.PrefsManager
import com.example.expensemanager.viewmodel.AssetViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MembersFragment : Fragment() {
    private var _binding: FragmentMembersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AssetViewModel by viewModels()
    private lateinit var memberAdapter: MemberAdapter
    private lateinit var prefs: PrefsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())
        viewModel.setHouseId(prefs.houseId)

        setupToolbar()
        setupRecyclerView()
        observeData()

        binding.btnCopyCode.setOnClickListener {
            val code = binding.tvInviteCode.text.toString()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Invite Code", code)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Invite code copied!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupToolbar() {
        binding.tvToolbarSubtitle.text = prefs.houseName
        binding.profileContainer.setOnClickListener { view ->
            showProfileMenu(view)
        }
    }

    private fun showProfileMenu(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.dashboard_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    logout()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        prefs.clear()
        findNavController().navigate(R.id.authFragment)
    }

    private fun setupRecyclerView() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.house.collect { house ->
                    memberAdapter = MemberAdapter(adminId = house?.adminId)
                    binding.rvMembers.adapter = memberAdapter
                    binding.tvInviteCode.text = house?.inviteCode ?: "......"
                    binding.tvToolbarSubtitle.text = house?.name ?: prefs.houseName
                }
            }
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.members.collect { memberList ->
                    if (::memberAdapter.isInitialized) {
                        memberAdapter.submitList(memberList)
                    }
                    binding.tvMemberCount.text = "${memberList.size} members"
                    
                    val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
                    val currentMember = memberList.find { it.uid == currentUserUid }
                    binding.tvProfileInitials.text = currentMember?.displayName?.take(1)?.uppercase() ?: "U"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
