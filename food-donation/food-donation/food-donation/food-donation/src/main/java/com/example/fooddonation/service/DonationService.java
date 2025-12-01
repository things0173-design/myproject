package com.example.fooddonation.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.fooddonation.entity.DonationDTO;
import com.example.fooddonation.entity.DonorDTO;
import com.example.fooddonation.entity.NgoDTO;
import com.example.fooddonation.repository.DonationRepository;
import com.example.fooddonation.repository.DonorRepository;
import com.example.fooddonation.repository.NgoRepository;

import java.util.List;
import java.util.Optional;

@Service
public class DonationService {

    @Autowired
    private DonationRepository donationRepo;

    @Autowired
    private DonorRepository donorRepo;

    @Autowired
    private NgoRepository ngoRepo;

    public DonationDTO addDonation(int donorId, DonationDTO payload) {
        Optional<DonorDTO> dOpt = donorRepo.findById(donorId);
        if (!dOpt.isPresent()) throw new RuntimeException("Donor not found");
        payload.setDonor(dOpt.get());

        // If NGO id included in payload.ngo, attach it
        if (payload.getNgo() != null && payload.getNgo().getId() != 0) {
            NgoDTO ngo = ngoRepo.findById(payload.getNgo().getId())
                    .orElseThrow(() -> new RuntimeException("NGO not found"));
            payload.setNgo(ngo);
        } else {
            payload.setNgo(null);
        }

        if (payload.getDonationType() != null && payload.getDonationType().equalsIgnoreCase("MONEY")) {
            if (payload.getAmount() != null && (payload.getQuantity() == null || payload.getQuantity().isEmpty())) {
                payload.setQuantity(payload.getAmount());
            }
        }

        return donationRepo.save(payload);
    }

    public DonationDTO updateDonation(int id, int donorId, DonationDTO payload) {
        DonationDTO existing = donationRepo.findById(id).orElseThrow(() -> new RuntimeException("Donation not found"));
        if (existing.getDonor() == null || existing.getDonor().getId() != donorId) {
            throw new RuntimeException("Not allowed");
        }

        // Update allowed fields (replace or patch behaviour can be extended)
        existing.setDonationType(payload.getDonationType());
        existing.setFoodName(payload.getFoodName());
        existing.setMealType(payload.getMealType());
        existing.setCategory(payload.getCategory());
        existing.setQuantity(payload.getQuantity());
        existing.setCity(payload.getCity());
        existing.setExpiryDateTime(payload.getExpiryDateTime());
        existing.setAmount(payload.getAmount());
        existing.setClothesType(payload.getClothesType());
        existing.setItemName(payload.getItemName());

        // Update ngo if provided
        if (payload.getNgo() != null && payload.getNgo().getId() != 0) {
            NgoDTO ngo = ngoRepo.findById(payload.getNgo().getId())
                    .orElseThrow(() -> new RuntimeException("NGO not found"));
            existing.setNgo(ngo);
        }

        return donationRepo.save(existing);
    }

    public List<DonationDTO> getAllDonations() {
        return donationRepo.findAll();
    }

    public List<DonationDTO> getByDonor(int donorId) {
        return donationRepo.findByDonorId(donorId);
    }

    public List<DonationDTO> getByNgo(int ngoId) {
        return donationRepo.findByNgoId(ngoId);
    }
}
