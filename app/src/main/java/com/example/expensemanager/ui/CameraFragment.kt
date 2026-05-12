package com.example.expensemanager.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.expensemanager.databinding.FragmentCameraBinding
import com.example.expensemanager.repository.VaultRepository
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CameraFragment : Fragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private val args: CameraFragmentArgs by navArgs()
    private val vaultRepo = VaultRepository()

    private var imageCapture: ImageCapture? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Safety check for houseId
        if (args.houseId.isBlank()) {
            Toast.makeText(requireContext(), "Error: No House ID found", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnCapture.setOnClickListener { takePhoto() }
        binding.btnClose.setOnClickListener { findNavController().popBackStack() }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraFragment", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // Visual flash feedback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.root.postDelayed({
                binding.root.foreground = ColorDrawable(Color.WHITE)
                binding.root.postDelayed({ binding.root.foreground = null }, 50)
            }, 100)
        }

        val photoFile = File(
            requireContext().cacheDir,
            "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        binding.btnCapture.isEnabled = false
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraFragment", "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(requireContext(), "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                    binding.btnCapture.isEnabled = true
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d("CameraFragment", "Photo saved to: ${photoFile.absolutePath}")
                    uploadPhoto(photoFile)
                }
            }
        )
    }

    private fun uploadPhoto(file: File) {
        Toast.makeText(requireContext(), "Uploading receipt...", Toast.LENGTH_SHORT).show()
        
        // Use viewLifecycleOwner.lifecycleScope for safer async work
        viewLifecycleOwner.lifecycleScope.launch {
            val result = if (args.warrantyItemId.isNotBlank()) {
                vaultRepo.addReceiptToItem(args.houseId, args.warrantyItemId, file)
            } else {
                vaultRepo.uploadReceiptImage(args.houseId, file)
            }

            if (result.isSuccess) {
                Toast.makeText(requireContext(), "Receipt saved!", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                Log.e("CameraFragment", "Upload failed: $error")
                Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_LONG).show()
                binding.btnCapture.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
