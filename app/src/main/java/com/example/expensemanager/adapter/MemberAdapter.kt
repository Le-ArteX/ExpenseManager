package com.example.expensemanager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanager.R
import com.example.expensemanager.databinding.ItemMemberBinding
import com.example.expensemanager.model.Member

class MemberAdapter(
    private val adminId: String? = null
) : ListAdapter<Member, MemberAdapter.MemberViewHolder>(DIFF_CALLBACK) {

    inner class MemberViewHolder(private val binding: ItemMemberBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(member: Member) {
            binding.tvMemberName.text = member.displayName
            binding.tvMemberEmail.text = member.email
            binding.tvAvatarInitial.text = member.displayName.take(1).uppercase()
            
            // Show Admin badge if member is the house admin
            binding.tvAdminBadge.visibility = if (member.uid == adminId) View.VISIBLE else View.GONE
            
            // Balance logic could be added here later
            binding.tvBalance.text = "Settled"
            binding.tvBalance.setTextColor(ContextCompat.getColor(binding.root.context, R.color.green_success))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemMemberBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Member>() {
            override fun areItemsTheSame(old: Member, new: Member) = old.uid == new.uid
            override fun areContentsTheSame(old: Member, new: Member) = old == new
        }
    }
}
